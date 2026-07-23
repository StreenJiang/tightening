# 锁机制 + 工作台状态 + SSE 推送 — 设计文档

基于 gap tracker `docs/superpowers/specs/2026-07-14-gap-tracker.md` 的新功能组（N3-N6, N8）。

---

## 1. SSE 推送框架

### 1.1 SseService

全局单例，登录会话绑定。

```java
@Component
public class SseService {
    private volatile SseEmitter emitter;
    private final ScheduledExecutorService heartbeat;

    public SseEmitter create() {
        SseEmitter previous = this.emitter;
        if (previous != null) {
            try { previous.complete(); } catch (Exception e) { /* ignore */ }
        }
        emitter = new SseEmitter(0L);  // 无超时，登出时手动关闭
        startHeartbeat();
        return emitter;
    }

    public void emit(SseEvent event) { ... }

    public void close() {
        heartbeat.shutdownNow();
        emitter.complete();
        emitter = null;  // 允许再次登录创建新 emitter
    }
}
```

- 登录时 Controller 调用 `SseService.create()` 返回 `SseEmitter` 给前端
- 登出/过期时 `SseService.close()`
- 心跳：每 30s 发 SSE comment `:keepalive`，防 proxy 断开
- 非线程安全——单工位场景无并发问题

### 1.2 SseEvent

```java
public record SseEvent(SseEventType type, Object payload, LocalDateTime timestamp) {}
```

`Object payload` 用 Jackson 序列化为 JSON。

### 1.3 SseEventType

```java
public enum SseEventType {
    WORKPLACE_STATUS,   // WorkplaceStatusPayload
    TIGHTENING_DATA,    // TighteningDataDTO
    DEVICE_STATUS       // Map<Long, Boolean> deviceId → connected
}
```

### 1.4 发送方约定

各业务组件注入 `SseService`，只调用 `emit()`。不关心连接状态、前端是否断开——emit 失败时 log warn 即可。

---

## 2. WorkplaceStatus

### 2.1 状态枚举

```java
public enum WorkplaceStatus {
    UNACTIVATED,
    ACTIVATED,
    OPERATION_ENABLE,
    OPERATION_DISABLE
}
```

### 2.2 WorkplaceStatusService

独立服务，不依赖 TaskOrchestrator。

```java
@Component
public class WorkplaceStatusService {
    private final SseService sseService;
    private volatile WorkplaceStatus status = WorkplaceStatus.UNACTIVATED;
    private volatile Set<LockReason> lockReasons = Set.of();

    public WorkplaceStatus current() { return status; }

    public void transitionTo(WorkplaceStatus newStatus, Set<LockReason> reasons) {
        this.status = newStatus;
        this.lockReasons = reasons;
        sseService.emit(new SseEvent(WORKPLACE_STATUS,
            new WorkplaceStatusPayload(newStatus, toDisplayMap(reasons)), now()));
    }

    public void reset() {
        transitionTo(UNACTIVATED, Set.of());
    }
}
```

### 2.3 状态转换触发点

| 转换 | 触发者 | 位置 |
|------|--------|------|
| UNACTIVATED → ACTIVATED | Trigger pipeline Pass | LifecycleEngine.handleTriggerRequest() success 分支 |
| ACTIVATED → OPERATION_ENABLE | LockStateMonitor 首次 unlock（lockMsgs 为空） | LockStateMonitor.execute() |
| ACTIVATED → OPERATION_DISABLE | LockStateMonitor 首次 lock（lockMsgs 非空） | LockStateMonitor.execute() |
| OPERATION_ENABLE ↔ OPERATION_DISABLE | LockStateMonitor 后续 lock/unlock | LockStateMonitor.execute() |
| 任意 → UNACTIVATED | Task OK/NG/引擎异常/登出 | LifecycleEngine shutdown 路径 + DeviceManager 登出 |

进入 OPERATION 阶段前 WorkplaceStatus 保持 ACTIVATED。LockStateMonitor 在 OPERATION 阶段启动后执行首次判断：lockMsgs 空 → enable，非空 → disable。

### 2.4 SSE Payload

```java
public record WorkplaceStatusPayload(
    WorkplaceStatus status,
    Map<String, String> lockReasons  // key → 中文展示名
) {}
```

---

## 3. LockMsg 与 LockStateMonitor

### 3.1 LockReason 枚举

```java
public enum LockReason {
    PSET_SENDING("pSetSending", "程序号下发中"),
    ARRANGER_POSITIONING("arrangerPositioning", "送钉中"),
    SOCKET_SELECTING("socketSelecting", "套筒选择中"),
    ADMIN_CONFIRM("adminConfirm", "需管理员确认");

    private final String key;
    private final String displayName;
}
```

### 3.2 TaskContext 变更

```java
// 替换 Set<LockMessage> 为:
@Builder.Default
private final Set<LockReason> lockReasons = new LinkedHashSet<>();

// 新增:
@Builder.Default @Setter
private volatile boolean boltUnlockOverride = false;
```

### 3.3 LockStateMonitor 逻辑

```java
@Override
public void execute(TaskContext ctx) {
    if (ctx.isBoltUnlockOverride()) {
        return;  // 管理员手动 unlock，跳过
    }

    boolean shouldLock = !ctx.getLockReasons().isEmpty();

    for (ITool tool : ctx.getDeviceRegistry().values()) {
        boolean currentlyLocked = !tool.isUnlocked();       // isUnlocked=false 即 locked
        if (shouldLock == currentlyLocked) continue;         // 无需变更

        if (shouldLock) {
            tool.sendLock();
            workplaceStatusService.transitionTo(OPERATION_DISABLE, ctx.getLockReasons());
        } else {
            tool.sendUnlock();
            workplaceStatusService.transitionTo(OPERATION_ENABLE, Set.of());
        }
    }
}
```

每条 tick 遍历 deviceRegistry 中所有 Tool，按 lockReasons 状态统一决策。单工位场景通常只有一台 Tool，循环体执行一次。

### 3.4 lockMsgs 写入方

各 Capability 操作 lockReasons 而非直接调 ToolHandler：

```java
// SendPSet.execute():
ctx.getLockReasons().add(LockReason.PSET_SENDING);
tool.sendPSet(psetId)
    .whenComplete((ok, ex) -> {
        try {
            // ... handle result ...
        } finally {
            ctx.getLockReasons().remove(LockReason.PSET_SENDING);
        }
    });
```

`sendPSet()` 是异步调用，`remove` 必须放在 `whenComplete` 回调中，否则 `LockStateMonitor` 观测不到锁原因。

`SendArrangerSignal`、`SendSetterSelector` 同理。

### 3.5 boltUnlockOverride

- 管理员在生命周期内手动 unlock → `ctx.setBoltUnlockOverride(true)` → 同时 force unlock tool
- SWITCH_BOLT 时 `AdvanceBolt` Capability 重置 `ctx.setBoltUnlockOverride(false)`
- 生命周期外管理员 lock/unlock 直接调 ToolHandler，不涉及此字段

### 3.6 删除 LockMessage.java

旧 `LockMessage` record 删除，由 `LockReason` 枚举替代。

---

## 4. DeviceConnectionMonitor 升级

### 4.1 从引擎级迁出

- `LifecycleEngineFactory` 中删除 `new DeviceConnectionMonitor()` 注册
- `DeviceConnectionMonitor` 不再实现 `PersistentMonitor`

### 4.2 新实现

```java
@Component
public class DeviceConnectionMonitor {
    private final DeviceRegistry registry;
    private final SseService sseService;
    private ScheduledExecutorService scheduler;
    private Map<Long, Boolean> lastStatus = Map.of();

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::check, 0, 1000, MILLISECONDS);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void check() {
        Map<Long, Boolean> current = new HashMap<>();
        for (var entry : registry.values()) {
            current.put(entry.id(), entry.isConnected());
        }
        if (!current.equals(lastStatus)) {
            lastStatus = current;
            sseService.emit(new SseEvent(DEVICE_STATUS, current, now()));
        }
    }
}
```

### 4.3 生命周期

- `DeviceManager.userLoggedIn()` → `deviceConnectionMonitor.start()`
- `DeviceManager` 最后一个用户登出 → `deviceConnectionMonitor.stop()` + `sseService.close()`

---

## 5. 依赖关系

```
SseService                          ← 无依赖
WorkplaceStatusService              ← SseService
LockStateMonitor (增强)             ← WorkplaceStatusService
lockMsgs 写入方 (Capability 修改)   ← LockReason 枚举
DeviceConnectionMonitor (升级)      ← SseService + DeviceRegistry
TaskContext (boltUnlockOverride)  ← 独立字段
```

SSE 是基础设施，WorkplaceStatusService 是桥——连接状态展示和锁机制。

---

## 6. 需要删除的旧代码

| 文件 | 操作 |
|------|------|
| `LockMessage.java` | 删除，由 LockReason 替代 |
| `LifecycleEngineFactory.java:77` | 删除 DeviceConnectionMonitor 注册行 |
| `DeviceConnectionMonitor.java` | 重写（不再 implements PersistentMonitor） |

## 7. 接口变更

| 接口 | 变更 |
|------|------|
| `ITool` | 新增 `boolean isUnlocked()` |
| `ToolAdapter` | 实现 `isUnlocked()`，委托 `handler.isUnlocked(deviceId)` |

## 8. 新增文件

| 文件 | 内容 |
|------|------|
| `constant/LockReason.java` | 锁原因枚举 |
| `constant/WorkplaceStatus.java` | 工作台状态枚举 |
| `constant/SseEventType.java` | SSE 事件类型枚举 |
| `service/SseService.java` | SSE 推送服务 |
| `service/WorkplaceStatusService.java` | 工作台状态服务 |
| `dto/SseEvent.java` | SSE 事件 DTO |
| `dto/WorkplaceStatusPayload.java` | WorkplaceStatus SSE payload |
