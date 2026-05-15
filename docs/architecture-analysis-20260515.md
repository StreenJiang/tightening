# Tightening 项目架构分析

> 分析日期: 2026-05-15
> 分析范围: 全量源码 + CLAUDE.md

---

## P0 — 致命：ToolHandler 状态字段跨设备共享

ToolHandler 的子类各自是 Spring 单例（每个 DeviceType 一个实例），不同子类之间字段隔离（如 PF4000 和 FTC6 互不干扰）。但 `isToolEnabled`、`lastEnableTime`、`lastDisableTime` 是实例字段，同类型的多台设备共享同一个 handler 实例：

```
FitFTC6Handler (唯一实例)   ← 3 台 FTC6 设备共用
    isToolEnabled = false   ← 设备 A enable 后，设备 B/C 的查询也返回 true
    lastEnableTime = 0
    stateLock               ← 锁也共享，设备 A 操作会阻塞设备 B
```

后果：
- 查询设备 B 的 enable 状态 → 返回设备 A 的状态（错误）
- 设备 A enable 后，设备 B 的 enable 请求因冷却被拒绝（冷却检查看到 isToolEnabled 已经是 true）
- `Controller.getEnabled(id)` 传入 deviceId 但完全不使用，直接读共享字段

修复方向：状态字段应移到 DeviceHolder（每个 device 独立），或使用 Map<Long, ToolState>。

---

## P1 — 严重：每个 Handler 类型独立 NioEventLoopGroup

`TCPDeviceHandler` 构造函数中：

```java
group = new NioEventLoopGroup();  // 默认线程数 = CPU核数 × 2
```

3 个具体的 `@Component` handler（AtlasPF4000Handler、AtlasPF6000OPHandler、FitFTC6Handler）各自拥有独立 EventLoopGroup。在 N2840（2核4线程）上 = 3 × 8 = 24 个 I/O 线程，对低配机器是严重浪费。

---

## P2 — 重要：rspFutures key 无法区分 enable/disable（FIT 协议）

FIT 协议 `0x02` (ENABLE_DISABLE) 是 enable 和 disable 共用命令码，data 字段区分（`0x01`/`0x00`）。响应处理 `FitSeriesInBoundHandler` 中两者的 key 相同：

```java
// enable 和 disable 都生成 "0x02:deviceId"
generateKey(FitCommandType.ENABLE_DISABLE.getCode(), deviceId)
```

正常路径下 cooldown 保护不会并发，但 `forceEnableToolOp` / `forceDisableToolOp` 绕过冷却，极端场景下后发的 future 会覆盖先发的。

修复方向：key 中附加 data 标识（如 `0x02:1:01` vs `0x02:1:00`），或 force 操作也加入排队机制。

---

## P2 — 重要：LoginController 硬编码设备

```java
// LoginController.java
Device d1 = new Device();
d1.setId(1L);
d1.setType(DeviceType.FIT_FTC6.getId());
d1.setDetail("""{"ip":"172.17.10.10","port":5000}""");
```

IP、端口、设备类型全部硬编码。应该是从数据库 Device 表读取。

---

## P2 — 重要：缺少前端实现

CLAUDE.md 和 README 提到 SSE 实时推送、REST API，但代码中没有任何 SseController 或 SseEmitter。当前只有 `DeviceController`（enable/disable/pSet）和 `LoginController`。数据采集后没有实时推送通道。

同样，README 提到 PLC4X / Modbus / S7 / 串口，pom.xml 有依赖，但代码中没有对应的 Service 或连接管理实现。

---

## P2 — 重要：异常处理和日志问题

- `scanAndConnect()` catch 块完全空：异常被静默吞掉
- `getHolder()` 直接 `throw new RuntimeException()` 无消息
- `disconnect()` 中 `InterruptedException` 被包装成 `RuntimeException` 重新抛出（吞掉了中断状态）
- `FitDataUtils.java:327` log 中中英文混用（`"解析完成：tighteningId={}, 总点数={}"`）

---

## P3 — 次要：disconnect() 阻塞调用线程

`TCPDeviceHandler.disconnect()` 中 `channel.close().sync()` 会阻塞当前线程直到 Channel 完全关闭。该类的整体设计是异步的（`sendCmdAsync`、`CompletableFuture`），同步阻塞与异步架构不一致，如果调用方在事件循环线程中调用会死锁。

```java
// TCPDeviceHandler.java:121
channel.close().sync().addListener(...)
```

---

## P3 — 次要：SerialPortDevice 空壳

`src/main/java/com/tightening/device/type/SerialPortDevice.java` 存在但无任何实现。README 和 pom.xml 声称支持串口通信（jSerialComm），但实际代码为零。

---

## P3 — 次要：测试覆盖不足

5 个测试文件，其中 `TighteningApplicationTests` 只是 Spring Boot 默认生成的空壳。`ConverterTest`、`HeartbeatHandlerTest`、两个 FrameCodecTest 有一定价值，但核心业务逻辑（DeviceManager、ToolHandler、TCPDeviceHandler）完全没有测试。

---

## P3 — 次要：代码遗留问题（CLAUDE.md 已标注的 TODO）

CLAUDE.md 中 6 个 TODO 全部未处理：
1. 重连逻辑需完善
2. disconnect 资源清理缺失
3. Bootstrap 定义位置考虑上提
4. 重连无退避策略
5. 日志中英文混用
6. rspFutures 并发冲突

---

## 总结优先级

| 优先级 | 问题 | 影响 |
|--------|------|------|
| P0 | ToolHandler 状态跨设备共享 | 多设备场景下功能完全错误 |
| P1 | EventLoopGroup 过度分配 | N2840 低配机器线程爆炸 |
| P2 | rspFutures key 无法区分 enable/disable | force 操作下 future 可能被覆盖 |
| P2 | LoginController 硬编码 | 不可部署 |
| P2 | 无 SSE / 前端实现 | README 功能缺失 |
| P2 | 空异常处理 | 线上排障困难 |
| P3 | disconnect() 阻塞调用线程 | 异步架构中混入同步阻塞 |
| P3 | SerialPortDevice 空壳 | README 声称支持但无实现 |
| P3 | 测试覆盖不足 | 重构风险高 |
