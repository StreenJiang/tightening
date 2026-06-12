# ToolHandler 锁和状态迁移至 DeviceHolder

## 背景

`ToolHandler` 持有两个 `ReentrantLock`（`stateLock`、`pSetLock`）和三个状态字段（`isToolEnabled`、`lastEnableTime`、`lastDisableTime`），均为实例级别。但 `ToolHandler` 是 Spring 单例，通过父类 `TCPDeviceHandler.devices`（`Map<Long, DeviceHolder>`）管理多台设备。

实例级状态导致两个 bug：

1. **状态串扰**：设备 A 的 enable 会覆盖设备 B 的 `isToolEnabled`，使设备 B 的冷却检查读取到错误的状态
2. **不必要的串行化**：`pSetLock` 跨设备互斥，设备 A 的 PSET 操作阻塞设备 B 的 PSET 操作

## 目标

将 ToolHandler 的锁和状态字段按正确的作用域（per-device）迁移到 `DeviceHolder`，消除跨设备状态串扰和不必要的锁争用。

## 设计决策

### 1. 状态字段迁移至 DeviceHolder

将 5 个字段从 `ToolHandler` 移到 `DeviceHolder`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `stateLock` | `ReentrantLock` | 保护 enable/disable 状态和冷却检查 |
| `pSetLock` | `ReentrantLock` | 保护 PSET 操作 |
| `isToolEnabled` | `volatile boolean` | 当前 enable/disable 状态 |
| `lastEnableTime` | `volatile long` | 上次 enable 时间戳 |
| `lastDisableTime` | `volatile long` | 上次 disable 时间戳 |

**初始化位置**：`DeviceHolder` 构造函数中初始化锁和状态默认值。

`DeviceHolder` 原本是 `@Data`，迁移后改为 `@Getter`（类级） + `@Setter`（status, channel, isToolEnabled, lastEnableTime, lastDisableTime）。锁字段（stateLock, pSetLock）final 不可变，只有 getter。状态字段的写操作由 `ToolHandler` 在持锁保护下完成。

### 2. ToolHandler 改为通过 DeviceHolder 获取锁和状态

`changeToolState()` 和 `sendPSetOp()` 当前只接受 `deviceId`，改为先调用 `getHolder(deviceId)` 获取 `DeviceHolder`，再从 holder 上拿锁和状态。

关键改动点：

- `changeToolState()`：`stateLock.lock()` → `holder.getStateLock().lock()`，状态字段同理
- `sendPSetOp()`：`pSetLock.lock()` → `holder.getPsetLock().lock()`
- `pSetLock` 的锁范围仅包住了 `sendPSetCmd()` 调用，保持原有语义（发令串行化），不需要扩展到异步返回的 Future 生命周期

### 3. sendHeartbeat() 保持原状

`sendHeartbeat()` 是 `ToolHandler` 的默认实现（返回 `false`），没有用到锁和状态，不需要改动。子类 `FitSeriesHandler` 覆盖了它，心跳逻辑在 `HeartbeatHandler` 中处理，互不影响。

### 4. 不做额外抽象

五个字段直接放在 `DeviceHolder` 上，不引入新的包装类（如 `ToolState`）。DeviceHolder 目前只有 device、status、channel 三个字段，加五个字段还在合理范围内。未来如果 DeviceHolder 膨胀到职责不清再拆。

## 影响范围

| 文件 | 变更类型 |
|---|---|
| `device/DeviceHolder.java` | 添加 5 个字段 + getter |
| `device/handler/ToolHandler.java` | 删除 5 个字段；`changeToolState` 和 `sendPSetOp` 改为从 DeviceHolder 获取锁/状态 |

子类 `AtlasPFSeriesHandler`、`FitSeriesHandler` 不需要修改——它们只实现 `enableTool`/`disableTool`/`sendPSetCmd` 抽象方法，不直接操作锁或状态。

## 验证

1. `mvn test` 全部通过
2. 手动检查：`ToolHandler` 不再包含 `ReentrantLock`、`lastEnableTime`、`lastDisableTime`、`isToolEnabled` 字段
3. `grep` 确认所有对 `stateLock`、`pSetLock`、`lastEnableTime`、`lastDisableTime`、`isToolEnabled` 的引用都通过 `holder.` 路径
