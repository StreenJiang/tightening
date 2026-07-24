# SSE 工位事件实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一前后端 SSE 协议为 `event:` + `data:` 格式，新增 `/api/workplace/events` 端点推送 5 种任务生命周期事件

**Architecture:** SseService 拆为双 emitter（device + workplace），LifecycleEngine 通过 PipelineEventListener 回调解耦 SSE 推送，曲线数据通过 CurveDataSavedEvent 事件驱动推送

**Tech Stack:** Java 21, Spring Boot 3.5.10, SseEmitter

## Global Constraints

- SSE 线格式：`event: <type>` + `data: <JSON>`，不在 data 内重复包装 type
- 事件命名：`domain:action` 小写冒号分隔
- 单客户端：新连接替换旧连接
- LifecycleEngine 不直接依赖 SseService
- 删除 `SseEvent` record 和 `SseEventType` enum

## 事件清单（5 个）

| Event | Payload | 触发时机 |
|-------|---------|---------|
| `task:control` | `{ control: "stopped", reason, ts }` | 引擎 faulted |
| `tightening:data` | `TighteningDataDTO`（全量） | 拧紧判定完成（JUDGING 后） |
| `curve:data` | `CurveDataDTO` | 曲线保存完成、与 tightening 绑定后 |
| `device:status` | `{ "<deviceId>": true\|false, ..., ts }` | 设备连接状态变化 |
| `workplace:status` | `{ status, lockReasons, ts }` | 工位状态 / lockReasons 变更 |

---

### Task 1: 新增 SseEvents 常量类 + PipelineEventListener 接口 + CurveDataSavedEvent

**Files:**
- Create: `src/main/java/com/tightening/constant/SseEvents.java`
- Create: `src/main/java/com/tightening/lifecycle/PipelineEventListener.java`
- Create: `src/main/java/com/tightening/device/event/CurveDataSavedEvent.java`

**Interfaces:**
- Produces: `SseEvents.*` — 5 个事件类型字符串常量
- Produces: `PipelineEventListener.onEvent(String type, Object data): void`
- Produces: `CurveDataSavedEvent(CurveDataDTO data)` — Spring ApplicationEvent

- [ ] **Step 1: 创建 SseEvents 常量类**

```java
package com.tightening.constant;

/** SSE 事件类型常量。替代旧 SseEventType enum。 */
public final class SseEvents {
    private SseEvents() {}

    public static final String TASK_CONTROL = "task:control";
    public static final String TIGHTENING_DATA = "tightening:data";
    public static final String CURVE_DATA = "curve:data";
    public static final String DEVICE_STATUS = "device:status";
    public static final String WORKPLACE_STATUS = "workplace:status";
}
```

- [ ] **Step 2: 创建 PipelineEventListener 接口**

```java
package com.tightening.lifecycle;

@FunctionalInterface
public interface PipelineEventListener {
    void onEvent(String type, Object data);
}
```

- [ ] **Step 3: 创建 CurveDataSavedEvent**

```java
package com.tightening.device.event;

import com.tightening.dto.CurveDataDTO;
import org.springframework.context.ApplicationEvent;

public class CurveDataSavedEvent extends ApplicationEvent {
    private final CurveDataDTO data;

    public CurveDataSavedEvent(Object source, CurveDataDTO data) {
        super(source);
        this.data = data;
    }

    public CurveDataDTO getData() { return data; }
}
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/constant/SseEvents.java \
        src/main/java/com/tightening/lifecycle/PipelineEventListener.java \
        src/main/java/com/tightening/device/event/CurveDataSavedEvent.java
git commit -m "feat: add SseEvents, PipelineEventListener, and CurveDataSavedEvent"
```

---

### Task 2: 重写 SseService —— 双 emitter + 新签名

**Files:**
- Modify: `src/main/java/com/tightening/service/SseService.java`（重写全文）

**Interfaces:**
- Produces: `createDeviceEmitter(): SseEmitter`, `createWorkplaceEmitter(): SseEmitter`, `emitDevice(String, Object): void`, `emitWorkplace(String, Object): void`, `closeDevice(): void`, `closeWorkplace(): void`

- [ ] **Step 1: 重写 SseService.java**

```java
package com.tightening.service;

import com.tightening.util.ThreadUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class SseService {

    private volatile SseEmitter deviceEmitter;
    private volatile SseEmitter workplaceEmitter;

    private final ScheduledExecutorService deviceHeartbeat =
            ThreadUtils.newDaemonScheduledExecutor("sse-device-heartbeat");
    private final ScheduledExecutorService workplaceHeartbeat =
            ThreadUtils.newDaemonScheduledExecutor("sse-workplace-heartbeat");

    private volatile ScheduledFuture<?> deviceHbFuture;
    private volatile ScheduledFuture<?> workplaceHbFuture;

    // ── Device emitter ──

    public SseEmitter createDeviceEmitter() {
        SseEmitter previous = this.deviceEmitter;
        if (previous != null) {
            try { previous.complete(); } catch (Exception e) { /* ignore */ }
        }
        deviceEmitter = new SseEmitter(0L);
        startHeartbeat(deviceEmitter, deviceHeartbeat, hb -> deviceHbFuture = hb, "device");
        return deviceEmitter;
    }

    public void emitDevice(String type, Object data) {
        SseEmitter current = this.deviceEmitter;
        if (current == null) return;
        send(current, type, data, "device");
    }

    public void closeDevice() {
        cancelHeartbeat(deviceHbFuture, hb -> deviceHbFuture = hb);
        completeEmitter(deviceEmitter, em -> deviceEmitter = em);
    }

    // ── Workplace emitter ──

    public SseEmitter createWorkplaceEmitter() {
        SseEmitter previous = this.workplaceEmitter;
        if (previous != null) {
            try { previous.complete(); } catch (Exception e) { /* ignore */ }
        }
        workplaceEmitter = new SseEmitter(0L);
        startHeartbeat(workplaceEmitter, workplaceHeartbeat, hb -> workplaceHbFuture = hb, "workplace");
        return workplaceEmitter;
    }

    public void emitWorkplace(String type, Object data) {
        SseEmitter current = this.workplaceEmitter;
        if (current == null) return;
        send(current, type, data, "workplace");
    }

    public void closeWorkplace() {
        cancelHeartbeat(workplaceHbFuture, hb -> workplaceHbFuture = hb);
        completeEmitter(workplaceEmitter, em -> workplaceEmitter = em);
    }

    // ── Internal ──

    private void send(SseEmitter emitter, String type, Object data, String channel) {
        try {
            emitter.send(SseEmitter.event().name(type).data(data));
        } catch (IOException e) {
            log.warn("SSE {} emit failed: {}", channel, e.getMessage());
        }
    }

    private void startHeartbeat(SseEmitter emitter, ScheduledExecutorService scheduler,
                                Consumer<ScheduledFuture<?>> setter, String channel) {
        cancelHeartbeat(null, setter);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException e) {
                log.warn("SSE {} heartbeat failed: {}", channel, e.getMessage());
                if ("device".equals(channel) && deviceEmitter == emitter) closeDevice();
                else if ("workplace".equals(channel) && workplaceEmitter == emitter) closeWorkplace();
            }
        }, 30, 30, TimeUnit.SECONDS);
        setter.accept(future);
    }

    private void cancelHeartbeat(ScheduledFuture<?> future,
                                 Consumer<ScheduledFuture<?>> setter) {
        if (future != null) future.cancel(false);
        setter.accept(null);
    }

    private void completeEmitter(SseEmitter emitter, Consumer<SseEmitter> setter) {
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception e) {
                log.warn("SSE emitter complete failed: {}", e.getMessage());
            }
            setter.accept(null);
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/tightening/service/SseService.java
git commit -m "refactor: rewrite SseService with dual emitters and string-based emit signature"
```

---

### Task 3: 更新 SseController —— 新增 workplace 端点

**Files:**
- Modify: `src/main/java/com/tightening/controller/SseController.java`

- [ ] **Step 1: 重写 SseController**

```java
package com.tightening.controller;

import com.tightening.service.SseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class SseController {

    private final SseService sseService;

    public SseController(SseService sseService) {
        this.sseService = sseService;
    }

    @GetMapping("/events")
    public SseEmitter deviceEvents() {
        return sseService.createDeviceEmitter();
    }

    @GetMapping("/workplace/events")
    public SseEmitter workplaceEvents() {
        return sseService.createWorkplaceEmitter();
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/tightening/controller/SseController.java
git commit -m "feat: add GET /api/workplace/events SSE endpoint"
```

---

### Task 4: LifecycleEngine 新增 PipelineEventListener + 管道事件发射

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngine.java`

**Interfaces:**
- Consumes: `PipelineEventListener`
- Produces: `onPipelineEvent(PipelineEventListener): void`

- [ ] **Step 1: 新增字段和 setter**

在 `LifecycleEngine.java` 的 `onTighteningJudged` 字段声明后新增：

```java
private PipelineEventListener pipelineEventListener;
```

在 `onTighteningJudged` setter 后新增：

```java
public void onPipelineEvent(PipelineEventListener listener) {
    this.pipelineEventListener = listener;
}
```

新增 import：
```java
import com.tightening.constant.SseEvents;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.util.Converter;
import java.util.Map;
```

- [ ] **Step 2: 修改 advancePipeline —— 用 firePipelineEvents 替代 onTighteningJudged 回调**

将 `advancePipeline()` 方法末尾的判断块：
```java
        if (onTighteningJudged != null
                && stage == Stage.OPERATION && subState == SubState.JUDGING
                && context.getJudgeResult() != null
                && context.getCurrentOperationData() != null) {
            onTighteningJudged.accept(context.getCurrentOperationData());
        }
```

替换为：
```java
        firePipelineEvents(stage, subState);
```

- [ ] **Step 3: 新增 firePipelineEvents 方法（在 advancePipeline 之后、handleStageFailure 之前）**

```java
    private void firePipelineEvents(Stage stage, SubState subState) {
        if (pipelineEventListener == null || context == null) return;

        // tightening:data — JUDGING 完成后推送全量 TighteningDataDTO
        if (stage == Stage.OPERATION && subState == SubState.JUDGING
                && context.getJudgeResult() != null
                && context.getCurrentOperationData() != null) {
            TighteningDataDTO dto = Converter.entity2Dto(
                    context.getCurrentOperationData(), TighteningDataDTO::new);
            pipelineEventListener.onEvent(SseEvents.TIGHTENING_DATA, dto);
        }
    }
```

- [ ] **Step 4: 在 handleStageFailure 中发射 task:control stopped**

在 `handleStageFailure` 方法末尾 `shutdown()` 之前新增：

```java
        if (pipelineEventListener != null && context != null) {
            pipelineEventListener.onEvent(SseEvents.TASK_CONTROL, Map.of(
                    "control", "stopped",
                    "reason", reason != null ? reason : "Capability failed: " + failedCap.id(),
                    "ts", java.time.LocalDateTime.now().toString()
            ));
        }
```

`handleStageFailure` 方法已有 `final String reason` 局部变量（由 `BusinessException.toErrorString(...)` 生成），直接引用即可。方法签名为 `void handleStageFailure(Capability failedCap)`——`reason` 在方法体内第 367 行定义。不需要新增变量。

- [ ] **Step 5: 在 handleFaulted 中发射 task:control stopped**

在 `handleFaulted` 的 `onFaulted` 回调前新增：

```java
        if (pipelineEventListener != null) {
            pipelineEventListener.onEvent(SseEvents.TASK_CONTROL, Map.of(
                    "control", "stopped",
                    "reason", fault.reason(),
                    "ts", java.time.LocalDateTime.now().toString()
            ));
        }
```

- [ ] **Step 6: 编译验证**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/LifecycleEngine.java
git commit -m "feat: add PipelineEventListener and fire tightening:data + task:control events"
```

---

### Task 5: 更新 TaskOrchestrator —— 桥接 workplace SSE 事件 + 监听 CurveDataSavedEvent

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/TaskOrchestrator.java`

**Interfaces:**
- Consumes: `SseEvents`, `PipelineEventListener`, `CurveDataSavedEvent`

- [ ] **Step 1: 更新 trigger 方法中的回调**

修改 import：移除 `SseEventType`, `SseEvent`, `TighteningDataDTO`；新增 `SseEvents`, `Map`。

在 `trigger()` 方法中：

删除整个 `engine.onTighteningJudged(...)` 回调块（改为由 `firePipelineEvents` 统一发射）。

`onFaulted` 回调保持不变（不做 SSE 发射——SSE 由引擎内部在 `handleFaulted` + `handleStageFailure` 中直接发射 PipelineEventListener），仅保留 cleanup + 日志。

`onTriggered` 和 `onCompleted` 回调保持不变（不做 SSE 发射——running 状态前端自管理）。

新增 PipelineEventListener 桥接：

```java
        engine.onPipelineEvent((type, data) -> sseService.emitWorkplace(type, data));
```

放在 `engine.onTighteningJudged(...)` 删除后的位置。

- [ ] **Step 2: 新增 CurveDataSavedEvent 监听器**

在 `TaskOrchestrator` 类中新增：

```java
    @EventListener
    void onCurveDataSaved(CurveDataSavedEvent event) {
        sseService.emitWorkplace(SseEvents.CURVE_DATA, event.getData());
    }
```

新增 import：
```java
import com.tightening.device.event.CurveDataSavedEvent;
```

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/TaskOrchestrator.java
git commit -m "refactor: bridge workplace SSE events + listen CurveDataSavedEvent"
```

---

### Task 6: ToolHandler 发布 CurveDataSavedEvent

**Files:**
- Modify: `src/main/java/com/tightening/device/handler/ToolHandler.java`

**Interfaces:**
- Consumes: `ApplicationEventPublisher`, `CurveDataSavedEvent`

- [ ] **Step 1: 注入 ApplicationEventPublisher 并在 handleCurveData 末尾发布事件**

在 ToolHandler 构造函数参数中新增 `ApplicationEventPublisher eventPublisher`，赋值给字段：

```java
private final ApplicationEventPublisher eventPublisher;
```

构造函数修改为：

```java
    public ToolHandler(NioEventLoopGroup group,
                       DeviceService deviceService,
                       TighteningDataService tighteningDataService,
                       CurveDataService curveDataService,
                       ToolCommonConfig toolCommonConfig,
                       DeviceConfig deviceConfig,
                       ApplicationEventPublisher eventPublisher) {
        super(group, deviceService, toolCommonConfig, deviceConfig);
        this.tighteningDataService = tighteningDataService;
        this.curveDataService = curveDataService;
        this.toolCommonConfig = toolCommonConfig;
        this.eventPublisher = eventPublisher;
    }
```

新增 import：
```java
import com.tightening.device.event.CurveDataSavedEvent;
import com.tightening.dto.CurveDataDTO;
import com.tightening.util.Converter;
import org.springframework.context.ApplicationEventPublisher;
```

在 `handleCurveData` 方法的 `curveDataService.save(data)` 之后新增：

```java
        CurveDataDTO savedDto = Converter.entity2Dto(data, CurveDataDTO::new);
        eventPublisher.publishEvent(new CurveDataSavedEvent(this, savedDto));
```

- [ ] **Step 2: 更新 LifecycleEngineFactory 中 ToolHandler 的构造调用**

`LifecycleEngineFactory` 需要传入 `ApplicationEventPublisher` 到 ToolHandler 构造。检查工厂中 ToolHandler 的创建方式并更新参数。

先确认工厂代码：

```bash
grep -n "new.*ToolHandler\|\bToolHandler\b" src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java
```

- [ ] **Step 3: 确认并更新工厂调用**

ToolHandler 由 Spring 管理（`@Component`），构造参数通过依赖注入自动填充，不需要额外修改工厂。验证：

```bash
grep -rn "ApplicationEventPublisher" src/main/java/com/tightening/device/handler/ --include="*.java"
```

Expected: ToolHandler 的构造参数已加入，Spring 自动注入。

- [ ] **Step 4: 编译验证**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/device/handler/ToolHandler.java
git commit -m "feat: publish CurveDataSavedEvent after curve data saved"
```

---

### Task 7: 更新 DeviceConnectionMonitor —— 新签名

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/monitor/DeviceConnectionMonitor.java`

- [ ] **Step 1: 修改 check() 方法**

移除 import：`SseEventType`, `SseEvent`。新增 import：`SseEvents`, `Map`, `HashMap`。

将：
```java
sseService.emit(new SseEvent(SseEventType.DEVICE_STATUS, current, LocalDateTime.now()));
```
替换为：
```java
Map<String, Object> payload = new HashMap<>(current);
payload.put("ts", LocalDateTime.now().toString());
sseService.emitDevice(SseEvents.DEVICE_STATUS, payload);
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/monitor/DeviceConnectionMonitor.java
git commit -m "refactor: update DeviceConnectionMonitor to new SSE emit signature"
```

---

### Task 8: 更新 WorkplaceStatusService —— 新签名

**Files:**
- Modify: `src/main/java/com/tightening/service/WorkplaceStatusService.java`

- [ ] **Step 1: 修改 transitionTo 方法**

移除 import：`SseEventType`, `SseEvent`, `WorkplaceStatusPayload`。新增 import：`SseEvents`, `Map`。

将：
```java
sseService.emit(new SseEvent(
    SseEventType.WORKPLACE_STATUS,
    new WorkplaceStatusPayload(newStatus, toKeySet(reasons)),
    LocalDateTime.now()));
```
替换为：
```java
sseService.emitWorkplace(SseEvents.WORKPLACE_STATUS, Map.of(
    "status", newStatus.name(),
    "lockReasons", toKeySet(reasons),
    "ts", LocalDateTime.now().toString()
));
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/tightening/service/WorkplaceStatusService.java
git commit -m "refactor: update WorkplaceStatusService to new SSE emit signature"
```

---

### Task 9: 更新 AnengGatewayHandler —— 新签名

**Files:**
- Modify: `src/main/java/com/tightening/device/handler/impl/AnengGatewayHandler.java`

- [ ] **Step 1: 修改 pushDeviceStatusEvent 方法**

移除 import：`SseEventType`, `SseEvent`。新增 import：`SseEvents`。

将：
```java
sseService.emit(new SseEvent(SseEventType.DEVICE_STATUS,
        Map.of("deviceId", deviceId, "status", status),
        LocalDateTime.now()));
```
替换为：
```java
sseService.emitDevice(SseEvents.DEVICE_STATUS, Map.of(
        "deviceId", deviceId,
        "status", status,
        "ts", LocalDateTime.now().toString()
));
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/tightening/device/handler/impl/AnengGatewayHandler.java
git commit -m "refactor: update AnengGatewayHandler to new SSE emit signature"
```

---

### Task 10: 清理 Aneng adapters 未使用的 import

**Files:**
- Modify: `src/main/java/com/tightening/device/handler/impl/aneng/ArrangerAdapter.java`
- Modify: `src/main/java/com/tightening/device/handler/impl/aneng/SetterSelectorAdapter.java`
- Modify: `src/main/java/com/tightening/device/handler/impl/aneng/ArmAdapter.java`

- [ ] **Step 1: 移除 3 个文件中的 stale import**

三个文件各移除：
```java
import com.tightening.constant.SseEventType;
import com.tightening.dto.SseEvent;
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/tightening/device/handler/impl/aneng/ArrangerAdapter.java \
        src/main/java/com/tightening/device/handler/impl/aneng/SetterSelectorAdapter.java \
        src/main/java/com/tightening/device/handler/impl/aneng/ArmAdapter.java
git commit -m "chore: remove stale SSE imports from Aneng sub-device adapters"
```

---

### Task 11: 删除旧 SseEvent 和 SseEventType

**Files:**
- Delete: `src/main/java/com/tightening/dto/SseEvent.java`
- Delete: `src/main/java/com/tightening/constant/SseEventType.java`

- [ ] **Step 1: 确认零引用**

```bash
grep -rn "SseEvent\|SseEventType" src/main/java/ --include="*.java"
```
Expected: 无输出

- [ ] **Step 2: 删除并提交**

```bash
git rm src/main/java/com/tightening/dto/SseEvent.java \
       src/main/java/com/tightening/constant/SseEventType.java
git commit -m "refactor: remove deprecated SseEvent and SseEventType"
```

---

### Task 12: 全量编译 + 测试验证

- [ ] **Step 1: 全量编译**

```bash
mvn clean compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 2: 运行现有测试**

```bash
mvn test -q
```
Expected: 全部测试通过

- [ ] **Step 3: Smoke test —— 启动应用验证端点**

```bash
mvn spring-boot:run &
sleep 10
curl -N -H "Accept: text/event-stream" http://localhost:8080/api/workplace/events &
sleep 2
kill %1 %2 2>/dev/null
```
Expected: 连接成功，收到 heartbeat keepalive 注释

---

### Task 13: 前端对齐要点

**前端改动（由前端工程师执行）：**

1. **API 层** (`shared/api/workplace.ts`)：`onmessage` + JSON.parse 分发 → `addEventListener(type, handler)` 独立注册
2. **Store 层** (`stores/workplace.ts`)：`handleEvent` switch-case → 改为各事件独立 listener，事件名对齐新常量
3. **Types** (`shared/types/workplace.ts`)：`WorkplaceEventType` 更新为 `'task:control' | 'tightening:data' | 'curve:data' | 'device:status' | 'workplace:status'`
