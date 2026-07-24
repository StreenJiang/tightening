# IO 网关设备适配：力臂 / 批头选择器 / 排列机

## 背景

C# 版 OperationGuidance 项目通过"安维能通信盒"（IO Box）管理三种辅助设备：力臂（读取坐标引导工具到位）、批头选择器（切换套筒）、排列机（送螺丝）。通信盒提供两种物理接口：力臂专用圆口 + IO 信号点。通信盒本身通过 TCP Socket 透传 Modbus RTU 帧（含 CRC16），非标准 Modbus TCP。

当前 Java 项目只有拧紧工具（Atlas PF / FIT FTC6 / 速动 X7），`DeviceHandler` 继承链和 `DeviceRegistry` 专为工具设计。`device/contract/` 包已定义了 `IDevice` → `ITool`，预留了 `IArm`/`IArranger` 等接口空间，但尚无通信盒及非工具设备的实现。

参考设计文档：`docs/superpowers/specs/2026-06-26-stage-1-device-contract-design.md`（Stage 1 设备接口抽象），`docs/superpowers/specs/2026-07-13-sudongx7-design.md`（速动 X7 协议适配模式）。

## 目标

1. 新增安维能通信盒 DeviceHandler（管理 TCP 连接 + Modbus RTU 帧收发）
2. 新增三种子设备接口：`IArm`、`ISetterSelector`、`IArranger`（放入 `device/contract/`）
3. 扩展 `DeviceRegistry` 注册非工具设备
4. 通信盒断连时级联标记子设备不可用
5. `SendArrangerSignal` Capability 从桩代码变为真实实现

## 架构概览

```
┌──────────────────────────────────────────────────────┐
│                    DeviceManager                      │
│  (仅管理通信盒的物理TCP连接，不管子设备)                   │
└────────────┬─────────────────────────────────────────┘
             │ DeviceChangeEvent
┌────────────▼─────────────────────────────────────────┐
│  AnengGatewayHandler  (implements DeviceHandler)  │
│  ├── Bootstrap + NioEventLoopGroup (TCP 连接管理)      │
│  ├── Modbus RTU 帧编解码 + CRC16 校验                  │
│  ├── 子设备轮询调度 (ScheduledExecutorService)          │
│  └── Map<slaveAddr, Consumer<String>> 响应分发         │
└────────────┬─────────────────────────────────────────┘
             │ 创建子设备 Adapter
┌────────────▼─────────────────────────────────────────┐
│                   DeviceRegistry                      │
│  ├── Map<Long, ITool>          tools                  │
│  ├── Map<Long, IArm>           arms        ← 新增     │
│  ├── Map<Long, ISetterSelector> setters     ← 新增     │
│  ├── Map<Long, IArranger>      arrangers   ← 新增     │
│  └── Map<Long, Long>           gatewayMap  ← 新增     │
│       (子设备ID → 通信盒ID，用于断连级联)                │
└──────────────────────────────────────────────────────┘
             │ 暴露契约接口
┌────────────▼─────────────────────────────────────────┐
│                 LifecycleEngine                       │
│   通过 DeviceRegistry.getArm(id) 等操作子设备           │
│   Capability: SendArrangerSignal, SendSetterSelector   │
└──────────────────────────────────────────────────────┘
```

## 设计

### 1. 设备类型扩展

```java
// constant/DeviceType.java — 新增枚举值
public enum DeviceType {
    // 现有工具
    ATLAS_PF4000(1, "PF4000"),
    ATLAS_PF6000_OP(2, "PF6000-OP"),
    FIT_FTC6(3, "FIT-FTC6"),
    SUDONG_X7(4, "SUDONG-X7"),

    // 新增：通信盒
    ANENG_GATEWAY(10, "安维能通信盒"),

    // 新增：子设备（通过通信盒间接连接）
    ARM(11, "力臂"),
    SETTER_SELECTOR(12, "批头选择器"),
    ARRANGER(13, "排列机"),
    ;
}
```

**ID 分段约定**：10-19 为网关及子设备段，1-9 为直属工具段，20+ 预留给未来设备类型。

`ARM`/`SETTER_SELECTOR`/`ARRANGER` 在 `DeviceHandlerFactory` 中不对应任何 handler — 它们不实现 `DeviceHandler`。通信盒 handler 只注册 `ANENG_GATEWAY` 的映射。

### 2. 通信盒子设备模型

```java
// device/type/SubDevice.java — 新建，所有子设备的公共基类
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class SubDevice extends Device {
    @JsonProperty("gateway_device_id")
    private Long gatewayDeviceId;             // 所属通信盒 device.id
}

// device/type/Arm.java — 新建
@Data
@EqualsAndHashCode(callSuper = true)
public class Arm extends SubDevice {
    private Integer armModelId;               // ArmModel 枚举 id
}

// device/type/SetterSelector.java — 新建
@Data
@EqualsAndHashCode(callSuper = true)
public class SetterSelector extends SubDevice {
    private Integer setterCount;              // 4 或 8
}

// device/type/Arranger.java — 修改基类（原 extends TCPDevice → extends SubDevice）
@Data
@EqualsAndHashCode(callSuper = true)
public class Arranger extends SubDevice {
    private Integer channelCount;             // 通道数，默认 8
    private Boolean reverseFirstFour;         // 前4位是否对换+反转
}
```

**力臂型号配置**：不做枚举，用 DB 表（新增型号 = INSERT，不需要发版）：

```sql
-- Flyway 迁移：V1.0.25__add_arm_model_config.sql
CREATE TABLE arm_model_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(20) NOT NULL,          -- CF01, CF02, ...
    x_slave_addr INTEGER NOT NULL,
    x_register INTEGER NOT NULL,
    x_count INTEGER NOT NULL DEFAULT 1,
    y_slave_addr INTEGER NOT NULL,
    y_register INTEGER NOT NULL,
    y_count INTEGER NOT NULL DEFAULT 1,
    z_slave_addr INTEGER,               -- nullable
    z_register INTEGER,
    z_count INTEGER DEFAULT 1,
    parse_strategy VARCHAR(20) NOT NULL DEFAULT 'STANDARD'  -- STANDARD / DIVIDE_BY_100
);

-- 预置数据
INSERT INTO arm_model_config (id, name, x_slave_addr, x_register, x_count,
    y_slave_addr, y_register, y_count, z_slave_addr, z_register, z_count, parse_strategy)
VALUES
  (1, 'CF01', 1, 0x0003, 2, 2, 0x0003, 2, NULL, NULL, 0, 'STANDARD'),
  (2, 'CF02', 1, 0x0000, 1, 2, 0x0000, 1, NULL, NULL, 0, 'STANDARD'),
  (3, 'CF03', 1, 0x0000, 1, 2, 0x0000, 1, 3,    0x0000, 1, 'STANDARD'),
  (4, 'CF04', 1, 0x0019, 1, 2, 0x0019, 1, NULL, NULL, 0, 'DIVIDE_BY_100');
```

`Device` 表加 `arm_model_id INTEGER` 列指向 `arm_model_config.id`。ArmAdapter 初始化时从 DB 读取对应行，解析逻辑根据 `parse_strategy` 和寄存器地址执行。

**DB 层面**：`device` 表已存在。
- Flyway 迁移新增 `gateway_device_id INTEGER` 列（可空，子设备填入，工具/通信盒留空）。
- `ANENG_GATEWAY` 行存储网关的 ip/port；`ARM`/`SETTER_SELECTOR`/`ARRANGER` 行 ip/port 留空，通过 `gateway_device_id` 指向通信盒。

**ProductBolt 字段扩展**：

```java
// entity/ProductBolt.java — 新增字段
public class ProductBolt extends BaseEntity {
    // 现有字段保持不变...

    // 新增：排列机关联
    private Long arrangerDeviceId;            // 排列机 device.id
    private String arrangerChannels;          // 逗号分隔通道号，如 "1,3,5"

    // 新增：套筒选择器关联
    private Long setterSelectorId;            // 批头选择器 device.id
    private Integer setterPosition;           // 目标套筒位号，1-based
}
```

Flyway 迁移新增对应列（可空，不配置设备的工位螺栓留空即可）。

### 3. 通信盒 Handler

```java
// device/handler/impl/AnengGatewayHandler.java
public class AnengGatewayHandler implements DeviceHandler {

    // === TCP 连接管理（组合 Bootstrap，不继承 TCPDeviceHandler） ===
    private final Bootstrap bootstrap;
    private final NioEventLoopGroup group;
    private final ConcurrentHashMap<Long, DeviceHolder> devices;

    // === Modbus RTU 通信 ===
    private final Map<String, CompletableFuture<String>> pendingCommands; // key = slaveAddr+seq
    private static final int RESPONSE_TIMEOUT_MS = 3000;

    // === 子设备管理 ===
    private final Map<Long, ArmAdapter> armAdapters;
    private final Map<Long, SetterSelectorAdapter> setterAdapters;
    private final Map<Long, ArrangerAdapter> arrangerAdapters;
    private ScheduledExecutorService pollScheduler;

    // === DeviceHandler 实现 ===
    @Override public void connect(long deviceId) { /* Bootstrap.connect */ }
    @Override public void disconnect(long deviceId) { /* channel.close + 清理 */ }
    @Override public DeviceStatus getStatus(long deviceId) { /* 查 devices map */ }
    @Override public Set<DeviceType> getSupportedTypes() {
        return Set.of(DeviceType.ANENG_GATEWAY);
    }

    // === Modbus RTU 帧方法 ===
    byte[] buildReadHoldingRegistersFrame(int slaveAddr, int startReg, int count);
    byte[] buildWriteSingleRegisterFrame(int slaveAddr, int regAddr, int value);
    String sendModbusCommand(long gatewayDeviceId, int slaveAddr, byte[] frame);

    // === 子设备注册（连接建立后由 DeviceRegistry 调用） ===
    void registerArm(long gatewayDeviceId, long armDeviceId, ArmAdapter adapter);
    void registerSetterSelector(...);
    void registerArranger(...);

    // === 内部轮询 ===
    private void startPolling(long gatewayDeviceId) {
        // 每个子设备的轮询任务独立提交
        // 力臂：定期 sendModbusCommand 读 X/Y/Z 寄存器
        // 排列机：定期读 IO 输入/输出字节
        // 批头选择器：定期读状态 + 如需则写位置
    }
}
```

**为什么不继承 TCPDeviceHandler**：
- `TCPDeviceHandler` 的 `sendCmdAsync` 模型是"发一帧→等一帧→CompletableFuture 完成"，但通信盒子设备的特征是持续轮询 + 并发的多个子设备读写 + 脉冲信号（写→延时→复位）
- 继承会迫使我们绕过或覆盖大部分方法，不如组合 Bootstrap/EventLoopGroup + 接口干净

### 4. 子设备契约接口

```java
// device/contract/IArm.java
public interface IArm extends IDevice {
    /** 异步获取当前三维坐标，返回 null 表示读取失败 */
    CompletableFuture<Coordinates3D> getCurrentCoordinates();
}

// device/contract/ISetterSelector.java
public interface ISetterSelector extends IDevice {
    CompletableFuture<Boolean> writePosition(int position);  // 1-based
    CompletableFuture<Boolean> reset();
    int getPositionCount();
}

// device/contract/IArranger.java
public interface IArranger extends IDevice {
    /** 向指定通道发送脉冲信号（channel 1-based，数组长度=通道数，非 null 位触发） */
    CompletableFuture<Boolean> sendPulse(int[] channels, int pulseWidthMs);
    CompletableFuture<int[]> getOutputStatus();  // 每通道出料状态
    CompletableFuture<int[]> getInputStatus();   // 每通道在料状态
    CompletableFuture<Boolean> reset();
}

// device/contract/Coordinates3D.java
public record Coordinates3D(int x, int y, int z) {}
```

**设计原则**：
- 异步返回 `CompletableFuture`，与 `ITool` 风格一致
- 接口只表达业务语义（`sendPulse`、`writePosition`），不暴露 Modbus/IO 细节
- `Coordinates3D` 用 record 做不可变值对象

### 5. 子设备 Adapter（内部类，在通信盒 Handler 包下）

Adaper 不独立暴露为 Spring Bean，由 `AnengGatewayHandler` 创建和管理：

```java
// device/handler/impl/aneng/ 包下
class ArmAdapter implements IArm {
    private final AnengGatewayHandler gateway;
    private final long gatewayDeviceId;
    private final int slaveAddr;
    private final String model;  // CF01~CF04，决定解析逻辑
    private final Device device;

    @Override
    public CompletableFuture<Coordinates3D> getCurrentCoordinates() {
        // 发送 Modbus RTU 读命令 → 解析 hex → Coordinates3D
    }
}

class SetterSelectorAdapter implements ISetterSelector { ... }
class ArrangerAdapter implements IArranger { ... }
```

### 6. DeviceRegistry 扩展

```java
// device/DeviceRegistry.java — 新增方法 + 新 Map
@Component
public class DeviceRegistry {
    // 现有
    private final Map<Long, ITool> tools = new ConcurrentHashMap<>();

    // 新增
    private final Map<Long, IArm> arms = new ConcurrentHashMap<>();
    private final Map<Long, ISetterSelector> setterSelectors = new ConcurrentHashMap<>();
    private final Map<Long, IArranger> arrangers = new ConcurrentHashMap<>();
    private final Map<Long, Long> gatewayMap = new ConcurrentHashMap<>(); // 子设备→通信盒

    // 新增查询方法
    public IArm getArm(Long deviceId) { return arms.get(deviceId); }
    public ISetterSelector getSetterSelector(Long deviceId) { ... }
    public IArranger getArranger(Long deviceId) { ... }

    @TransactionalEventListener
    void onDeviceChange(DeviceChangeEvent event) {
        Device device = event.getDevice();
        DeviceType type = DeviceType.getType(device.getType());

        switch (event.getEventType()) {
            case ADD -> {
                if (type == DeviceType.ANENG_GATEWAY) {
                    // 不注册到 tools，但需要查找该网关下的子设备并创建 Adapter
                    registerGatewaySubDevices(device);
                } else if (isSubDevice(type)) {
                    registerSubDevice(device, type);
                } else {
                    registerTool(device); // 现有逻辑
                }
            }
            case UPDATE -> { /* 先移除再重新注册 */ }
            case DELETE -> {
                if (type == DeviceType.ANENG_GATEWAY) {
                    cascadeRemoveSubDevices(device.getId());
                }
                tools.remove(event.getDeviceId());
                arms.remove(event.getDeviceId());
                setterSelectors.remove(event.getDeviceId());
                arrangers.remove(event.getDeviceId());
            }
        }
    }

    private boolean isSubDevice(DeviceType type) {
        return type == DeviceType.ARM
            || type == DeviceType.SETTER_SELECTOR
            || type == DeviceType.ARRANGER;
    }

    private void registerSubDevice(Device device, DeviceType type) {
        // 1. 通过 device.getGatewayDeviceId() 找到通信盒
        // 2. 从 DeviceManager 获取通信盒 handler
        // 3. 创建对应 Adapter 并注册到通信盒 handler
        // 4. 将 Adapter 放入对应的 Map
    }

    private void cascadeRemoveSubDevices(long gatewayDeviceId) {
        // 遍历 gatewayMap，移除所有 gatewayDeviceId==gatewayId 的子设备注册
        // 通知 DeviceManager 更新子设备的连接状态（标记为 DISCONNECTED）
    }

    // 当通信盒连接/断开时，由 AnengGatewayHandler 回调
    void onGatewayConnected(long gatewayDeviceId) {
        // 为该网关下所有子设备创建 Adapter 并开始轮询
    }

    void onGatewayDisconnected(long gatewayDeviceId) {
        // 标记子设备为 DISCONNECTED，但保留注册（不 remove）
        // 通信盒重连后自动恢复
    }
}
```

### 7. Lifecycle Capability 更新

```java
// lifecycle/capability/SendArrangerSignal.java — 从桩代码变为真实实现
public class SendArrangerSignal implements Capability {
    @Override public String id() { return "SendArrangerSignal"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 0; }

    @Override
    public boolean precondition(TaskContext ctx) {
        // 当前螺栓配置了排列机通道 → 才执行
        return ctx.getCurrentBoltConfig() != null
            && ctx.getCurrentBoltConfig().getArrangerChannels() != null;
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        IArranger arranger = ctx.getDeviceRegistry().getArranger(
            ctx.getCurrentBoltConfig().getArrangerDeviceId());
        if (arranger == null) return CapabilityResult.Skip;

        ctx.addLockReason(LockReason.ARRANGER_POSITIONING);
        try {
            CompletableFuture<Boolean> result = arranger.sendPulse(
                ctx.getCurrentBoltConfig().getArrangerChannels(), 200);
            // 同步等待结果（排列机信号是快速操作）
            try {
                return result.get(3, TimeUnit.SECONDS) ? CapabilityResult.Pass : CapabilityResult.Fail;
            } catch (Exception e) {
                return CapabilityResult.Fail;
            }
        } finally {
            ctx.removeLockReason(LockReason.ARRANGER_POSITIONING);
        }
    }
}

// lifecycle/capability/SendSetterSelector.java — 新建
public class SendSetterSelector implements Capability {
    @Override public String id() { return "SendSetterSelector"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 1; } // 在 SendArrangerSignal 之后

    @Override
    public boolean precondition(TaskContext ctx) {
        var config = ctx.getCurrentBoltConfig();
        return config != null && config.getSetterSelectorId() != null;
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        ISetterSelector setter = ctx.getDeviceRegistry().getSetterSelector(
            ctx.getCurrentBoltConfig().getSetterSelectorId());
        if (setter == null) return CapabilityResult.Skip;

        int position = ctx.getCurrentBoltConfig().getSetterPosition();
        ctx.addLockReason(LockReason.SOCKET_SELECTING);
        try {
            CompletableFuture<Boolean> result = setter.writePosition(position);
            try {
                return result.get(3, TimeUnit.SECONDS) ? CapabilityResult.Pass : CapabilityResult.Fail;
            } catch (Exception e) {
                return CapabilityResult.Fail;
            }
        } finally {
            ctx.removeLockReason(LockReason.SOCKET_SELECTING);
        }
    }
}
```

**与 LockStateMonitor 的协作**：

两个 Capability 在执行期间各自通过 `ctx.addLockReason` / `ctx.removeLockReason` 控制工具锁定：

```
SendArrangerSignal.execute():
  addLockReason(ARRANGER_POSITIONING)
    → LockStateMonitor（50ms 周期）检测到 lockReasons 非空 → 锁住工具
  sendPulse(channels, 200) + 等待
  removeLockReason(ARRANGER_POSITIONING)
    → LockStateMonitor 检测到 lockReasons 为空 → 解锁工具

SendSetterSelector: 同理，用 SOCKET_SELECTING
```

`LockReason.ARRANGER_POSITIONING` 和 `LockReason.SOCKET_SELECTING` 已在 `LockReason` 枚举中定义，`LockStateMonitor` 已就位。Capability 只负责增减自己的原因，不直接操作 lock/unlock。

## Modbus RTU 帧协议

通信盒与子设备间使用 Modbus RTU 帧，通过 TCP 透传。帧格式：

```
| slaveAddr(1B) | funcCode(1B) | data(N B) | crc16(2B LE) |
```

**常用命令**（以排列机为例，从站地址 0x09）：

```
读 IO 状态:   09 03 0000 0004 4541
写 IO 信号:   09 06 0000 XXXX CRC16
力臂读X坐标:  01 03 0003 0002 340B
力臂读Y坐标:  02 03 0003 0002 3438
```

项目已有 `Crc16Utils`（从速动 X7 协议引入），可直接复用。

## 配置结构

```yaml
# application-dev.yml — 新增配置块
tool-control:
  common:
    lock_unlock_cooldown_ms: 5000
    cmd_timeout_ms: 10000
  atlas:
    fit:
      heart-beat-interval-ms: 30000
      heart-beat-retry-max: 3
  # 新增
  gateway:
    poll-interval-ms: 100          # 子设备轮询间隔
    arm-read-timeout-ms: 3000      # 力臂读取超时
    setter-reset-retry-max: 10     # 批头选择器复位重试次数
    setter-retry-delay-ms: 100     # 批头选择器重试间隔
```

对应配置类：`config/GatewayConfig.java`

### 8. 通信盒设备状态模型

新增 `DeviceStatus.DEGRADED`：

```java
public enum DeviceStatus {
    CONNECTING,
    CONNECTED,     // TCP 通 + 所有子设备响应正常
    DEGRADED,      // TCP 通但部分子设备无响应 — 不触发 TCP 重连
    DISCONNECTED,
    NONE
}
```

**通信盒状态计算**（轮询循环内每次子设备健康检查后更新）：

```
所有子设备健康 → CONNECTED
部分子设备异常 → DEGRADED
全部子设备异常 → DEGRADED（TCP 还在，不降到 DISCONNECTED）
```

**子设备独立状态推送**：子设备在 DB 中有独立的 `device` 行和 DeviceType，各自的状态通过 SSE 独立推送给前端：

```java
// 通信盒轮询循环内，每个子设备健康检查后
boolean healthy = adapter.pollHealth();  // 发 Modbus 读命令，有响应=健康
if (healthy != adapter.isHealthy()) {
    adapter.setHealthy(healthy);
    sseService.pushDeviceStatus(adapter.getDeviceId(),
        healthy ? DeviceStatus.CONNECTED : DeviceStatus.DISCONNECTED);
}
```

前端可以根据 DeviceType 渲染不同图标（力臂/批头选择器/排列机各自有独立的状态指示）。

**DeviceManager 行为**：`scanAndConnect()` 中 `DEGRADED` ≠ `DISCONNECTED`，不触发 TCP 重连（TCP 层没问题）。

## 文件变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `device/handler/impl/AnengGatewayHandler.java` | 通信盒 DeviceHandler |
| 新建 | `device/handler/impl/aneng/ArmAdapter.java` | IArm 实现（内部类包） |
| 新建 | `device/handler/impl/aneng/SetterSelectorAdapter.java` | ISetterSelector 实现 |
| 新建 | `device/handler/impl/aneng/ArrangerAdapter.java` | IArranger 实现 |
| 新建 | `device/contract/IArm.java` | 力臂接口 |
| 新建 | `device/contract/ISetterSelector.java` | 批头选择器接口 |
| 新建 | `device/contract/IArranger.java` | 排列机接口 |
| 新建 | `device/contract/Coordinates3D.java` | 三维坐标 record |
| 新建 | `config/GatewayConfig.java` | 网关配置类 |
| 新建 | `lifecycle/capability/SendSetterSelector.java` | 批头切换 Capability |
| 新建 | `constant/GatewaySubDeviceType.java` | 子设备型号枚举（ArmModel/SetterModel） |
| 修改 | `constant/DeviceType.java` | 新增 4 个枚举值 |
| 修改 | `device/DeviceRegistry.java` | 新增 arms/setters/arrangers Map + 子设备注册逻辑 |
| 新建 | `device/type/SubDevice.java` | 子设备公共基类（extend Device） |
| 新建 | `device/type/Arm.java` | 力臂实体 |
| 新建 | `device/type/SetterSelector.java` | 批头选择器实体 |
| 修改 | `device/type/Arranger.java` | 基类改为 SubDevice，补充字段 |
| 新建 | `constant/ArmModelConfig.java` | 力臂型号配置实体 |
| 新建 | `resources/db/migration/V1.0.25__add_arm_model_config.sql` | arm_model_config 表 + 预置数据 |
| 修改 | `entity/ProductBolt.java` | 新增 arrangerDeviceId/arrangerChannels/setterSelectorId/setterPosition |
| 新建 | `resources/db/migration/V1.0.24__add_gateway_subdevice_columns.sql` | Flyway: gateway_device_id + ProductBolt 新列 |
| 修改 | `lifecycle/capability/SendArrangerSignal.java` | 桩代码 → 真实实现 |
| 修改 | `lifecycle/LifecycleEngineFactory.java` | 注册 SendSetterSelector Capability |
| 修改 | `device/handler/DeviceHandlerFactory.java` | 注册 ANENG_GATEWAY 映射 |
| 修改 | `constant/DeviceStatus.java` | 新增 DEGRADED 枚举值 |
| 修改 | `device/DeviceManager.java` | addDevice() 加子设备类型过滤；scanAndConnect 中 DEGRADED 不触发重连 |
| 无需改动 | `device/handler/ToolHandler.java` | 不受影响 |

**预估**：14 个新建文件，9 个修改文件。

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| 通信盒断连后子设备操作全部失败 | `DeviceRegistry.cascadeRemoveSubDevices` 标记状态；Capability 的 precondition 检查 isConnected，不连不执行；通信盒重连后自动恢复适配器 |
| Modbus RTU CRC16 与速动 X7 的 CRC16 算法差异 | `Crc16Utils` 已支持参数化多项式；先用现有实现，对不上再加参数 |
| 一个通信盒挂多个同种子设备时的寻址 | 力臂/批头选择器的从站地址（slaveAddr）从 Device 配置中读取，不硬编码 |
| 子设备 Adapter 生命周期与通信盒 Handler 耦合 | Adapter 由通信盒 Handler 持有和销毁，DeviceRegistry 只持有引用，不管理生命周期 |
| 现有 `Device` 表字段不够存子设备信息 | 利用 `Device` 表的 `extra_data` JSON 字段或 Flyway 新增 `gateway_device_id` 列；设计时先用 `extra_data` 避免 schema 迁移 |
| 力臂型号差异（CF01~CF04 解析逻辑不同） | `ArmAdapter` 内部根据 `armModel` 字段选择解析策略，策略枚举/switch 即可，不需要策略模式 |

### 9. DeviceManager 子设备过滤

`DeviceManager.addDevice()` 当前逻辑：

```java
DeviceHandler handler = DeviceType.getHandlerByTypeId(device.getType());
```

子设备类型（ARM/SETTER_SELECTOR/ARRANGER）不在 `DeviceHandlerFactory` 中注册，调用 `getHandlerByTypeId` 会抛 `IllegalArgumentException`。

**修复**：在 `addDevice()` 顶部加类型过滤，子设备直接跳过：

```java
private static final Set<DeviceType> SUB_DEVICE_TYPES = Set.of(
    DeviceType.ARM, DeviceType.SETTER_SELECTOR, DeviceType.ARRANGER);

private void addDevice(Device device) {
    if (device == null) return;
    DeviceType type = DeviceType.getType(device.getType());
    if (SUB_DEVICE_TYPES.contains(type)) return; // 子设备不在此管理

    DeviceHandler handler = DeviceType.getHandlerByTypeId(device.getType());
    if (handler instanceof TCPDeviceHandler tcpDeviceHandler) {
        tcpDeviceHandler.tryAddDeviceInfo(device);
    }
    deviceHandlers.put(device.getId(), handler);
}
```

同时 `removeDevice()` 和 `updateDevice()` 也加对应的 null 检查（`deviceHandlers.remove` 返回 null 即跳过，无需额外处理）。

## 交付分期

本设计文档覆盖 **一期**：安维能通信盒 + 三种子设备的接口定义和适配器实现。后续分期包括：
- 二期：力臂坐标引导 UI（坐标可视化）
- 三期：批头选择器 Plus（多套筒在位检测循环写入）
- 四期：新 IO 信号设备接入指南（开发者在现有框架下新增子设备类型的模式）
