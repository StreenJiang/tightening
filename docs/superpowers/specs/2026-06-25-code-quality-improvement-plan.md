# 代码质量改进计划

> 日期: 2026-06-25
> 基于: 2026-06-21 Task 生命周期架构设计 + 当前代码审查
> 目标架构: 方案 B — 完整 Actor 模型
> 原则: 本项目是新开发项目，当前不完善处是待完善目标，非 bug

---

## 1. 审查结论摘要

### 1.1 当前强项

| 维度 | 状态 |
|------|------|
| 数据层 | 9 张配置表 + tightening_data/curve_data/task_record，Flyway 迁移规范 |
| 设备通信层 | Netty + Atlas/FIT 协议，工厂模式解耦，新增设备类型零改动 |
| 测试覆盖 | 90+ 测试文件，覆盖 entity/enum/DTO/controller/protocol |
| 配置层 CRUD | RESTful API，校验规则完备（循环依赖检测、key_char 长度校验等） |
| 级联删除 | Service 层显式管理，比隐式 CASCADE 可控 |

### 1.2 生命周期的数据层 100% 就绪

`product_task` + `product_side` + `product_bolt` 三张表覆盖了生命周期设计所需的全部配置数据（max_ng_count、扭矩/角度范围、PSet、条码规则、前置依赖、点检绑定）。

### 1.3 核心断层

当前 `ToolHandler.handleTighteningData()` 直接将拧紧数据存入数据库，没有经过任何生命周期环节：

```
设备 → InBoundHandler → ToolHandler → tighteningDataService.save() → DB
```

生命周期设计要求：

```
设备 → InBoundHandler → Actor inbox → onMessage → executeStage(OPERATION)
    → JUDGING → STORING → ADVANCING → DB
```

---

## 2. 目标架构：方案 B — 完整 Actor 模型

### 2.1 选择理由

- 生命周期设计文档本身就是 Actor 模型，选方案 B 最大化设计资产复用
- Actor 模型天然保证线程安全，消除当前 `ReentrantLock` + `volatile` 的并发复杂度
- 消息驱动的 inbox 机制为后续多客户版本（SCII/GLB/YMT）的 Capability 差异化提供统一扩展点
- 阻断式确认（AdminConfirm、BoltBarCodeCheck）在 Actor 模型中有天然的消息驱动实现

### 2.2 实施哲学：先建骨架，再填血肉

Actor 模型不是一次性重写整个项目。实施策略：

1. **LifecycleEngine 先作为独立的"黑盒"组件** — 与现有代码并行运行，通过适配器连接
2. **设备层不直接改造** — ToolHandler 通过 Adapter 将数据投递到 inbox，内部实现不变
3. **判定逻辑优先落地** — JudgmentStrategy 接口 + Atlas/FIT 实现是独立组件，不依赖 Actor
4. **Capability 渐进式实现** — 先做默认管道（详见 §2.3 管道表全部 17 个 Capability），扩展 Capability（MaxNGCheck、BuzzerAlert、AdminConfirm、BoltBarCodeCheck 等）后续添加
5. **崩溃恢复内置 — 不是附加功能** — Actor 模型单线程运行，线程崩溃导致 Context 全部丢失。从第一天起在关键转换点（子状态推进前、TaskRecord 创建后、存储数据后）将 Context 摘要写入 TaskRecord 的 `context_snapshot` 字段。Actor 启动时检测上次 checkpoint，尝试恢复。**崩溃恢复不是后期可以安全添加的功能** — 它决定了 checkpoint 字段在 Context 中的位置、持久化策略、恢复路径；后期加会需要改所有 Capability 的 execute 签名

---

## 3. 实施阶段

### 阶段 0: 代码质量完善（前置，约 1-2 天）

生命周期引擎实施前，先修复当前代码中影响可维护性的问题。

#### 0.1 Service 方法封装

将分散在 Controller 层的 MyBatis-Plus lambdaQuery 调用提取到 Service：

| 当前（Controller 中） | 应改为（Service 中） |
|---|---|
| `taskService.lambdaQuery().eq(deleted,0).orderByDesc(id).last(...)` | `taskService.listByPage(page, size)` |
| `prerequisiteService.lambdaQuery().eq(taskId).eq(deleted,0).list()` | `prerequisiteService.listByTaskId(taskId)` |
| `barcodeRuleService.lambdaQuery().eq(taskId).eq(deleted,0).list()` | `barcodeRuleService.listByTaskId(taskId)` |
| `bindingService.lambdaQuery().eq(inspectionTaskId).eq(deleted,0).list()` | `bindingService.listByInspectionTaskId(taskId)` |

**原因**: 生命周期引擎需要通过 Service 方法查询数据（如 "查某个 task 下所有螺栓"），如果 Service 是空壳则无处承载这些查询。

**具体变更**:

```java
// TighteningDataService — 空壳 → 封装查询
public List<TighteningData> listByTaskRecordId(Long taskRecordId) {
    return lambdaQuery()
        .eq(TighteningData::getTaskRecordId, taskRecordId)
        .eq(TighteningData::getDeleted, 0)
        .list();
}

// TaskRecordService — 空壳 → 封装激活/完成操作
public TaskRecord createRecord(Long productTaskId, String productCode, Integer isRework) {
    TaskRecord record = new TaskRecord()
        .setProductTaskId(productTaskId)
        .setProductCode(productCode)
        .setIsRework(isRework)
        .setTaskResult(TaskResult.NG.getCode());  // 激活时初始为 NG
    save(record);
    return record;
}

public void markAsOk(Long recordId) {
    lambdaUpdate().eq(TaskRecord::getId, recordId)
        .set(TaskRecord::getTaskResult, TaskResult.OK.getCode())
        .update();
}

// ProductBoltService — 新增按 taskId 查询
public List<ProductBolt> listByTaskId(Long taskId) {
    // 通过 sideId 关联查询
    ...
}
```

#### 0.2 ProductTaskController 分页改用 MyBatis-Plus Page

```java
// 替换:
.last("LIMIT " + safeSize + " OFFSET " + ((safePage - 1) * safeSize))

// 改为:
Page<ProductTask> page = new Page<>(page, size);
taskService.lambdaQuery()
    .eq(ProductTask::getDeleted, 0)
    .orderByDesc(ProductTask::getId)
    .page(page);
```

#### 0.3 统一 Controller 响应格式

新建 `ApiResponse<T>`:

```java
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "ok", data);
    }
    public static ApiResponse<String> ok() {
        return new ApiResponse<>(200, "ok", null);
    }
    public static ApiResponse<String> fail(String message) {
        return new ApiResponse<>(500, message, null);
    }
}
```

所有 Controller 返回 `ResponseEntity<ApiResponse<T>>`。涉及 ProductTaskController、ProductBoltController、ProductSideController、UserAccountInfoController 共 4 个。DeviceController 保持 DeferredResult 异步模式不变，LoginController 为测试桩不改造。

#### 0.4 `task_record_id` 改为可空（tightening_data + curve_data）

在 V1.0.11 迁移中：

1. `tightening_data.task_record_id` — `INTEGER NOT NULL` → `INTEGER`（允许 NULL）
2. `curve_data.task_record_id` — `INTEGER NOT NULL` → `INTEGER`（允许 NULL）

并重建表（SQLite 不支持 ALTER COLUMN）。Java 侧同步将 `TighteningData.taskRecordId` 从 `long` 改为 `Long`，`CurveData.taskRecordId` 同理。

**原因**: 生命周期引擎上线前，拧紧数据和曲线数据到达时没有关联的 task_record。允许 NULL 使引擎可以渐进式部署。primitive `long` 默认值为 0，无法区分"未关联"和"关联 ID=0 的记录"。

#### 0.5 `task_record` 增加崩溃恢复字段

在 V1.0.12 迁移中为 `task_record` 表增加：

```sql
ALTER TABLE task_record ADD COLUMN context_snapshot TEXT;  -- JSON，可为 NULL，记录关键转换点的 Context 摘要
ALTER TABLE task_record ADD COLUMN fault_message TEXT;     -- 可为 NULL，Actor 线程崩溃时的异常信息
```

**原因**: §2.2 崩溃恢复机制需要在关键转换点将 Context 摘要写入 `context_snapshot`，Actor 启动时检测并尝试恢复。不是后期可安全添加的功能。

### 阶段 1: 设备接口抽象 + JudgmentStrategy（约 2-3 天）

生命周期引擎通过接口访问设备，不与 Netty 实现耦合。

#### 1.1 新建设备接口包 `com.tightening.device.contract`

```java
public interface IDevice {
    Long id();
    DeviceType type();
    boolean isConnected();
}

public interface ITool extends IDevice {
    CompletableFuture<Boolean> sendPSet(int psetId, CancellationToken token);
    CompletableFuture<Boolean> sendLock(CancellationToken token);
    CompletableFuture<Boolean> sendUnlock(CancellationToken token);
    CompletableFuture<Boolean> sendBarcode(String barcode, CancellationToken token);
    void onTighteningData(Consumer<TighteningDataDTO> callback);
    void onCurveData(Consumer<CurveDataDTO> callback);
}

public interface IPreconditionCheckable {
    PreconditionResult checkPrecondition();
}
```

#### 1.2 新建适配器 `ToolAdapter`

将现有 `ToolHandler` 包装为 `ITool`:

```java
public class ToolAdapter implements ITool {
    private final ToolHandler handler;
    private final Long deviceId;
    
    @Override
    public CompletableFuture<Boolean> sendPSet(int psetId, CancellationToken token) {
        return handler.sendPSetOp(deviceId, psetId);  // 首阶段忽略 token，后续 ToolHandler 支持取消时传入
    }
    // sendLock/sendUnlock/sendBarcode 同理
}
```

`DeviceRegistry` 管理 `Map<Long, ITool>` 的注册和查询。

#### 1.3 JudgmentStrategy 接口 + 实现

```java
public interface JudgmentStrategy {
    JudgmentResult judge(TighteningData data);
}

public record JudgmentResult(boolean isOk, String reason) {}

// Atlas: checking tighteningStatus + torqueStatus + angleStatus
public class AtlasJudgment implements JudgmentStrategy { ... }

// FIT: checking tighteningStatus only
public class FitJudgment implements JudgmentStrategy { ... }
```

`Map<DeviceType, JudgmentStrategy>` 注册表在 Spring 配置中初始化。

### 阶段 2: LifecycleEngine 核心（约 3-5 天）

#### 2.1 Context

> **技术审查新增**：
> - `volatile` 关键字移除 — Actor 模型单线程串行处理，不存在跨线程可见性问题
> - 字段分类为三层：核心字段 / 管道间传递数据 / Capability 间临时数据
> - 新增 `checkpoint` 用于崩溃恢复
> - `extras{}` 保留但有明确边界：仅 Capability 间同一线程内的临时数据交换，不持久化、不参与业务判定
> - **以下为阶段 2 首次实现的最小子集。完整 Context 字段定义参见生命周期设计文档 §4.2，其余字段（workstationConfig、workplaceStatus、barcodeObj、cancellationToken、设备开关等）在对应 Capability 实现时按需补充**

```java
public class TaskContext {
    // ═══ 第一层：核心字段（引擎核心代码和 Capability 的 precondition/execute 直接访问） ═══
    
    // 不可变（Workstation 就绪时注入，生命周期内只读）
    final ProductTask taskData;
    final List<ProductBolt> boltConfigs;        // 从 product_bolt 加载，按 boltSerialNum 排序
    final Map<Long, ITool> deviceRegistry;       // 设备引用
    final boolean shouldSelfLoop;
    
    // 可变（引擎核心代码维护，生命周期运行时状态）
    Stage currentStage;
    SubState currentSubState;
    final BoltState[] boltStates;
    int currentBoltIndex;
    int currentSideIndex;
    TaskRecord taskRecord;
    final List<TighteningData> tighteningDataList;  // 本生命周期收集的数据
    boolean interruptRequested;
    String interruptReason;
    
    // ═══ 第二层：管道间传递数据（相邻 Capability 间通过 Context 字段传递的明确数据） ═══
    
    TighteningData currentOperationData;   // JUDGING 写 → STORING 读
    TighteningData previousOperationData;  // 双槽位曲线匹配用
    final List<CurveData> pendingCurveData; // 待匹配曲线队列
    JudgmentResult judgeResult;            // JUDGING 写 → STORING/ADVANCING 读
    int tighteningStatus;                  // ControllerStatusCheck 写 → 诊断/UI 读，控制器原始判定
    final Set<LockMessage> lockMessages;   // DevicePreconditionMonitor 等写 → LockStateMonitor 读
                                           // LockMessage: record(source, reason)，source 决定优先级
                                           // "MANUAL_LOCK"/"MANUAL_UNLOCK" 最高优先级，覆盖其他所有
    
    // ═══ 第三层：Capability 间临时数据（extras{}，仅客户特定扩展场景使用） ═══
    // 边界：不参与引擎核心判定逻辑，不持久化，Capability 自行维护读写契约
    // 示例：DataCacheForOuterDB 将临时缓存的数据 ID 放入 extras，StoreToOuterDB 从中读取
    final Map<String, Object> extras;
    
    // ═══ 崩溃恢复 ═══
    // 关键转换点（子状态推进前、TaskRecord 创建后、StoreData 后）写入的快照
    ContextCheckpoint checkpoint;
}
```

#### 2.2 LifecycleEngine Actor

> **技术审查新增**：Actor 循环实现修正。生命周期设计文档 §7.5（引擎并发模型）已正确使用 `inbox.take()`（阻塞等待），以下在此基础上的修正实现：用消息处理器注册表替代 §4.4 的 `switch(msg)` 分发。

Actor 模型保证单线程串行处理。这意味着：
- **不需要 `volatile` 关键字** — Actor 内无并发可见性问题
- **不需要 `poll()` 轮询** — 阻塞等待消息即可，避免 CPU 空转  
- **MonitorTick 由独立定时器驱动** — 不混入 actorLoop

```java
public class LifecycleEngine {
    private final BlockingQueue<InboundMessage> inbox = new LinkedBlockingQueue<>();
    private final Map<Class<?>, MessageHandler> handlers = new HashMap<>();  // 消息处理器注册表
    private final ScheduledExecutorService tickScheduler = Executors.newSingleThreadScheduledExecutor();
    private TaskContext context;
    private boolean alive = false;
    private Thread actorThread;
    
    // === 消息处理器注册表 ===
    // 替代 switch(msg)：每种消息类型有独立 handler，新增消息类型只需注册 handler，不改 actorLoop
    
    @FunctionalInterface
    interface MessageHandler {
        void handle(InboundMessage msg, TaskContext ctx, LifecycleEngine engine);
    }
    
    public void registerHandler(Class<?> msgType, MessageHandler handler) {
        handlers.put(msgType, handler);
    }
    
    // === Actor 主循环（阻塞等待消息，无轮询） ===
    private void actorLoop() {
        while (alive) {
            InboundMessage msg;
            try {
                msg = inbox.take();  // 阻塞等待，非 poll(100ms) 空转
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                MessageHandler handler = handlers.get(msg.getClass());
                if (handler != null) {
                    handler.handle(msg, context, this);
                } else {
                    log.warn("Unknown message type: {}", msg.getClass().getSimpleName());
                }
            } catch (Exception e) {
                handleActorCrash(e, msg);
            }
        }
    }
    
    // === 崩溃恢复 ===
    // Actor 线程 executePipeline/Capability 执行中抛异常 → 尝试记住恢复点
    
    private void handleActorCrash(Exception e, InboundMessage msg) {
        log.error("Actor 线程异常崩溃, message={}", msg, e);
        if (context == null) return;
        // 1. 记录故障到 task_record（如果有）
        if (context.taskRecord != null && context.taskRecord.getId() != null) {
            taskRecordService.markFaulted(context.taskRecord.getId(), e.getMessage());
        }
        // 2. 记住当前阶段的 checkpoint 位置（供恢复时用）
        saveCheckpoint(context, CrashPoint.of(context.currentStage, context.currentSubState));
        // 3. 投递 Faulted 消息让引擎进入安全收尾
        try {
            inbox.put(new EngineInternal.Faulted(e.getMessage()));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
    
    // === 定时器驱动的 MonitorTick ===
    // 持久监控器不混入 actorLoop，由独立定时器周期性投递
    
    public void startMonitorTicks() {
        tickScheduler.scheduleAtFixedRate(
            () -> inbox.offer(new EngineInternal.MonitorTick()),
            0, 50, TimeUnit.MILLISECONDS  // ~50ms 周期，与 LockStateMonitor 需求对齐
        );
    }
    
    public void stopMonitorTicks() {
        tickScheduler.shutdownNow();
    }
    
    // === 生命周期控制 ===
    
    public void start(TaskContext ctx) {
        this.context = ctx;
        this.alive = true;
        // 注册默认消息处理器
        registerDefaultHandlers();
        // 启动 Actor 线程
        actorThread = new Thread(this::actorLoop, "lifecycle-engine-" + ctx.taskData.getId());
        actorThread.setUncaughtExceptionHandler((t, e) -> {
            log.error("Actor 线程未捕获异常", e);
            handleActorCrash(e, null);
        });
        actorThread.start();
        // 投递激活消息
        try {
            inbox.put(new InboundCommand.ActivateTask());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void postMessage(InboundMessage msg) {
        if (alive) inbox.offer(msg);
    }
    
    public void interrupt(String reason) {
        if (context.currentStage == Stage.FINALIZATION) return;  // FINALIZATION 不可中断
        context.interruptRequested = true;
        context.interruptReason = reason;
    }
}
```

#### 2.3 Pipeline + Capability

```java
public interface Capability {
    String id();
    Stage stage();
    SubState subState();
    int priority();
    boolean precondition(TaskContext ctx);
    CapabilityResult execute(TaskContext ctx);
    ErrorAction onError(TaskContext ctx, Exception e);
}

enum CapabilityResult { Pass, Fail, Skip, Interrupt }
```

默认管道（第一阶段实现的 Capability）:

| Stage | SubState | Capability |
|-------|----------|-----------|
| VALIDATION | VALIDATING | WorkstationConfigCheck |
| ACTIVATION | PREPARING | PrepareBolts |
| ACTIVATION | ACTIVATING | CreateTaskRecord |
| OPERATION | SWITCH_BOLT | SendArrangerSignal → SendSetterSelector → SendPSet |
| OPERATION | TIGHTENING_RECEIVED | ReceiveData |
| OPERATION | JUDGING | ControllerStatusCheck → TorqueRangeCheck → AngleRangeCheck |
| OPERATION | STORING | StoreData |
| OPERATION | ADVANCING | AdvanceBolt |
| FINALIZATION | CLEANING_TASKS | CancelTasks |
| FINALIZATION | LOCKING_TOOLS | LockTools |
| FINALIZATION | RESETTING_STATE | ResetState |
| FINALIZATION | EXPORTING | ExportData → SelfLoopCheck |

#### 2.4 持久监控器

```java
public interface PersistentMonitor {
    long intervalMs();
    void execute(TaskContext ctx);
}

// 实现:
public class LockStateMonitor implements PersistentMonitor { ... }
public class DeviceConnectionMonitor implements PersistentMonitor { ... }
```

### 阶段 3: Outbox Worker（约 1-2 天）

```sql
-- V1.0.13: export_task 表
CREATE TABLE export_task (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    type             TEXT NOT NULL,
    task_record_id INTEGER NOT NULL,
    payload          TEXT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'PENDING',
    retry_count      INTEGER NOT NULL DEFAULT 0,
    max_retries      INTEGER NOT NULL DEFAULT 3,
    error_message    TEXT,
    created_at       TEXT,
    completed_at     TEXT
);
```

```java
@Component
public class ExportWorker {
    @Scheduled(fixedDelay = 5000)
    public void processPending() {
        List<ExportTask> tasks = exportTaskService.findPending(10);
        for (ExportTask task : tasks) {
            Exporter exporter = ExporterRegistry.get(task.getType());
            // 执行导出，处理重试
        }
    }
}
```

### 阶段 4: 设备层适配（约 1-2 天）

修改 `ToolHandler.handleTighteningData()`:

```java
// 之前:
public void handleTighteningData(TighteningDataDTO dto, Channel channel) {
    TighteningData data = Converter.dto2Entity(dto, TighteningData::new);
    TCPDeviceHandler.applyToolTypeName(channel, data);
    tighteningDataService.save(data);
}

// 之后:
public void handleTighteningData(TighteningDataDTO dto, Channel channel) {
    TighteningData data = Converter.dto2Entity(dto, TighteningData::new);
    TCPDeviceHandler.applyToolTypeName(channel, data);
    // 投递给生命周期引擎，不再直接存储
    long deviceId = channel.attr(DEVICE_ID).get();
    lifecycleEngine.postMessage(new DeviceEvent.TighteningData(data, deviceId));
}
```

### 阶段 5: 集成测试 + 端到端验证（约 2-3 天）

---

## 4. 对生命周期设计文档的修改建议

当前设计文档 `docs/2026-06-21-task-lifecycle-architecture-design.md` 是一个很好的架构蓝图。以下是根据代码审查提出的修改建议：

### 4.1 Context 中需要新增的字段

| 新增字段 | 类型 | 原因 |
|----------|------|------|
| `productTaskId` | Long | 数据层以 ID 关联，Context 需要持有以便存储时填写外键 |
| `deviceBindings[]` | BoltDeviceBinding[] | 螺栓→排列机/套筒选择器的映射，当前通过 `bolt_device_binding` 表承载 |
| `partsBarcodes[]` | BoltPartsBarcode[] | 螺栓→物料码规则的映射 |

### 4.2 工作台就绪（Workstation Readiness）需要独立设计

当前设计文档中"工作台就绪"作为生命周期外部的前置步骤，但没有具体的设计。建议新增一节：

```
工作台就绪流程:
  1. 用户选择 Task → 加载 ProductTask + ProductSide + ProductBolt
  2. 查询 BoltDeviceBinding → 确定需要哪些设备（工具/排列机/套筒选择器）
  3. 从 DeviceRegistry 获取设备引用（设备已在用户登录时连接）
  4. 加载 BarCodeMatchingRule → 确定扫码规则
  5. 组装 TaskContext 不可变部分 → READY
```

### 4.3 `DeviceRegistry` 与现有 `DeviceManager` 的关系

设计文档的 `DeviceRegistry` 和当前代码的 `DeviceManager` 职责重叠。建议明确：

- `DeviceManager`（现有）— 管理 Netty 连接生命周期（connect/disconnect/scan/reconnect）
- `DeviceRegistry`（新增）— 设备查询入口，包装 `DeviceManager`，提供 `ITool`/`IArm` 接口视图

`DeviceRegistry` 不管理连接——它是只读查询层。

### 4.4 Outbox 表补充

设计文档 §8 定义了 `export_task` 表，建议补充 `export_task` 的 Flyway 迁移编号和 Service/Mapper 文件清单。

### 4.5 自循环简化建议（技术审查新增）

当前设计文档 §2.3 列出了 10 种触发场景（场景 A-J），其中自循环涉及 4 种（D: SELF_LOOP+扫码, E: SELF_LOOP+PLC, F: SELF_LOOP+无条码, G: 混合）。本质上是 5 种触发信号 × 4 种条码获取方式的组合爆炸。

建议改为 **自循环正交化设计**：

```
核心逻辑（不变）：
  FINALIZATION OK + shouldSelfLoop=true → 投递 SELF_LOOP 信号 → 重回触发阶段

条码获取方式（正交维度，不参与场景组合）：
  BarcodeAcquisitionStrategy 接口，引擎初始化时注入，不受自循环影响
```

```java
// BarcodeAcquisitionStrategy — 自循环与条码获取解耦
public interface BarcodeAcquisitionStrategy {
    /**
     * 获取条码。返回 CompletableFuture 以支持异步获取：
     * - MANUAL_INPUT: 等待操作员扫码 → future.complete(barcode)
     * - PLC_READ:     PLC 定时器投递 PlcPollTick → 读到后 complete
     * - HTTP_BODY:    HTTP 请求体中携带 → 立即 complete
     * - NONE:         立即 complete(null)
     */
    CompletableFuture<String> acquireBarcode(TaskContext ctx);
}
```

**简化效果**：
- 自循环只需要关心是否要发 SELF_LOOP 信号，不关心条码怎么来
- 场景不再枚举，而是由 `TriggerSignal` × `BarcodeAcquisitionStrategy` 运行时组合
- 新增条码获取方式不需要改任何触发流程代码
- 生命周期设计文档 §2.3 的场景表格可删除，改为一段简短说明即可

### 4.6 Outbox 导出统一抽象建议（技术审查新增）

当前设计文档 §8 定义了 `export_task` 表结构和 ExportWorker 轮询逻辑，但缺少 `Exporter` 接口的统一抽象。建议补充：

```java
/**
 * 导出执行器接口。
 * 不同导出目标（DB/文件/网络）实现此接口，注册到 ExporterRegistry。
 * ExportData Capability 通过 type 名称查找 Exporter，不感知具体实现。
 */
public interface Exporter {
    /** 导出类型标识，与 export_task.type 对应 */
    String type();
    
    /** 执行导出 */
    ExportResult execute(ExportPayload payload);
}

// 内置实现
public class StandardExcelExporter implements Exporter {
    @Override public String type() { return "standard_excel"; }
    @Override public ExportResult execute(ExportPayload payload) {
        // 生成 Excel 文件
    }
}

public class OuterDatabaseStorer implements Exporter {
    @Override public String type() { return "outer_db_store"; }
    @Override public ExportResult execute(ExportPayload payload) {
        // 写入外部数据库
    }
}

public class PlcResultSender implements Exporter {
    @Override public String type() { return "send_plc_result"; }
    @Override public ExportResult execute(ExportPayload payload) {
        // 通过 PLC 适配器发送结果
    }
}

// 注册表
@Component
public class ExporterRegistry {
    private final Map<String, Exporter> exporters = new ConcurrentHashMap<>();
    
    public ExporterRegistry(List<Exporter> exporterList) {
        exporterList.forEach(e -> exporters.put(e.type(), e));
    }
    
    public Exporter get(String type) {
        Exporter exporter = exporters.get(type);
        if (exporter == null) throw new IllegalArgumentException("Unknown exporter: " + type);
        return exporter;
    }
}
```

**设计要点**：
- `Exporter` 接口与 `ExportPayload` 解耦 — Exporter 不依赖 `TaskContext`
- Spring 自动注入所有 `Exporter` 实现到 `ExporterRegistry`
- 新增导出目标 = 实现 `Exporter` + Spring 注册，无需修改 ExportWorker
- ExportWorker 只负责调度和重试，不包含任何业务导出逻辑
- `Exporter.execute()` 运行在 ExportWorker 的独立线程池中，不阻塞 Actor 线程

---

### 4.7 onMessage 消息分发模式统一建议（技术审查新增）

生命周期设计文档 §4.4 的 `onMessage` 使用 `switch(msg)` 硬编码 10+ 种消息类型。建议改为**消息处理器注册表**模式，与改进计划 §2.2 的 LifecycleEngine 实现统一：

```java
// 替代 switch(msg) 的消息处理器注册表
private final Map<Class<?>, MessageHandler> handlers = new HashMap<>();

@FunctionalInterface
interface MessageHandler {
    void handle(InboundMessage msg, TaskContext ctx, LifecycleEngine engine);
}
```

**理由**：新增消息类型只需 `registerHandler(NewMsg.class, handler)`，不改 onMessage。switch 分支随消息类型持续膨胀将不可维护。

---

## 5. 文件变更预估

| 阶段 | 新建 | 修改 | 删除 |
|------|------|------|------|
| 0: 代码完善 | 4（ApiResponse, 2 migrations, 1 Service 方法） | 7（4 Controller + 3 Service） | 0 |
| 1: 设备接口 | 6（ITool, IArm, IArranger, ISetterSelector, IPreconditionCheckable, DeviceRegistry） | 2（ToolHandler, DeviceManager） | 0 |
| 1: Judgment | 4（JudgmentStrategy, JudgmentResult, AtlasJudgment, FitJudgment） | 0 | 0 |
| 2: Engine | ~25（LifecycleEngine, TaskContext, ContextCheckpoint, Capability, ~17 Capability 实现, 2 PersistentMonitor, Stage/SubState enum, BarcodeAcquisitionStrategy） | 0 | 0 |
| 3: Outbox | 8（ExportTask entity/DTO/mapper/service, ExportWorker, Exporter, ExporterRegistry, 具体 Exporter 实现） | 0 | 0 |
| 4: 适配 | 1（ToolAdapter） | 1（ToolHandler 1 行改动） | 0 |
| 5: 测试 | 8+ | 0 | 0 |
| **合计** | **~56** | **~9** | **0** |

---

## 6. 风险与缓解

| 风险 | 缓解 |
|------|------|
| Actor 线程中的 DB 写入阻塞 | SQLite WAL 模式下写入 µs 级，实测确认。如超预期则 Outbox 批量写入 |
| Netty I/O 线程到 Actor inbox 的消息堆积 | inbox 使用有界队列 + 背压；拧紧数据频率低（每秒 1-2 条），实际不会堆积 |
| 设备命令（sendPSet/sendLock）的 Future 回调在 Actor 线程外完成 | Adapter 将 CompletableFuture 回调结果转为 inbox 消息，回到 Actor 线程处理 |
| Actor 单线程成为瓶颈 | 当前为单工具串行场景，Actor 处理一条拧紧数据 < 1ms。未来多工具并行时每工具一个 Actor 实例 |
| 生命周期设计文档与实施细节的差异 | 以设计文档为准，实施中发现不合理的部分及时更新设计文档 |
| **Actor 线程崩溃导致 Context 完全丢失（技术审查新增）** | **关键转换点（子状态推进前、TaskRecord 创建后、StoreData 后）将 Context 摘要写入 TaskRecord.context_snapshot，Actor 启动时检测并尝试恢复。从阶段 2 开始内置 checkpoint 机制，不做后期补丁** |
| **TaskContext 字段膨胀失控（技术审查新增）** | **执行三层分类纪律：第一层（核心字段）需架构师审批新增；第二层（管道间传递）需对应 Capability 的 PR 评审确认；第三层（extras{}）自由度最高但不得被引擎核心代码读取。每半年 review Context 字段确认必要性** |

---

## 7. 附录：当前待完善项清单（非 bug，是未完成功能）

| 位置 | 现状 | 完善目标 |
|------|------|---------|
| `ToolHandler.handleTighteningData()` | 直接存储，无 Task 上下文 | 阶段 4 接入 LifecycleEngine |
| `TighteningDataService` | 空壳 | 阶段 0.1 封装查询方法 |
| `TaskRecordService` | 空壳 | 阶段 0.1 封装 createRecord/markAsOk |
| `CurveDataService` | 空壳 | 阶段 0.1 封装曲线匹配查询 |
| ProductTaskController 分页 | 字符串拼接 SQL | 阶段 0.2 改用 MyBatis-Plus Page |
| Controller 响应格式 | `String.valueOf(id)` / `"ok"` / `byte[]` | 阶段 0.3 统一 ApiResponse |
| `tightening_data` / `curve_data` 的 `task_record_id` | NOT NULL 但代码不填值 | 阶段 0.4 改为可空 |
| OK/NG 判定 | 无 | 阶段 1.3 JudgmentStrategy |
| Task 激活/完成状态 | 无 | 阶段 2 LifecycleEngine |
| 曲线数据与拧紧数据关联 | 独立存储 | 阶段 2 双槽位匹配 |
| 数据导出 | 无 | 阶段 3 Outbox Worker |
