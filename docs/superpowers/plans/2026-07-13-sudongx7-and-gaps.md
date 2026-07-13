# SudongX7 集成 + Bug 修复 + Gap 清理

基于 `docs/superpowers/specs/2026-07-13-sudongx7-design.md` 和 `docs/superpowers/specs/2026-07-13-gap-tracker.md`。

## Global Constraints

- Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.9, SQLite + Flyway
- 六质量标准：高可维护性、高可读性、高内聚、低耦合、高性能、轻量
- TDD：先写测试看到红灯，再写实现看到绿灯
- 遵循现有分层架构和编码约定
- 手术式修改，不触碰无关代码
- Flyway 版本号按序递增（当前最新 V1.0.13）

---

## P1: Bug 修复

### Task 1: Atlas lock/unlock 响应 Key 不匹配

**文件**: `AtlasPFSeriesHandler.java`, `AtlasPFSeriesInBoundHandler.java`
**Bug**: 发送侧用 `generateKey(ENABLE, deviceId)` → `toString()` 生成含中文的 key（如 `"0043 (使能信号):1"`），响应侧用 `generateKey(mid.getMid(), deviceId)` → `getMid()` 生成纯数字 key（如 `"43:1"`），永不匹配 → lock/unlock 超时 → channel 关闭。
**修复**: 发送侧 `generateKey(ENABLE, deviceId)` 改为 `generateKey(ENABLE.getMid(), deviceId)`，与响应侧一致。
**测试**: 验证 `generateKey` 输出一致性。检查 PSet/Heartbeat 不受影响。

### Task 2: ToolAdapter sendLock/sendUnlock 语义颠倒

**文件**: `device/ToolAdapter.java`
**Bug**: `sendLock()` 调用 `handler.unlock()`，`sendUnlock()` 调用 `handler.lock()`，ITool 接口名与实际操作相反。
**修复**: 交换方法体 — `sendLock()` 调用 `handler.lock()`，`sendUnlock()` 调用 `handler.unlock()`。
**测试**: 验证 ITool 方法名与 ToolHandler 方法调用一致。

---

## P2: 条码多段关键字匹配

### Task 3: Flyway 迁移 + Entity + BarcodeMatcher 多段支持

**设计**: 将 `bar_code_matching_rule` 表的 `key_start_position`、`key_end_position`、`key_char` 三列替换为一个 `segments TEXT` 列，存储 JSON 数组。

**segments JSON 格式**:
```json
[{"s":0,"e":3,"v":"ABC"},{"s":6,"e":9,"v":"123"}]
```
- `s`: start position (0-based, inclusive)
- `e`: end position (0-based, exclusive，与 Java substring 一致)
- `v`: expected value string
- 匹配逻辑：ALL segments must match (AND semantics)
- `segments` 为 NULL 或空数组 `[]` → 无位置约束，仅 expectedLength 生效

**子步骤**:

1. **Flyway V1.0.14**: `ALTER TABLE bar_code_matching_rule ADD COLUMN segments TEXT;`（不删旧列，向后兼容）
2. **Entity `BarCodeMatchingRule`**: 新增 `private String segments;` 字段，旧三字段保留 `@Deprecated`
3. **DTO `BarCodeMatchingRuleDTO`**: 新增 `private String segments;`
4. **`BarcodeMatcher`**: 新增 `matchesSegments(String segmentsJson, String code)` 方法 — 反序列化 `List<Segment>`，逐段验证。`matches()` 方法在旧三字段存在时仍可用，新增重载 `matchesBySegments()` 优先使用 `segments`
5. **`BarcodeValidationService` + `PartsBarCodeMatching`**: 优先用 `segments` 字段，fallback 到旧三字段

**Segment record 定义**:
```java
record Segment(int s, int e, String v) {}
```

**异常处理**: JSON 解析失败 → WARN 日志 + fallback 旧三字段匹配（不抛异常）

**测试**: 
- 单段匹配成功/失败
- 多段 AND 全部成功
- 多段部分失败
- segments=null 时 fallback 旧三字段
- segments="[]" 时无位置约束
- segments JSON 格式错误 → fallback

---

## P3: SudongX7 协议集成

按 `docs/superpowers/specs/2026-07-13-sudongx7-design.md` 第 4 节实现顺序执行。

### Task 4: Crc16Utils + SudongX7Constants + SudongX7Frame

**新文件**:
- `util/Crc16Utils.java` — Modbus CRC16（多项式 0xA001，初始值 0xFFFF），`compute(byte[] data)` 返回 int
- `constant/sudongx7/SudongX7Constants.java` — 命令字常量 + 帧头 `0x55 0xAA` + 帧尾 `0x0D 0x0A`
- `netty/protocol/codec/sudongx7/SudongX7Frame.java` — 帧模型（cmd, subCmd, data byte[]），静态工厂方法：`lock()`, `unlock()`, `sendPSet(int psetId)`, `sendHeartbeat()`, `isTighteningData(long cmd)`, `isPsetResponse(long cmd, byte subCmd)`, `isToolRunning(long cmd)`, `isError(long cmd)`

**测试**: Crc16Utils 用协议文档已知帧 CRC 值验证。SudongX7Frame 工厂方法 hex 验证。

### Task 5: SudongX7TighteningDataParser

**新文件**: `netty/protocol/util/sudongx7/SudongX7TighteningDataParser.java`

Parser 只做数据提取（hex→数值），不做判定。用设计文档 1.3 节的字段偏移表解析 37 字节命令+数据段。关键逻辑：
- 扭矩单位判断（offset 4-5: 0=kgf.cm/div100, 1=N.m/div1000）
- 旋转方向 → resultType（0=CW→TIGHTENING, 1=CCW→LOOSENING）
- tighteningStatus 原始值映射（01→OK, 04→CCW, other→NG）
- 不提供的字段留默认值（tighteningId=0, vin=null 等）

**测试**: 用设计文档示例帧 `55AA2781...` 解析，断言各个字段值。

### Task 6: SudongX7FrameDecoder + SudongX7FrameCodec

**新文件**:
- `netty/protocol/codec/sudongx7/SudongX7FrameDecoder.java` — `ByteToMessageDecoder`，逐字节扫描 + CRC16 校验。实现步骤：
  1. 扫描 `0x55 0xAA`
  2. 读数据包长度 N
  3. 等可读 ≥ N+2
  4. 校验帧尾 `0D0A`
  5. CRC16 校验
  6. 通过后传命令+数据段给下游
- `netty/protocol/codec/sudongx7/SudongX7FrameCodec.java` — `MessageToMessageCodec<ByteBuf, SudongX7Frame>`，encode 时追加 CRC + 帧头帧尾

**测试**: EmbeddedChannel 完整/截断/CRC错误/粘包场景。

### Task 7: SudongX7InitHandler + SudongX7InBoundHandler

**新文件**:
- `netty/protocol/handler/sudongx7/SudongX7InitHandler.java` — 连接后 forceLock（无订阅，无心跳）
- `netty/protocol/handler/sudongx7/SudongX7InBoundHandler.java` — `SimpleChannelInboundHandler<SudongX7Frame>`，switch 命令头分发：
  - `2781` → 拧紧数据 → Parser → `DeviceEvent.TighteningDataReceived`
  - `0582` → PSet 应答 → `addResultFuture(cmd + subCmd, ...)`
  - `0585` → Tool Running → `addResultFuture(cmd, ...)`
  - `05CF` → Error → `addResultFuture(cmd, ...)`

**测试**: Mock ToolHandler，验证命令分发，PSet 应答按 cmd+subCmd 匹配。

### Task 8: SudongSeriesHandler (abstract) + SudongX7Handler (concrete)

**新文件**:
- `device/handler/impl/SudongSeriesHandler.java` — extends `ToolHandler`，abstract：
  - `setupChannelInitializer()` 组装 Pipeline（FrameDecoder→FrameCodec→InitHandler→InBoundHandler）
  - `lockTool()`/`unlockTool()` 默认抛 UnsupportedOperationException
  - `sendPSetCmd()` abstract
  - `sendHeartbeat()` 返回 `completedFuture(false)`
  - `getSupportedTypes()` 返回空 Set
- `device/handler/impl/SudongX7Handler.java` — extends `SudongSeriesHandler`，`@Component`：
  - `getSupportedTypes()` → `EnumSet.of(SUDONG_X7)`
  - `lockTool()`/`unlockTool()` 双发策略（间隔 200ms 连发两次，直接 return true）
  - `sendPSetCmd()` → `SudongX7Frame.sendPSet()`

### Task 9: DeviceType 枚举 + MissionContext.currentPSet + SendPSet + ReceiveData + LifecycleEngine

**修改文件**:
- `constant/DeviceType.java` — 新增 `SUDONG_X7(4, "SudongX7")`
- `lifecycle/MissionContext.java` — 新增 `volatile Integer currentPSet` 字段
- `lifecycle/capability/SendPSet.java` — `execute()` 成功后 `ctx.setCurrentPSet(psetId)`
- `lifecycle/capability/ReceiveData.java` — `precondition()` 对 `SUDONG_X7` 返回 `false`（Skip）
- `lifecycle/LifecycleEngine.java` — `handleTighteningData()` 新增三字段回填：
  - vin ← `ctx.getProductCode()`
  - parameterSet ← `ctx.getCurrentPSet()`
  - timestamp ← `LocalDateTime.now().toString()`

### Task 10: SudongJudgment

**新文件**: `lifecycle/judgment/SudongJudgment.java` — 实现 `JudgmentStrategy`。
**逻辑**:
- 第一步：torqueStatus/angleStatus/rundownAngleStatus 分别 vs 上下限
- 第二步：综合判定 = tighteningStatus==OK ∧ torqueStatus==OK ∧ angleStatus==OK
- rundownAngleStatus 不参与综合判定

**测试**: 组合各种状态验证综合判定结果。

---

## P4: 关键 Gap 清理

### Task 11: FIT barcode 写入 DTO + 死字段清理 + ObjectMapper 共享

**修改**:
1. `FitTighteningDataParser.java` — barcode 解码后 `dto.setVin(barcode)`（一行 setter）
2. `MissionContext.java` — `previousOperationData`/`pendingCurveData`/`extras` 标记 `@Deprecated`，加注释说明当前无消费者
3. `JsonUtils.java` + `Converter.java` — 提取共享 `ObjectMapper` 常量到 `JsonUtils`，`Converter` 引用之
