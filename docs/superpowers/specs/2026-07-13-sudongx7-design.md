# SudongX7 设备协议集成设计

## 1. 协议分析

### 1.1 帧格式

```
| 帧头(2B) | 数据包长度(1B) | 命令(2B) | 数据(N-5)B | CRC16(2B, LE) | 帧尾(2B) |
| 55 AA    | N              | CMD      | DATA       | CRC           | 0D 0A    |
```

- **数据包长度 N** = 命令(2) + 数据长度 + CRC(2)，不含帧头帧尾
- **CRC16** = Modbus RTU（多项式 0xA001，初始值 0xFFFF），低字节在前
- **帧编码**：hex 字符串对应的原始字节（与 Atlas ASCII 不同，与 FIT 二进制不同）

### 1.2 命令体系

| 操作 | PC→控制器 | 控制器应答 |
|------|----------|-----------|
| Lock | `55AA 07 01 00 0002 00 CRC 0D0A` | 无显式应答 |
| Unlock | `55AA 07 01 00 0000 00 CRC 0D0A` | 无显式应答 |
| PSet | `55AA 07 02 05 {pset:2B, LE} CRC 0D0A` | `55AA 05 82 05 B9 76 0D0A` |
| Tool Running | （被动接收） | `55AA 05 85 00 7B 45 0D0A` |
| Error | （被动接收） | `55AA 05 CF FC 4C 64 0D0A` |

- Lock/Unlock 无显式应答 — 间隔 200ms 连发两次（双发策略），直接返回 true
- PSet 应答格式 `55AA 05 82 {subCmd:1B} CRC 0D0A` — `0582` 为通用应答命令，按 `cmd + subCmd` 匹配而非整串 hex

### 1.3 拧紧数据帧（命令头 0x2781）

示例帧（原始 43 字节）：`55AA2781003901E7030000B70D0B00470B000200000100174C048403E7030000100ED00200000163A20D0A`

FrameDecoder 剥离帧头(55AA) + CRC(63A2) + 帧尾(0D0A)后，Parser 收到 37 字节命令+数据段，偏移基准如下。

**原始帧偏移** → **Parser 偏移** 对照：

| 原始偏移 | Parser偏移 | 长度 | 字段 | 解析 | DTO 映射 |
|---------|-----------|------|------|------|---------|
| 0-2 | — | 2 | 帧头 | `55AA` | FrameDecoder 剥离 |
| 2-4 | 0-2 | 2 | 命令 | `2781` | 用于 switch 分发 |
| 4-6 | 2-4 | 2 | 站点号 | LE int16 | extraData |
| 6-7 | 4-5 | 1 | 扭矩单位 | 0=kgf.cm(div100), 1=N.m(div1000) | —（决定 divisor）|
| 7-9 | 5-7 | 2 | 扭矩 | LE int16 / divisor | `torque` |
| 9-11 | 7-9 | 2 | 转速 | LE int16 | extraData |
| 11-13 | 9-11 | 2 | 旋入角度 | LE int16 | `rundownAngle` |
| 13-15 | 11-13 | 2 | 拧紧角度 | LE int16 | `angle` |
| 15-17 | 13-15 | 2 | 运行时间 | LE int16 (ms) | extraData |
| 17-18 | 15-16 | 1 | 旋转方向 | 0=CW→TIGHTENING, 1=CCW→LOOSENING | `resultType` |
| 18-20 | 16-18 | 2 | 剩余数量 | LE int16 | extraData |
| 20-21 | 18-19 | 1 | 螺丝数量 | int8 | extraData |
| 21-22 | 19-20 | 1 | 拧紧状态 | 01=OK, 04=CCW, other=NG | `tighteningStatus` |
| 22-23 | 20-21 | 1 | 错误报告 | int8 | `tighteningErrorStatus` |
| 23-24 | 21-22 | 1 | 温度 | int8 (°C) | extraData |
| 24-26 | 22-24 | 2 | 扭矩上限 | LE int16 / divisor | `torqueMaxLimit` |
| 26-28 | 24-26 | 2 | 扭矩下限 | LE int16 / divisor | `torqueMinLimit` |
| 28-30 | 26-28 | 2 | 拧紧角度上限 | LE int16 | `angleMaxLimit` |
| 30-32 | 28-30 | 2 | 拧紧角度下限 | LE int16 | `angleMinLimit` |
| 32-34 | 30-32 | 2 | 旋入角度上限 | LE int16 | `rundownAngleMaxLimit` |
| 34-36 | 32-34 | 2 | 旋入角度下限 | LE int16 | `rundownAngleMinLimit` |
| 36-37 | 34-35 | 1 | 模式 | 0=扭矩模式 | extraData |
| 37-38 | 35-36 | 1 | 状态标志 | — | extraData |
| 38-39 | 36-37 | 1 | 序号 | int8 | extraData |
| 39-41 | — | 2 | CRC16 | Modbus | FrameDecoder 剥离+校验 |
| 41-43 | — | 2 | 帧尾 | `0D0A` | FrameDecoder 剥离 |

**状态计算（Parser 不做判定，由 SudongJudgment 负责）**:
- Parser 只做字段提取（hex→数值），包括 `tighteningStatus` 的原始值映射（01→OK, 04→CCW, other→NG）
- `torqueStatus`、`angleStatus`、`rundownAngleStatus` 的计算全部移到 `SudongJudgment`：实测值 vs [min, max] → OK / LOW / HIGH
- 理由：所有"拧紧质量判定"逻辑集中一处，Parser 保持纯数据提取

**单位处理**: Parser 提取原始扭矩 int 值，不做除以 divisor 的转换。divisor 和最终 double 值的计算交给 SudongJudgment。

**SudongX7 不提供的字段（留空/默认值，后续由 LifecycleEngine 回填）**:
- `tighteningId` — 留 0
- `timestamp` — LifecycleEngine 回填 `LocalDateTime.now()`
- `vin` — LifecycleEngine 从 `MissionContext.productCode` 回填
- `parameterSet` — LifecycleEngine 从 `MissionContext.currentPSet` 回填
- `cellId`, `channelId`, `controllerName` — 留空
- `parameterSetName` — 留空（SudongX7 协议不提供）
- `jobId`, `batchSize`, `batchCounter`, `batchStatus` — 留空
- `torqueFinalTarget`, `angleFinalTarget` — 留空
- 所有扩展数据（自攻螺钉/预置扭矩等）— 留空

### 1.4 与 Atlas / FIT 的差异总结

| 维度 | Atlas PF | FIT FTC6 | SudongX7 |
|------|----------|----------|----------|
| 编码 | ASCII 文本 | 二进制 LE | Hex 原始字节 |
| 帧边界 | ASCII 长度前缀 | `AA55...55AA` | `55AA...0D0A` |
| 校验 | 无 | 无 | CRC16 Modbus |
| 帧解码 | LengthFieldBasedFrameDecoder | 自定义 UnpackData | 自定义 FrameDecoder |
| 心跳 | 协议层自带 | IdleState + HeartbeatHandler | 无（不需要）|
| 连接初始化 | Connect → Subscribe → Lock | ForceLock | ForceLock |
| 曲线数据 | 支持 | 支持(分片重组) | 暂不实现 |
| barcode 回传 | 支持(协议自带) | 支持(协议自带) | **不支持，需回填** |

---

## 2. 集成设计

### 2.1 继承链

```
DeviceHandler (interface)
  └── TCPDeviceHandler (abstract)
        └── ToolHandler (abstract)
              ├── AtlasPFSeriesHandler       ← 已有
              ├── FitSeriesHandler            ← 已有
              └── SudongSeriesHandler         ← 新增 abstract
                    └── SudongX7Handler        ← 新增 concrete, @Component
```

父类 `SudongSeriesHandler` 放 Sudong 系列公共逻辑（帧编解码、CRC 校验、InBoundHandler 命令分发、InitHandler forceLock、数据解析器），子类 `SudongX7Handler` 放 X7 特有行为（Lock/Unlock 双发策略、Pipeline init 组装）。未来新增 Sudong 其他型号时直接继承父类。

**职责边界明细**：

| 放在父类 `SudongSeriesHandler` | 放在子类 `SudongX7Handler` |
|-----------------------------|--------------------------|
| `setupChannelInitializer()` 组装 Pipeline | 无（Pipeline 顺序由父类定义） |
| `SudongX7FrameDecoder` / `SudongX7FrameCodec` 注册 | 无 |
| `SudongX7Constants` 命令字/帧头帧尾常量 | 无 |
| `SudongX7Frame` 帧模型 + 工厂方法 | 无 |
| `SudongX7TighteningDataParser` 数据解析 | 无 |
| `SudongX7InBoundHandler` 命令分发（switch 命令头） | 无 |
| `SudongX7InitHandler` 连接后 forceLock | 无 |
| `getSupportedTypes()` 返回空 Set（子类覆盖） | `getSupportedTypes()` 返回 `EnumSet.of(SUDONG_X7)` |
| `lockTool()` / `unlockTool()` 默认实现（抛 UnsupportedOperationException） | `lockTool()` / `unlockTool()` 双发策略 |
| `sendPSetCmd()` 抽象方法 | `sendPSetCmd()` 调用 `SudongX7Frame.sendPSet()` |
| `sendHeartbeat()` 返回 `completedFuture(false)` | 无 |

### 2.2 Netty Pipeline

```
SudongX7FrameDecoder      ← ByteToMessageDecoder（帧边界 + CRC 校验）
  → SudongX7FrameCodec    ← MessageToMessageCodec<ByteBuf, SudongX7Frame>
    → SudongX7InitHandler ← DeviceInitHandler（连接后 forceLock）
      → SudongX7InBoundHandler ← SimpleChannelInboundHandler<SudongX7Frame>
```

**不需要的 handler**:
- `IdleStateHandler` + `HeartbeatHandler` — SudongX7 无心跳机制
- `FitCurveDataReassembler` — 曲线数据暂不实现

### 2.3 SudongX7FrameDecoder（核心组件）

逐字节扫描策略：

```
1. 在 ByteBuf 中扫描 0x55 0xAA
2. 读到帧头后，读取下一个字节 = 数据包长度 N
3. 等待可读字节 ≥ N + 2（帧尾）
4. 读取 N 字节载荷 + 2 字节帧尾
5. 校验帧尾 = 0x0D 0x0A，不匹配则丢弃并重置到帧头后一个字节继续扫描
6. 从载荷末尾 2 字节提取 CRC16，对载荷前 N-2 字节计算 CRC，不匹配则丢弃+WARN 日志
7. 校验通过：将命令+数据部分（不含帧头、长度字节、CRC、帧尾）封装为 ByteBuf 传给下游
```

边界情况处理：
- 帧头后数据不足：等待下次 receive 累积
- CRC 校验失败：丢弃整帧，告警日志
- 连续扫描失败超过阈值：不做特殊处理（由 Netty 连接管理兜底）

### 2.4 Barcode / ParameterSet / Timestamp 回填

SudongX7 控制器不回传 barcode、parameterSet、timestamp。拧紧数据到达时，在 `LifecycleEngine.handleTighteningData()` 中统一回填：

```java
void handleTighteningData(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
    var event = (DeviceEvent.TighteningDataReceived) msg;
    TighteningData data = event.data();

    // 不回传 vin 的协议 → 从 MissionContext 回填产品条码
    if (data.getVin() == null || data.getVin().isEmpty()) {
        data.setVin(ctx.getProductCode());
    }
    // 不回传 parameterSet 的协议 → 从 MissionContext 回填
    if (data.getParameterSet() <= 0) {
        Integer pset = ctx.getCurrentPSet();
        if (pset != null) data.setParameterSet(pset);
    }
    // 不回传 timestamp 的协议 → 用系统时间
    if (data.getTimestamp() == null || data.getTimestamp().isEmpty()) {
        data.setTimestamp(java.time.LocalDateTime.now().toString());
    }

    ctx.setCurrentOperationData(data);
    // ...
}
```

其中 `MissionContext.currentPSet` 由 `SendPSet` Capability 在切换成功后写入：

```java
// SendPSet.execute()
tool.sendPSet(bolt.getParameterSetId().intValue())
    .whenComplete((ok, ex) -> {
        if (Boolean.TRUE.equals(ok)) {
            ctx.setCurrentPSet(bolt.getParameterSetId().intValue());
        }
    });
```

- **线程安全**：actor 线程内读写，无竞争
- **零侵入**：Atlas/FIT 自带完整数据不进入这些分支
- **通用兜底**：任何不回传这些字段的协议都适用

### 2.5 无需实现的特性

| 特性 | 原因 |
|------|------|
| 心跳 | 协议无心跳命令，`0585` 仅是被动通知 |
| 曲线数据 | C# 代码已注释，协议文档未描述，后续按需补充 |
| Barcode 下发到控制器 | 协议不支持，barcode 仅为系统侧概念 |
| 连接订阅 | 协议无订阅概念，连接后只需 forceLock |

### 2.6 Judgment 判定策略

`SudongJudgment`（新增，实现 `JudgmentStrategy`），集中所有 SudongX7 拧紧质量判定逻辑：

**第一步：单项状态计算**（原始值 vs 上下限）：
```
torqueStatus:   实测 torque vs [torqueMinLimit, torqueMaxLimit] → OK / LOW / HIGH
angleStatus:    实测 angle vs [angleMinLimit, angleMaxLimit] → OK / LOW / HIGH
rundownAngleStatus: 实测 rundownAngle vs [rundownAngleMinLimit, rundownAngleMaxLimit] → OK / LOW / HIGH
```

**第二步：综合判定**：
```
OK 条件: tighteningStatus == OK
         ∧ torqueStatus == OK
         ∧ angleStatus == OK
         （忽略 rundownAngleStatus — 旋入角度不作为质量判定依据）
```

Parser 不做任何判定，只提供原始值。与 Atlas/FIT 的差异：SudongX7 控制器返回的 `tighteningStatus` 是控制器自身判定，但扭矩和角度需独立验证，不可全信控制器。

### 2.7 ReceiveData 兼容处理

SudongX7 不回传 `tighteningId`（始终为 0）。`ReceiveData` Capability 当前逻辑：

```java
if (data.getTighteningId() <= 0) return CapabilityResult.Fail;
```

会误拦截 SudongX7 的所有拧紧数据。修改方案：

```java
// ReceiveData.precondition()
if (ctx.getCurrentDeviceType() == DeviceType.SUDONG_X7) return false; // Skip
```

或者更通用的方案 — 将 `Fail` 改为 `Skip`（非关键校验兜底）。采用前者（显式排除，避免误放过其他异常）。

---

## 3. 文件清单

### 3.1 新增文件

| 文件 | 包/路径 | 职责 |
|------|---------|------|
| `Crc16Utils.java` | `util` | Modbus CRC16 计算（多项式 0xA001） |
| `SudongX7Constants.java` | `constant.sudongx7` | 命令字、帧头帧尾、特殊 hex 常量 |
| `SudongX7Frame.java` | `netty.protocol.codec.sudongx7` | 帧模型 + 静态工厂方法 |
| `SudongX7FrameDecoder.java` | `netty.protocol.codec.sudongx7` | 帧边界检测 + CRC 校验 |
| `SudongX7FrameCodec.java` | `netty.protocol.codec.sudongx7` | ByteBuf ↔ SudongX7Frame 编解码 |
| `SudongX7InitHandler.java` | `netty.protocol.handler.sudongx7` | 连接后 forceLock |
| `SudongX7InBoundHandler.java` | `netty.protocol.handler.sudongx7` | 命令分发 + 数据委托 |
| `SudongX7TighteningDataParser.java` | `netty.protocol.util.sudongx7` | 拧紧数据 hex→DTO 解析 |
| `SudongSeriesHandler.java` | `device.handler.impl` | extends ToolHandler, abstract（公共逻辑）|
| `SudongX7Handler.java` | `device.handler.impl` | extends SudongSeriesHandler, @Component |

### 3.2 修改文件

| 文件 | 改动内容 |
|------|---------|
| `constant/DeviceType.java` | 新增 `SUDONG_X7(4, "SudongX7")` |
| `lifecycle/LifecycleEngine.java` | `handleTighteningData()` 新增 vin/parameterSet/timestamp 回填逻辑 |
| `lifecycle/capability/ReceiveData.java` | `precondition()` 新增 SudongX7 排除 |
| `lifecycle/capability/SendPSet.java` | `execute()` 新增 `ctx.setCurrentPSet()` |
| `lifecycle/MissionContext.java` | 新增 `currentPSet` volatile 字段 |
| `lifecycle/MissionOrchestrator.java` | 无需改动（回填在 Engine 内完成） |

---

## 4. 实现顺序

| 步骤 | 文件 | 验证方式 |
|------|------|---------|
| 1 | `Crc16Utils` | 用协议文档已知帧的 CRC 值做单元测试 |
| 2 | `SudongX7Constants` | 编译通过 |
| 3 | `SudongX7Frame` | 工厂方法生成帧，手动验证 hex |
| 4 | `SudongX7TighteningDataParser` | 用示例帧 `55AA2781...` 解析，断言各字段值 |
| 5 | `SudongX7FrameDecoder` | 构造完整/截断/CRC错误帧，验证行为 |
| 6 | `SudongX7FrameCodec` | encode→decode 往返测试 |
| 7 | `SudongX7InitHandler` | 集成测试 |
| 8 | `SudongX7InBoundHandler` | Mock ToolHandler，验证命令分发（PSet 应答按 cmd+subCmd 匹配）|
| 9 | `SudongSeriesHandler`（abstract）| 编译通过，抽象方法签名确认 |
| 10 | `SudongX7Handler`（concrete）| 集成测试，验证 Lock/Unlock 双发 + PSet 应答 |
| 11 | `DeviceType` 枚举 | 编译通过，getHandler 解析正确 |
| 12 | `MissionContext.currentPSet` | 编译通过 |
| 13 | `SendPSet` 写入 currentPSet | 单元测试 |
| 14 | `ReceiveData` precondition | 单元测试：SudongX7 时 Skip |
| 15 | `LifecycleEngine` 三字段回填 | 端到端测试：触发→拧紧数据→vin/pset/timestamp 已回填 |
| 16 | `SudongJudgment` | 单元测试：torque NG ∧ angle OK → 综合 NG |

---

## 5. 测试策略

### 5.1 单元测试

- `Crc16UtilsTest`：已知数据 → 预期 CRC 值（参考 Python/C# 产出）
- `SudongX7TighteningDataParserTest`：固定 hex 字符串 → 断言 DTO 各字段值（包括扭矩原始值、状态计算）
- `SudongX7FrameDecoderTest`：EmbeddedChannel 测试帧边界/CRC 失败/粘包场景
- `SudongX7FrameCodecTest`：EmbeddedChannel encode→decode 往返
- `SudongJudgmentTest`：组合 tighteningStatus/torqueStatus/angleStatus 验证综合判定

### 5.2 集成测试

- `SudongX7HandlerTest`：完整 pipeline 测试（需 EmbeddedChannel），验证 PSet 应答匹配、Lock/Unlock 双发时序
- `LifecycleEngineTest`：验证 vin/parameterSet/timestamp 三字段回填分支（mock device event）
- `ReceiveDataTest`：验证 SudongX7 时 precondition 返回 false

---

## 6. 风险与约束

| 风险 | 缓解 |
|------|------|
| 验证依赖真实控制器 | 使用协议文档示例帧 + C# 代码提取的已知数据做测试 |
| CRC 实现与 C# 不一致 | 参考 C# `MainUtils.Crc16ToBytes` 逐位对齐，单元测试交叉验证 |
| Lock/Unlock 无显式应答 | 双发策略（间隔 200ms 连发两次），直接返回 true |
| PSet 应答与其他 `0582` 应答混淆 | 按 `cmd + subCmd` 精确匹配（`0582` + `05`），不匹配整串 hex |
| 帧定界符 `0D0A` 可能出现在数据中 | 依靠数据包长度 N 精确控制帧边界，不纯依赖分隔符扫描 |
| `tighteningId=0` 被 `ReceiveData` 拦截 | `ReceiveData.precondition()` 对 SudongX7 返回 false（Skip）|
| 逐字节帧头扫描在高频场景退化 | 当前拧紧数据为低频（每秒 1 帧级别），无性能问题。未来若支持曲线数据（高频），考虑 `ByteBuf.indexOf()` 或 Boyer-Moore 跳表替代逐字节扫描 |
