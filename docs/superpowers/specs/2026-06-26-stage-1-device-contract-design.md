# Stage 1: 设备接口抽象 + JudgmentStrategy 设计

## 背景

Actor 生命周期引擎（stage 2）需要通过接口操作设备，不能直接依赖 `ToolHandler`（Netty 实现）。当前代码中，`ToolHandler` 承担三个职责混在一起：设备命令发送、数据回调处理、Netty 连接管理。Stage 1 将前两者抽象为 `ITool` 接口，为引擎提供干净的设备视图。

同时引入 `JudgmentStrategy` 判定接口，作为独立组件落地阶段 1，不依赖 Actor 引擎。

## 目标

1. 新建 `com.tightening.device.contract` 包，定义 `IDevice` / `ITool` 接口
2. 通过事件驱动 `DeviceRegistry` 管理 `ITool` 注册表，与 `DeviceManager` 零耦合
3. 新建 `ToolAdapter` 将 `ToolHandler` 包装为 `ITool`
4. 新建 `JudgmentStrategy` 接口 + Atlas/FIT 实现

## 设计

### 1. 设备接口

```java
// com.tightening.device.contract.IDevice
public interface IDevice {
    Long id();
    DeviceType type();
    boolean isConnected();
}

// com.tightening.device.contract.ITool
public interface ITool extends IDevice {
    CompletableFuture<Boolean> sendLock();
    CompletableFuture<Boolean> sendUnlock();
    CompletableFuture<Boolean> sendPSet(int psetId);

    void onTighteningData(Consumer<TighteningDataDTO> callback);
    void onCurveData(Consumer<CurveDataDTO> callback);
}
```

**命名理由：**
- 业务语义优于代码语义：`sendLock`/`sendUnlock` 对应底层 `enableToolOp`/`disableToolOp`，但对调用方（引擎）直接表达"锁定/解锁工具"
- `sendPSet` 保持原名，因为参数集切换本身就是业务概念
- `CancellationToken` 不在 stage 1 引入，接口签名不加无用参数

**不放 `IPreconditionCheckable`：** "前置条件检查"是 stage 2 Capability（`WorkstationConfigCheck`）的职责，设备层面的可用性用 `isConnected()` 已足够。

### 2. DeviceRegistry（事件驱动）

`DeviceRegistry` 监听 `DeviceManager` 已发布的 `DeviceChangeEvent`，自行维护 `Map<Long, ITool>`。

```
DeviceManager  ──DeviceChangeEvent(ADD)──→  DeviceRegistry.createToolAdapter()
               ──DeviceChangeEvent(DELETE)──→  DeviceRegistry.removeToolAdapter()
```

```java
@Component
public class DeviceRegistry {
    private final Map<Long, ITool> tools = new ConcurrentHashMap<>();
    private final DeviceHandlerFactory handlerFactory;

    public DeviceRegistry(DeviceHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    public ITool getTool(Long deviceId) { return tools.get(deviceId); }
    public List<ITool> getAllTools() { return List.copyOf(tools.values()); }

    @TransactionalEventListener
    void onDeviceChange(DeviceChangeEvent event) {
        switch (event.getEventType()) {
            case ADD    -> registerTool(event.getDevice());
            case UPDATE -> { tools.remove(event.getDeviceId()); registerTool(event.getDevice()); }
            case DELETE -> tools.remove(event.getDeviceId());
        }
    }

    private void registerTool(Device device) {
        DeviceHandler handler = handlerFactory.getHandler(DeviceType.getType(device.getType()));
        if (handler instanceof ToolHandler toolHandler) {
            ToolAdapter toolAdapter = new ToolAdapter(toolHandler, device);
            toolHandler.setToolAdapter(toolAdapter);  // 回路：ToolHandler 持有 ToolAdapter，stage 2 数据分叉用
            tools.put(device.getId(), toolAdapter);
        }
    }
}
```

**特点：** `DeviceManager` 零改动。`DeviceRegistry` 注入 `DeviceHandlerFactory`，通过 `instanceof ToolHandler` 过滤非工具设备（如未来可能的排列机/套筒选择器等）。非工具设备暂不在 stage 1 范围。

### 3. ToolAdapter

将 `ToolHandler` 适配为 `ITool`。

```java
public class ToolAdapter implements ITool {
    private final ToolHandler handler;
    private final Long deviceId;
    private final Device device;  // 提供 IDevice 方法所需数据
    private final List<Consumer<TighteningDataDTO>> tighteningDataListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<CurveDataDTO>> curveDataListeners = new CopyOnWriteArrayList<>();

    // === IDevice 实现（从 Device 实体取数据）===

    @Override public Long id() { return deviceId; }
    @Override public DeviceType type() { return DeviceType.getType(device.getType()); }
    @Override public boolean isConnected() {
        return handler.getStatus(deviceId) == DeviceStatus.CONNECTED;
    }

    // === ITool 命令发送（委托给 ToolHandler）===

    @Override
    public CompletableFuture<Boolean> sendLock() {
        return handler.enableToolOp(deviceId);
    }

    @Override
    public CompletableFuture<Boolean> sendUnlock() {
        return handler.disableToolOp(deviceId);
    }

    @Override
    public CompletableFuture<Boolean> sendPSet(int psetId) {
        return handler.sendPSetOp(deviceId, psetId);
    }

    // === 数据回调注册 ===

    @Override
    public void onTighteningData(Consumer<TighteningDataDTO> callback) {
        tighteningDataListeners.add(callback);
    }

    @Override
    public void onCurveData(Consumer<CurveDataDTO> callback) {
        curveDataListeners.add(callback);
    }

    // === 数据回调通知（由 ToolHandler 在 handleTighteningData 中触发）===

    public void fireTighteningData(TighteningDataDTO dto) {
        tighteningDataListeners.forEach(l -> l.accept(dto));
    }

    public void fireCurveData(CurveDataDTO dto) {
        curveDataListeners.forEach(l -> l.accept(dto));
    }
}
```

**数据回调桥接策略（通过 ToolHandler.setToolAdapter 回路）：**

- **Stage 1（当前）：** `ToolHandler.toolAdapter` 为 null，`handleTighteningData()` 中 fire 调用被跳过。`onTighteningData()` listener 列表为空。接口和 Consumer 列表仅定义就位。
- **Stage 2（引擎上线时）：** DeviceRegistry 在 `registerTool()` 中调用 `toolHandler.setToolAdapter(toolAdapter)` 建立回路。ToolHandler.handleTighteningData() 中取消注释 fire 调用，使数据分叉为两条路径：一条到引擎 inbox（通过 Consumer），一条到存储（现有逻辑）。两条路径并行不互斥。InBoundHandler 零改动。

### 4. JudgmentStrategy

```java
public interface JudgmentStrategy {
    JudgmentResult judge(TighteningDataDTO dto);
}

public record JudgmentResult(boolean isOk, String reason) {
    public static JudgmentResult ok() {
        return new JudgmentResult(true, "OK");
    }
    public static JudgmentResult ng(String reason) {
        return new JudgmentResult(false, reason);
    }
}
```

**AtlasJudgment：**
```
OK 条件：tighteningStatus == TighteningStatus.OK(1)
       && torqueStatus == AtlasTorqueStatus.OK(1)
       && angleStatus == AtlasAngleStatus.OK(1)
```
- torqueStatus: LOW(0)/OK(1)/HIGH(2) — 只用 OK 才 PASS
- angleStatus: LOW(0)/OK(1)/HIGH(2) — 同上
- 实现中使用枚举常量，不用裸数字

**FitJudgment：**
```
OK 条件：tighteningStatus == TighteningStatus.OK(1)
```
- FIT 设备仅通过 tighteningStatus 判定
- 实现中使用枚举常量，不用裸数字

**SudongJudgment（预留）：** `JudgmentStrategy` 接口已就位，后续新增 Sudong 设备时只需实现一个新策略类并注册到 `JudgmentConfig`，不修改任何现有代码。

**策略注册：**
```java
@Configuration
public class JudgmentConfig {
    @Bean
    public Map<DeviceType, JudgmentStrategy> judgmentStrategies() {
        return Map.of(
            DeviceType.ATLAS_PF4000, new AtlasJudgment(),
            DeviceType.ATLAS_PF6000_OP, new AtlasJudgment(),
            DeviceType.FIT_FTC6, new FitJudgment()
        );
    }
}
```

`JudgmentStrategy` 是无状态接口，实现类直接 new 即可，无需 Spring Bean 管理。

## 文件变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `device/contract/IDevice.java` | 设备基础接口 |
| 新建 | `device/contract/ITool.java` | 工具接口 |
| 新建 | `device/contract/ToolAdapter.java` | ToolHandler → ITool 适配器 |
| 新建 | `device/DeviceRegistry.java` | ITool 注册表 |
| 新建 | `judgment/JudgmentStrategy.java` | 判定策略接口 |
| 新建 | `judgment/JudgmentResult.java` | 判定结果 record |
| 新建 | `judgment/AtlasJudgment.java` | Atlas 判定实现 |
| 新建 | `judgment/FitJudgment.java` | FIT 判定实现 |
| 新建 | `config/JudgmentConfig.java` | 策略注册表配置 |
| 修改 | `device/handler/ToolHandler.java` | 新增 toolAdapter 字段 + setter（约 3 行） |
| 无需改动 | `device/DeviceManager.java` | 事件驱动，零耦合 |

**预估：** 10 个新建文件，1 个修改文件（`ToolHandler.java` 加 3 行）。

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| `ToolAdapter` 数据回调桥接在 stage 1 无法验证 | 接口定义就位，桥接逻辑在 stage 2 首次使用时有集成测试覆盖 |
| `DeviceChangeEvent` 的 UPDATE 事件 | DeviceRegistry 显式处理 UPDATE（先 remove 再 register），不依赖 DeviceManager 内部行为 |
| FIT 设备 handler 构造有额外参数 `FitConfig` | `DeviceHandlerFactory.getHandler()` 已通过 Spring 注入自动处理不同构造签名，不额外处理 |
