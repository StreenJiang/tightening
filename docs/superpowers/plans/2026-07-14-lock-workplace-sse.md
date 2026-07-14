# 锁机制 + 工作台状态 + SSE 推送 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 SSE 推送框架，实现 WorkplaceStatus 状态机，重写 LockStateMonitor 为 lockReasons 驱动模型，升级 DeviceConnectionMonitor 为系统级监控。

**Architecture:** SseService（基础设施）→ WorkplaceStatusService（桥接）→ LockStateMonitor + DeviceConnectionMonitor（消费者）。Capability 通过 LockReason 枚举间接控制锁，不再直接操作 ToolHandler。增量搭建，每步 TDD。

**Tech Stack:** Java 21, Spring Boot 3.5.10, JUnit 5 + AssertJ 3.27.7, SseEmitter

## Global Constraints

- Java 21, Spring Boot 3.5.10
- TDD：先写测试看到红灯，再写实现看到绿灯
- 手术式修改，不触碰无关代码
- 六质量标准：高可维护性、高可读性、高内聚、低耦合、高性能、轻量
- 单工位场景，无并发问题

---

## File Structure

**新增:**
| 文件 | 职责 |
|------|------|
| `constant/LockReason.java` | 锁原因枚举 |
| `constant/WorkplaceStatus.java` | 工作台 4 状态枚举 |
| `constant/SseEventType.java` | SSE 事件类型枚举 |
| `dto/SseEvent.java` | SSE 事件 record |
| `dto/WorkplaceStatusPayload.java` | WorkplaceStatus SSE payload |
| `service/SseService.java` | SSE 推送服务，单 SseEmitter，登录生命周期 |
| `service/WorkplaceStatusService.java` | 工作台状态服务，独立于 MissionOrchestrator |

**修改:**
| 文件 | 改动 |
|------|------|
| `device/contract/ITool.java` | 新增 `isUnlocked()` |
| `device/contract/ToolAdapter.java` | 实现 `isUnlocked()` |
| `lifecycle/MissionContext.java` | lockMessages → lockReasons，+boltUnlockOverride |
| `lifecycle/monitor/LockStateMonitor.java` | 完整重写（lockReasons 驱动 + boltUnlockOverride 检查） |
| `lifecycle/monitor/DeviceConnectionMonitor.java` | 重写（不再实现 PersistentMonitor，系统级） |
| `lifecycle/LifecycleEngineFactory.java` | 移除 DeviceConnectionMonitor 注册 |
| `lifecycle/LifecycleEngine.java` | 注入 WorkplaceStatusService，触发点调用 |
| `lifecycle/MissionOrchestrator.java` | 传递 WorkplaceStatusService 到引擎 |
| `device/DeviceManager.java` | 启动/停止 DeviceConnectionMonitor + SseService |
| `lifecycle/capability/SendPSet.java` | 改用 lockReasons |
| `lifecycle/capability/AdvanceBolt.java` | SWITCH_BOLT 时重置 boltUnlockOverride |

**删除:**
| 文件 | 原因 |
|------|------|
| `lifecycle/LockMessage.java` | 由 LockReason 替代 |

---

### Task 1: LockReason 枚举 + 测试

**Files:**
- Create: `src/main/java/com/tightening/constant/LockReason.java`
- Create: `src/test/java/com/tightening/constant/LockReasonTest.java`

**Interfaces:**
- Produces: `LockReason.PSET_SENDING`, `ARRANGER_POSITIONING`, `SOCKET_SELECTING`, `ADMIN_CONFIRM` — 每个有 `key` 和 `displayName`

- [ ] **Step 1: 写测试**

```java
// src/test/java/com/tightening/constant/LockReasonTest.java
package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LockReasonTest {

    @Test
    @DisplayName("每个 LockReason 应有非空的 key 和 displayName")
    void shouldHaveNonEmptyKeyAndDisplayName() {
        for (LockReason reason : LockReason.values()) {
            assertThat(reason.getKey()).isNotBlank();
            assertThat(reason.getDisplayName()).isNotBlank();
        }
    }

    @Test
    @DisplayName("key 和 displayName 应不同（key 为英文标识，displayName 为中文展示）")
    void keyAndDisplayNameShouldDiffer() {
        for (LockReason reason : LockReason.values()) {
            assertThat(reason.getKey()).isNotEqualTo(reason.getDisplayName());
        }
    }

    @Test
    @DisplayName("应包含 4 个枚举值")
    void shouldHaveFourValues() {
        assertThat(LockReason.values()).hasSize(4);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -pl . -Dtest="LockReasonTest" -DfailIfNoTests=false
```
Expected: compilation error / FAIL (文件不存在)

- [ ] **Step 3: 实现枚举**

```java
// src/main/java/com/tightening/constant/LockReason.java
package com.tightening.constant;

import lombok.Getter;

@Getter
public enum LockReason {
    PSET_SENDING("pSetSending", "程序号下发中"),
    ARRANGER_POSITIONING("arrangerPositioning", "送钉中"),
    SOCKET_SELECTING("socketSelecting", "套筒选择中"),
    ADMIN_CONFIRM("adminConfirm", "需管理员确认");

    private final String key;
    private final String displayName;

    LockReason(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -pl . -Dtest="LockReasonTest" -DfailIfNoTests=false
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/constant/LockReason.java \
        src/test/java/com/tightening/constant/LockReasonTest.java
git commit -m "feat: add LockReason enum with key and Chinese displayName"
```

---

### Task 2: SseEventType + SseEvent + WorkplaceStatusPayload — 基础 DTO

**Files:**
- Create: `src/main/java/com/tightening/constant/SseEventType.java`
- Create: `src/main/java/com/tightening/dto/SseEvent.java`
- Create: `src/main/java/com/tightening/dto/WorkplaceStatusPayload.java`

**Interfaces:**
- Produces: `SseEventType.WORKPLACE_STATUS, TIGHTENING_DATA, DEVICE_STATUS`
- Produces: `new SseEvent(SseEventType type, Object payload, LocalDateTime timestamp)`
- Produces: `new WorkplaceStatusPayload(WorkplaceStatus status, Map<String, String> lockReasons)`
- Consumes: `WorkplaceStatus` 枚举（Task 3 定义，此处先引用）— 两个 task 独立提交，编译需 Task 3 先合入，或此 task 调换顺序

> **注意**: Task 2 依赖 `WorkplaceStatus` 枚举（Task 3）。实际执行时 Task 2 和 Task 3 可对调顺序。

- [ ] **Step 1: 写测试**

```java
// src/test/java/com/tightening/dto/SseEventTest.java
package com.tightening.dto;

import com.tightening.constant.SseEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventTest {

    @Test
    @DisplayName("SseEvent record 应正确存储 type/payload/timestamp")
    void shouldStoreFields() {
        var now = LocalDateTime.now();
        var payload = Map.of("key", "value");
        var event = new SseEvent(SseEventType.DEVICE_STATUS, payload, now);

        assertThat(event.type()).isEqualTo(SseEventType.DEVICE_STATUS);
        assertThat(event.payload()).isSameAs(payload);
        assertThat(event.timestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("WorkplaceStatusPayload 应正确存储 status 和 lockReasons")
    void shouldStoreWorkplaceStatusPayload() {
        var reasons = Map.of("pSetSending", "程序号下发中");
        var payload = new WorkplaceStatusPayload(
                com.tightening.constant.WorkplaceStatus.OPERATION_ENABLE, reasons);

        assertThat(payload.status()).isEqualTo(
                com.tightening.constant.WorkplaceStatus.OPERATION_ENABLE);
        assertThat(payload.lockReasons()).containsEntry("pSetSending", "程序号下发中");
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -pl . -Dtest="SseEventTest" -DfailIfNoTests=false
```
Expected: FAIL

- [ ] **Step 3: 实现**

```java
// src/main/java/com/tightening/constant/SseEventType.java
package com.tightening.constant;

public enum SseEventType {
    WORKPLACE_STATUS,
    TIGHTENING_DATA,
    DEVICE_STATUS
}
```

```java
// src/main/java/com/tightening/dto/SseEvent.java
package com.tightening.dto;

import com.tightening.constant.SseEventType;
import java.time.LocalDateTime;

public record SseEvent(SseEventType type, Object payload, LocalDateTime timestamp) {}
```

```java
// src/main/java/com/tightening/dto/WorkplaceStatusPayload.java
package com.tightening.dto;

import com.tightening.constant.WorkplaceStatus;
import java.util.Map;

public record WorkplaceStatusPayload(WorkplaceStatus status, Map<String, String> lockReasons) {}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -pl . -Dtest="SseEventTest" -DfailIfNoTests=false
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/constant/SseEventType.java \
        src/main/java/com/tightening/dto/SseEvent.java \
        src/main/java/com/tightening/dto/WorkplaceStatusPayload.java \
        src/test/java/com/tightening/dto/SseEventTest.java
git commit -m "feat: add SseEvent, SseEventType, and WorkplaceStatusPayload DTOs"
```

---

### Task 3: WorkplaceStatus 枚举 + 测试

**Files:**
- Create: `src/main/java/com/tightening/constant/WorkplaceStatus.java`
- Create: `src/test/java/com/tightening/constant/WorkplaceStatusTest.java`

**Interfaces:**
- Produces: `WorkplaceStatus.UNACTIVATED, ACTIVATED, OPERATION_ENABLE, OPERATION_DISABLE`

- [ ] **Step 1: 写测试**

```java
// src/test/java/com/tightening/constant/WorkplaceStatusTest.java
package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkplaceStatusTest {

    @Test
    @DisplayName("应包含 4 个状态值")
    void shouldHaveFourValues() {
        assertThat(WorkplaceStatus.values()).hasSize(4);
        assertThat(WorkplaceStatus.valueOf("UNACTIVATED")).isNotNull();
        assertThat(WorkplaceStatus.valueOf("ACTIVATED")).isNotNull();
        assertThat(WorkplaceStatus.valueOf("OPERATION_ENABLE")).isNotNull();
        assertThat(WorkplaceStatus.valueOf("OPERATION_DISABLE")).isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -pl . -Dtest="WorkplaceStatusTest" -DfailIfNoTests=false
```
Expected: FAIL

- [ ] **Step 3: 实现**

```java
// src/main/java/com/tightening/constant/WorkplaceStatus.java
package com.tightening.constant;

public enum WorkplaceStatus {
    UNACTIVATED,
    ACTIVATED,
    OPERATION_ENABLE,
    OPERATION_DISABLE
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -pl . -Dtest="WorkplaceStatusTest" -DfailIfNoTests=false
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/constant/WorkplaceStatus.java \
        src/test/java/com/tightening/constant/WorkplaceStatusTest.java
git commit -m "feat: add WorkplaceStatus enum (4-state)"
```

---

### Task 4: ITool.isUnlocked() 接口变更

**Files:**
- Modify: `src/main/java/com/tightening/device/contract/ITool.java`
- Modify: `src/main/java/com/tightening/device/contract/ToolAdapter.java`
- Create: `src/test/java/com/tightening/device/contract/ToolAdapterIsUnlockedTest.java`

**Interfaces:**
- Produces: `ITool.isUnlocked() -> boolean`
- Produces: `ToolAdapter.isUnlocked()` 委托 `handler.isUnlocked(deviceId)`

- [ ] **Step 1: 写测试**

```java
// src/test/java/com/tightening/device/contract/ToolAdapterIsUnlockedTest.java
package com.tightening.device.contract;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.handler.ToolHandler;
import com.tightening.entity.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolAdapterIsUnlockedTest {

    private ToolHandler handler;
    private ToolAdapter adapter;
    private Device device;

    @BeforeEach
    void setUp() {
        handler = mock(ToolHandler.class);
        device = new Device();
        device.setId(2L);
        device.setType(DeviceType.FIT_FTC6.getId());
        adapter = new ToolAdapter(handler, device);
    }

    @Test
    @DisplayName("isUnlocked 应委托 handler.isUnlocked(deviceId)")
    void shouldDelegateToHandler() {
        when(handler.isUnlocked(2L)).thenReturn(true);
        assertThat(adapter.isUnlocked()).isTrue();

        when(handler.isUnlocked(2L)).thenReturn(false);
        assertThat(adapter.isUnlocked()).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -pl . -Dtest="ToolAdapterIsUnlockedTest" -DfailIfNoTests=false
```
Expected: compilation error (ITool 无 isUnlocked 方法)

- [ ] **Step 3: 实现**

在 `ITool.java` 新增：

```java
boolean isUnlocked();
```

在 `ToolAdapter.java` 新增：

```java
@Override
public boolean isUnlocked() {
    return handler.isUnlocked(device.getId());
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -pl . -Dtest="ToolAdapterIsUnlockedTest" -DfailIfNoTests=false
```
Expected: PASS

- [ ] **Step 5: 编译验证其余代码不受影响**

```bash
mvn compile -q
```
Expected: 无编译错误

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/tightening/device/contract/ITool.java \
        src/main/java/com/tightening/device/contract/ToolAdapter.java \
        src/test/java/com/tightening/device/contract/ToolAdapterIsUnlockedTest.java
git commit -m "feat: add isUnlocked() to ITool interface"
```

---

### Task 5: SseService

**Files:**
- Create: `src/main/java/com/tightening/service/SseService.java`
- Create: `src/test/java/com/tightening/service/SseServiceTest.java`

**Interfaces:**
- Consumes: `SseEvent`, `SseEventType` (from Task 2)
- Produces: `SseService.create() -> SseEmitter`, `emit(SseEvent)`, `close()`

- [ ] **Step 1: 写测试**

```java
// src/test/java/com/tightening/service/SseServiceTest.java
package com.tightening.service;

import com.tightening.constant.SseEventType;
import com.tightening.dto.SseEvent;
import org.junit.jupiter.api.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SseServiceTest {

    private SseService service;

    @BeforeEach
    void setUp() {
        service = new SseService();
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    @DisplayName("create() 应返回非空 SseEmitter")
    void shouldCreateSseEmitter() {
        SseEmitter emitter = service.create();
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("emit() 不应抛异常（emitter 存在时）")
    void shouldEmitWithoutException() {
        service.create();
        var event = new SseEvent(SseEventType.DEVICE_STATUS, "test", LocalDateTime.now());
        // emit 应静默完成（无异常 = 通过）
        assertThatCode(() -> service.emit(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("close() 后再次 create() 应创建新 emitter")
    void shouldAllowRecreateAfterClose() {
        SseEmitter first = service.create();
        service.close();
        SseEmitter second = service.create();
        assertThat(second).isNotSameAs(first);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -pl . -Dtest="SseServiceTest" -DfailIfNoTests=false
```
Expected: FAIL

- [ ] **Step 3: 实现**

```java
// src/main/java/com/tightening/service/SseService.java
package com.tightening.service;

import com.tightening.dto.SseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SseService {

    private volatile SseEmitter emitter;
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> heartbeatFuture;

    public SseEmitter create() {
        emitter = new SseEmitter(0L);
        startHeartbeat();
        return emitter;
    }

    public void emit(SseEvent event) {
        SseEmitter current = this.emitter;
        if (current == null) return;
        try {
            current.send(SseEmitter.event()
                .name(event.type().name())
                .data(event));
        } catch (IOException e) {
            log.warn("SSE emit failed: {}", e.getMessage());
        }
    }

    public void close() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }
        SseEmitter current = this.emitter;
        if (current != null) {
            try {
                current.complete();
            } catch (Exception e) {
                log.warn("SSE emitter complete failed: {}", e.getMessage());
            }
            this.emitter = null;
        }
    }

    private void startHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }
        heartbeatFuture = heartbeat.scheduleAtFixedRate(() -> {
            SseEmitter current = this.emitter;
            if (current == null) return;
            try {
                current.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException e) {
                log.warn("SSE heartbeat failed: {}", e.getMessage());
                close();
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -pl . -Dtest="SseServiceTest" -DfailIfNoTests=false
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/service/SseService.java \
        src/test/java/com/tightening/service/SseServiceTest.java
git commit -m "feat: add SseService with heartbeat and lifecycle management"
```

---

### Task 6: WorkplaceStatusService

**Files:**
- Create: `src/main/java/com/tightening/service/WorkplaceStatusService.java`
- Create: `src/test/java/com/tightening/service/WorkplaceStatusServiceTest.java`

**Interfaces:**
- Consumes: `SseService` (Task 5), `WorkplaceStatus` (Task 3), `WorkplaceStatusPayload` (Task 2)
- Produces: `transitionTo(WorkplaceStatus, Set<LockReason>)`, `reset()`, `current()`

- [ ] **Step 1: 写测试**

```java
// src/test/java/com/tightening/service/WorkplaceStatusServiceTest.java
package com.tightening.service;

import com.tightening.constant.LockReason;
import com.tightening.constant.WorkplaceStatus;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkplaceStatusServiceTest {

    private SseService sseService;
    private WorkplaceStatusService wsService;

    @BeforeEach
    void setUp() {
        sseService = mock(SseService.class);
        wsService = new WorkplaceStatusService(sseService);
    }

    @Test
    @DisplayName("初始状态应为 UNACTIVATED")
    void shouldStartUnactivated() {
        assertThat(wsService.current()).isEqualTo(WorkplaceStatus.UNACTIVATED);
    }

    @Test
    @DisplayName("transitionTo 应更新状态并推 SSE")
    void shouldTransitionAndEmit() {
        wsService.transitionTo(WorkplaceStatus.ACTIVATED, Set.of());

        assertThat(wsService.current()).isEqualTo(WorkplaceStatus.ACTIVATED);
        verify(sseService, times(1)).emit(any());
    }

    @Test
    @DisplayName("transitionTo 应推送带 lockReasons 的 payload")
    void shouldEmitWithLockReasons() {
        var reasons = Set.of(LockReason.PSET_SENDING);
        wsService.transitionTo(WorkplaceStatus.OPERATION_DISABLE, reasons);

        verify(sseService, times(1)).emit(any());
    }

    @Test
    @DisplayName("reset() 应回到 UNACTIVATED 且清除 lockReasons")
    void shouldResetToUnactivated() {
        wsService.transitionTo(WorkplaceStatus.OPERATION_ENABLE, Set.of(LockReason.ADMIN_CONFIRM));
        wsService.reset();

        assertThat(wsService.current()).isEqualTo(WorkplaceStatus.UNACTIVATED);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -pl . -Dtest="WorkplaceStatusServiceTest" -DfailIfNoTests=false
```
Expected: FAIL

- [ ] **Step 3: 实现**

```java
// src/main/java/com/tightening/service/WorkplaceStatusService.java
package com.tightening.service;

import com.tightening.constant.LockReason;
import com.tightening.constant.SseEventType;
import com.tightening.constant.WorkplaceStatus;
import com.tightening.dto.SseEvent;
import com.tightening.dto.WorkplaceStatusPayload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WorkplaceStatusService {

    private final SseService sseService;
    private volatile WorkplaceStatus status = WorkplaceStatus.UNACTIVATED;
    private volatile Set<LockReason> lockReasons = Set.of();

    public WorkplaceStatusService(SseService sseService) {
        this.sseService = sseService;
    }

    public WorkplaceStatus current() {
        return status;
    }

    public void transitionTo(WorkplaceStatus newStatus, Set<LockReason> reasons) {
        this.status = newStatus;
        this.lockReasons = reasons;
        sseService.emit(new SseEvent(
            SseEventType.WORKPLACE_STATUS,
            new WorkplaceStatusPayload(newStatus, toDisplayMap(reasons)),
            LocalDateTime.now()));
    }

    public void reset() {
        transitionTo(WorkplaceStatus.UNACTIVATED, Set.of());
    }

    private Map<String, String> toDisplayMap(Set<LockReason> reasons) {
        return reasons.stream()
            .collect(Collectors.toMap(LockReason::getKey, LockReason::getDisplayName));
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -pl . -Dtest="WorkplaceStatusServiceTest" -DfailIfNoTests=false
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/service/WorkplaceStatusService.java \
        src/test/java/com/tightening/service/WorkplaceStatusServiceTest.java
git commit -m "feat: add WorkplaceStatusService with SSE emission"
```

---

### Task 7: MissionContext 变更

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/MissionContext.java`
- Delete: `src/main/java/com/tightening/lifecycle/LockMessage.java`
- Modify: `src/test/java/com/tightening/lifecycle/capability/StoreDataTest.java` — 如有引用旧 lockMessages
- Modify: `src/test/java/com/tightening/lifecycle/MissionContextTest.java` — 如有

**Interfaces:**
- Consumes: `LockReason` (Task 1)
- Produces: `MissionContext.getLockReasons() -> Set<LockReason>`, `isBoltUnlockOverride() -> boolean`, `setBoltUnlockOverride(boolean)`

- [ ] **Step 1: 写测试**

```java
// src/test/java/com/tightening/lifecycle/MissionContextLockReasonsTest.java
package com.tightening.lifecycle;

import com.tightening.constant.LockReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissionContextLockReasonsTest {

    @Test
    @DisplayName("lockReasons 初始应为空集合")
    void shouldStartWithEmptyLockReasons() {
        var ctx = MissionContext.builder()
            .productMissionId(1L)
            .shouldSelfLoop(false)
            .boltConfigs(java.util.List.of())
            .deviceRegistry(java.util.Map.of())
            .build();

        assertThat(ctx.getLockReasons()).isEmpty();
    }

    @Test
    @DisplayName("boltUnlockOverride 初始应为 false")
    void shouldStartWithBoltUnlockOverrideFalse() {
        var ctx = MissionContext.builder()
            .productMissionId(1L)
            .shouldSelfLoop(false)
            .boltConfigs(java.util.List.of())
            .deviceRegistry(java.util.Map.of())
            .build();

        assertThat(ctx.isBoltUnlockOverride()).isFalse();
    }

    @Test
    @DisplayName("lockReasons 应可增删")
    void shouldAddAndRemoveLockReasons() {
        var ctx = MissionContext.builder()
            .productMissionId(1L)
            .shouldSelfLoop(false)
            .boltConfigs(java.util.List.of())
            .deviceRegistry(java.util.Map.of())
            .build();

        ctx.getLockReasons().add(LockReason.PSET_SENDING);
        assertThat(ctx.getLockReasons()).hasSize(1);
        assertThat(ctx.getLockReasons()).contains(LockReason.PSET_SENDING);

        ctx.getLockReasons().remove(LockReason.PSET_SENDING);
        assertThat(ctx.getLockReasons()).isEmpty();
    }

    @Test
    @DisplayName("boltUnlockOverride 应可设置")
    void shouldSetBoltUnlockOverride() {
        var ctx = MissionContext.builder()
            .productMissionId(1L)
            .shouldSelfLoop(false)
            .boltConfigs(java.util.List.of())
            .deviceRegistry(java.util.Map.of())
            .build();

        ctx.setBoltUnlockOverride(true);
        assertThat(ctx.isBoltUnlockOverride()).isTrue();

        ctx.setBoltUnlockOverride(false);
        assertThat(ctx.isBoltUnlockOverride()).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -pl . -Dtest="MissionContextLockReasonsTest" -DfailIfNoTests=false
```
Expected: FAIL（lockReasons 字段仍是 Set<LockMessage>）

- [ ] **Step 3: 修改 MissionContext**

在 `MissionContext.java` 中：

删除或替换第 3 行 import：
```java
// 删除: import com.tightening.lifecycle.LockMessage;
```

替换 `lockMessages` 字段（第 54 行）：
```java
@Builder.Default
private final Set<LockReason> lockReasons = new LinkedHashSet<>();
```

新增 `boltUnlockOverride` 字段（在 lockReasons 之后）：
```java
@Builder.Default @Setter
private volatile boolean boltUnlockOverride = false;
```

新增 import：
```java
import com.tightening.constant.LockReason;
```

删除 `LockMessage.java`：
```bash
rm src/main/java/com/tightening/lifecycle/LockMessage.java
```

- [ ] **Step 4: 修复所有编译错误**

搜索项目中引用 `LockMessage` 或 `getLockMessages()` 的地方：

```bash
grep -rn "LockMessage\|getLockMessages" src/main/java/ src/test/java/
```

预期只有 `LockStateMonitor.java` 引用，Task 8 会修复。如果测试文件有引用，同步更新。

- [ ] **Step 5: 运行测试验证通过**

```bash
mvn test -pl . -Dtest="MissionContextLockReasonsTest" -DfailIfNoTests=false
mvn compile -q  # 确认无编译错误
```
Expected: PASS + 编译无错误

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/MissionContext.java \
        src/test/java/com/tightening/lifecycle/MissionContextLockReasonsTest.java
git rm src/main/java/com/tightening/lifecycle/LockMessage.java
git commit -m "refactor: replace LockMessage with LockReason enum in MissionContext

- Replace Set<LockMessage> lockMessages with Set<LockReason> lockReasons
- Add volatile boolean boltUnlockOverride field
- Delete LockMessage.java"
```

---

### Task 8: LockStateMonitor 重写

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/monitor/LockStateMonitor.java`
- Modify: `src/test/java/com/tightening/lifecycle/monitor/LockStateMonitorTest.java`（如存在）或 Create 新测试

**Interfaces:**
- Consumes: `MissionContext.getLockReasons()`, `MissionContext.isBoltUnlockOverride()`, `ITool.isUnlocked()`, `WorkplaceStatusService`
- Produces: LockStateMonitor 成为 lock/unlock 的唯一执行者

- [ ] **Step 1: 写测试**

```java
// src/test/java/com/tightening/lifecycle/monitor/LockStateMonitorTest.java
package com.tightening.lifecycle.monitor;

import com.tightening.constant.DeviceType;
import com.tightening.constant.LockReason;
import com.tightening.constant.WorkplaceStatus;
import com.tightening.device.contract.ITool;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.WorkplaceStatusService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LockStateMonitorTest {

    private WorkplaceStatusService wsService;
    private LockStateMonitor monitor;
    private ITool tool;

    @BeforeEach
    void setUp() {
        wsService = mock(WorkplaceStatusService.class);
        monitor = new LockStateMonitor(wsService);
        tool = mock(ITool.class);
    }

    @Test
    @DisplayName("intervalMs 应为 50ms")
    void shouldReturn50msInterval() {
        assertThat(monitor.intervalMs()).isEqualTo(50);
    }

    @Test
    @DisplayName("lockReasons 非空 + tool 已 unlock → 应发送 lock")
    void shouldLockWhenReasonsNotEmptyAndToolUnlocked() {
        var ctx = ctxWithTool(tool);
        ctx.getLockReasons().add(LockReason.PSET_SENDING);
        when(tool.isUnlocked()).thenReturn(true);

        monitor.execute(ctx);

        verify(tool, times(1)).sendLock();
        verify(wsService, times(1)).transitionTo(
                eq(WorkplaceStatus.OPERATION_DISABLE), anySet());
    }

    @Test
    @DisplayName("lockReasons 为空 + tool 已 lock → 应发送 unlock")
    void shouldUnlockWhenReasonsEmptyAndToolLocked() {
        var ctx = ctxWithTool(tool);
        when(tool.isUnlocked()).thenReturn(false);

        monitor.execute(ctx);

        verify(tool, times(1)).sendUnlock();
        verify(wsService, times(1)).transitionTo(
                eq(WorkplaceStatus.OPERATION_ENABLE), eq(Set.of()));
    }

    @Test
    @DisplayName("boltUnlockOverride 为 true → 跳过所有逻辑")
    void shouldSkipWhenBoltUnlockOverrideTrue() {
        var ctx = ctxWithTool(tool);
        ctx.setBoltUnlockOverride(true);
        ctx.getLockReasons().add(LockReason.PSET_SENDING);

        monitor.execute(ctx);

        verify(tool, never()).sendLock();
        verify(tool, never()).sendUnlock();
        verify(wsService, never()).transitionTo(any(), any());
    }

    @Test
    @DisplayName("状态已匹配时不应重复发送")
    void shouldNotRedundantSend() {
        var ctx = ctxWithTool(tool);
        ctx.getLockReasons().add(LockReason.ADMIN_CONFIRM);
        when(tool.isUnlocked()).thenReturn(false);  // already locked

        monitor.execute(ctx);

        verify(tool, never()).sendLock();
        verify(tool, never()).sendUnlock();
    }

    private static MissionContext ctxWithTool(ITool tool) {
        return MissionContext.builder()
            .productMissionId(1L)
            .shouldSelfLoop(false)
            .boltConfigs(java.util.List.of())
            .deviceRegistry(Map.of(1L, tool))
            .build();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -pl . -Dtest="LockStateMonitorTest" -DfailIfNoTests=false
```
Expected: FAIL（当前 LockStateMonitor 是空壳）

- [ ] **Step 3: 重写 LockStateMonitor**

```java
// src/main/java/com/tightening/lifecycle/monitor/LockStateMonitor.java
package com.tightening.lifecycle.monitor;

import com.tightening.constant.WorkplaceStatus;
import com.tightening.device.contract.ITool;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.WorkplaceStatusService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;

@Slf4j
public class LockStateMonitor implements PersistentMonitor {

    private final WorkplaceStatusService wsService;

    public LockStateMonitor(WorkplaceStatusService wsService) {
        this.wsService = wsService;
    }

    @Override
    public long intervalMs() {
        return 50;
    }

    @Override
    public void execute(MissionContext ctx) {
        if (ctx.isBoltUnlockOverride()) {
            return;
        }

        boolean shouldLock = !ctx.getLockReasons().isEmpty();

        for (ITool tool : ctx.getDeviceRegistry().values()) {
            boolean currentlyLocked = !tool.isUnlocked();
            if (shouldLock == currentlyLocked) {
                continue;
            }

            if (shouldLock) {
                tool.sendLock();
                wsService.transitionTo(WorkplaceStatus.OPERATION_DISABLE,
                    new HashSet<>(ctx.getLockReasons()));
                log.debug("LockStateMonitor: lock — reasons: {}", ctx.getLockReasons());
            } else {
                tool.sendUnlock();
                wsService.transitionTo(WorkplaceStatus.OPERATION_ENABLE,
                    java.util.Set.of());
                log.debug("LockStateMonitor: unlock");
            }
        }
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -pl . -Dtest="LockStateMonitorTest" -DfailIfNoTests=false
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/monitor/LockStateMonitor.java \
        src/test/java/com/tightening/lifecycle/monitor/LockStateMonitorTest.java
git commit -m "feat: rewrite LockStateMonitor with lockReasons-driven logic"
```

---

### Task 9: lockMsgs 写入方 — SendPSet 改造

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/capability/SendPSet.java`
- Modify: `src/test/java/com/tightening/lifecycle/capability/SendPSetTest.java`（如存在）

**Interfaces:**
- Consumes: `LockReason.PSET_SENDING`, `MissionContext.getLockReasons()`

- [ ] **Step 1: 写测试**

```java
// 在已有 SendPSetTest 中新增测试
@Test
@DisplayName("execute 应在发送前添加 PSET_SENDING 到 lockReasons，完成后移除")
void shouldAddAndRemovePsetSwitchingLockReason() {
    var ctx = testContext();
    when(tool.sendPSet(2)).thenReturn(CompletableFuture.completedFuture(true));

    cap.execute(ctx);

    assertThat(ctx.getLockReasons()).doesNotContain(LockReason.PSET_SENDING); // 已移除
    verify(tool, times(1)).sendPSet(2);
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -pl . -Dtest="SendPSetTest" -DfailIfNoTests=false
```
Expected: FAIL（锁原因未被添加）

- [ ] **Step 3: 修改 SendPSet.execute()**

在 `sendPSetCmd` 调用前后包装：

```java
ctx.getLockReasons().add(LockReason.PSET_SENDING);
tool.sendPSet(psetId)
    .whenComplete((ok, ex) -> {
        try {
            // handle result
        } finally {
            ctx.getLockReasons().remove(LockReason.PSET_SENDING);
        }
    });
```

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -pl . -Dtest="SendPSetTest" -DfailIfNoTests=false
```
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/SendPSet.java \
        src/test/java/com/tightening/lifecycle/capability/SendPSetTest.java
git commit -m "feat: SendPSet uses lockReasons instead of direct tool lock/unlock"
```

---

### Task 10: DeviceConnectionMonitor 升级 + LifecycleEngineFactory 清理

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/monitor/DeviceConnectionMonitor.java`
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java`
- Modify: `src/test/java/com/tightening/lifecycle/monitor/DeviceConnectionMonitorTest.java`（如存在）或 Create

**Interfaces:**
- Consumes: `SseService`, `DeviceRegistry`
- Produces: `start()`, `stop()` 替代 `PersistentMonitor.execute()`

- [ ] **Step 1: 写测试**

```java
// src/test/java/com/tightening/lifecycle/monitor/DeviceConnectionMonitorTest.java
package com.tightening.lifecycle.monitor;

import com.tightening.constant.DeviceType;
import com.tightening.device.contract.ITool;
import com.tightening.device.DeviceRegistry;
import com.tightening.service.SseService;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class DeviceConnectionMonitorTest {

    private DeviceRegistry registry;
    private SseService sseService;
    private DeviceConnectionMonitor monitor;
    private ITool tool;

    @BeforeEach
    void setUp() {
        registry = mock(DeviceRegistry.class);
        sseService = mock(SseService.class);
        tool = mock(ITool.class);
        monitor = new DeviceConnectionMonitor(registry, sseService);
    }

    @AfterEach
    void tearDown() {
        monitor.stop();
    }

    @Test
    @DisplayName("start() 应启动后台调度")
    void shouldStartScheduler() {
        when(registry.values()).thenReturn(Map.of(1L, tool));
        when(tool.isConnected()).thenReturn(true);
        when(tool.id()).thenReturn(1L);

        monitor.start();
        assertThat(monitor.isRunning()).isTrue();
    }

    @Test
    @DisplayName("设备状态变更时应推送 SSE")
    void shouldEmitWhenStatusChanges() throws Exception {
        when(registry.values()).thenReturn(Map.of(1L, tool));
        when(tool.isConnected()).thenReturn(false, true);
        when(tool.id()).thenReturn(1L);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(sseService).emit(any());

        monitor.start();
        latch.await(3, TimeUnit.SECONDS);

        verify(sseService, atLeastOnce()).emit(any());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
mvn test -pl . -Dtest="DeviceConnectionMonitorTest" -DfailIfNoTests=false
```
Expected: FAIL

- [ ] **Step 3: 重写 DeviceConnectionMonitor + 修改 LifecycleEngineFactory**

重写 `DeviceConnectionMonitor.java`：

```java
// src/main/java/com/tightening/lifecycle/monitor/DeviceConnectionMonitor.java
package com.tightening.lifecycle.monitor;

import com.tightening.constant.SseEventType;
import com.tightening.device.DeviceRegistry;
import com.tightening.dto.SseEvent;
import com.tightening.service.SseService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DeviceConnectionMonitor {

    private final DeviceRegistry registry;
    private final SseService sseService;
    private ScheduledExecutorService scheduler;
    private Map<Long, Boolean> lastStatus = Map.of();
    private volatile boolean running = false;

    public DeviceConnectionMonitor(DeviceRegistry registry, SseService sseService) {
        this.registry = registry;
        this.sseService = sseService;
    }

    public void start() {
        if (running) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "device-connection-monitor");
            t.setDaemon(true);
            return t;
        });
        running = true;
        scheduler.scheduleAtFixedRate(this::check, 0, 1000, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void check() {
        try {
            Map<Long, Boolean> current = new HashMap<>();
            var tools = registry.values();
            if (tools == null) return;
            for (var entry : tools.entrySet()) {
                current.put(entry.getKey(), entry.getValue().isConnected());
            }
            if (!current.equals(lastStatus)) {
                lastStatus = current;
                sseService.emit(new SseEvent(SseEventType.DEVICE_STATUS, current, LocalDateTime.now()));
            }
        } catch (Exception e) {
            log.warn("DeviceConnectionMonitor check error", e);
        }
    }
}
```

在 `LifecycleEngineFactory.java` 中删除 `new DeviceConnectionMonitor()` 注册行（第 77 行附近）。

- [ ] **Step 4: 运行测试验证通过**

```bash
mvn test -pl . -Dtest="DeviceConnectionMonitorTest" -DfailIfNoTests=false
mvn compile -q
```
Expected: PASS + 编译无错误

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/monitor/DeviceConnectionMonitor.java \
        src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java \
        src/test/java/com/tightening/lifecycle/monitor/DeviceConnectionMonitorTest.java
git commit -m "refactor: upgrade DeviceConnectionMonitor to system-level, remove from engine monitors"
```

---

### Task 11: LifecycleEngine + MissionOrchestrator + DeviceManager 集成

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngine.java`
- Modify: `src/main/java/com/tightening/device/DeviceManager.java`
- Modify: `src/main/java/com/tightening/lifecycle/capability/AdvanceBolt.java`

**Interfaces:**
- Consumes: `WorkplaceStatusService`
- Produces: `handleTriggerRequest` 成功时调用 `wsService.transitionTo(ACTIVATED, ...)`，shutdown 时调用 `wsService.reset()`

- [ ] **Step 1: 修改 LifecycleEngine**

在 `LifecycleEngine` 中注入 `WorkplaceStatusService` 并在关键点调用：

```java
// 构造函数新增参数
private final WorkplaceStatusService wsService;

// handleTriggerRequest() 中 trigger pipeline Pass 后:
wsService.transitionTo(WorkplaceStatus.ACTIVATED, Set.of());

// shutdown() 中:
wsService.reset();
```

在 `AdvanceBolt.java` 的 SWITCH_BOLT 逻辑中：

```java
ctx.setBoltUnlockOverride(false);
```

在 `DeviceManager.java` 登录/登出流程中：

```java
// userLoggedIn():
deviceConnectionMonitor.start();

// 最后一个用户登出:
deviceConnectionMonitor.stop();
sseService.close();
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```
Expected: 无错误

- [ ] **Step 3: 运行全量测试**

```bash
mvn test
```
Expected: 全量通过

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: integrate WorkplaceStatus/SSE/LockStateMonitor into lifecycle engine"
```

---

### Task 12: Controller SSE 端点

**Files:**
- Create: 在已有 Controller 中新增 SSE 端点（如 `DeviceController` 或新建 `SseController`）

- [ ] **Step 1: 新增 SSE Controller**

```java
// src/main/java/com/tightening/controller/SseController.java
package com.tightening.controller;

import com.tightening.service.SseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class SseController {

    private final SseService sseService;

    public SseController(SseService sseService) {
        this.sseService = sseService;
    }

    @GetMapping("/events")
    public SseEmitter events() {
        return sseService.create();
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```
Expected: 无错误

- [ ] **Step 3: 全量测试**

```bash
mvn test
```
Expected: 全量通过

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/controller/SseController.java
git commit -m "feat: add SSE endpoint GET /api/events"
```

---

## 验证检查点

- [ ] `mvn test` 全量通过
- [ ] `grep -rn "LockMessage" src/main` 无残留
- [ ] `grep -rn "getLockMessages" src/main` 无残留
- [ ] LockStateMonitor 正确引用 `LockReason` 枚举
- [ ] DeviceConnectionMonitor 不再 implements PersistentMonitor
- [ ] SseService 心跳正常（30s keepalive）
- [ ] WorkplaceStatus 状态转换与 CONTEXT.md 一致
