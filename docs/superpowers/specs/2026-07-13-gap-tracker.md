# 代码遗落项追踪

全项目六标准审查（2026-07-13），覆盖设备层、协议层、生命周期层、数据层、API层、配置层。

> **分类说明**：
> - **🐛 Bug** — 代码逻辑错误，行为与预期不符，需修复
> - **📋 TODO** — 功能未完成/标记了 TODO/标了 [规划中]
> - **⚠️ Gap** — 不一致、遗漏、死代码、硬编码，非紧急但应清理

## 已修复（2026-07-13）

| 项 | 修复 |
|----|------|
| 🐛 1. Atlas lock/unlock Key 不匹配 | `generateKey(ENABLE.getMid(), deviceId)` / `generateKey(DISABLE.getMid(), deviceId)` |
| 🐛 2. ToolAdapter 语义颠倒 | `sendLock()` → `handler.lock()`, `sendUnlock()` → `handler.unlock()` |
| ⚠️ G5. ReceiveData tighteningId 校验 | 新增 `precondition()` 对 `SUDONG_X7` 返回 false |
| ⚠️ G6. TaskContext 缺 currentPSet | 新增 `volatile Integer currentPSet` 字段 + `SendPSet` 写入 |
| 📋 T3. FIT barcode 未写入 DTO | `tighteningData.setVin(barcode)` |
| ⚠️ G1. TaskContext 死字段 | `previousOperationData`/`pendingCurveData`/`extras` 标记 `@Deprecated` |
| ⚠️ G12. 重复 ObjectMapper | `Converter` 和 `BarcodeMatcher` 引用 `JsonUtils.OBJECT_MAPPER` |
| ⚠️ G4. partsCode 绑定粒度 | 条码匹配规则新增 `segments TEXT` 列（多段 JSON 匹配），Flyway V1.0.14 |
| 📋 T7. TaskContext 预留字段 | `currentPSet` 已补充；`checkpoint`/`lockMessages` 仍预留 |

---

## 🐛 Bug

### 1. Atlas lock/unlock 响应 Key 不匹配

**位置**: `AtlasPFSeriesHandler.java:86-106`, `AtlasPFSeriesInBoundHandler.java:80-101`

**代码**:

发送侧 (`AtlasPFSeriesHandler.java:86-89`):
```java
generateKey(AtlasCommandType.ENABLE, deviceId)
// toString() → "0043 (使能信号)" → key = "0043 (使能信号):1"
```

响应侧 (`AtlasPFSeriesInBoundHandler.java:85,97`):
```java
String key = deviceHandler.generateKey(mid.getMid(), deviceId);
// getMid() → 43 → key = "43:1"
```

**发送侧**用 `toString()`（含中文空格），**响应侧**用 `getMid()`（纯数字），Key 不匹配 → lock/unlock 永远超时 → channel 被关闭。

PSet 和 Heartbeat 两侧都用 `getMid()`，所以正常工作（纯巧合）。

**修复方向**: 发送侧改用 `getMid()`

### 2. ToolAdapter sendLock/sendUnlock 语义颠倒

**位置**: `ToolAdapter.java:46-53`

```java
@Override
public CompletableFuture<Boolean> sendLock() {
    return handler.unlock(device.getId());   // 实际执行解锁
}
@Override
public CompletableFuture<Boolean> sendUnlock() {
    return handler.lock(device.getId());     // 实际执行锁定
}
```

ITool 接口名与实际操作相反。调用方 `SendPSet`、`LockTools` 等 Capability 的行为与 ITool 文档预期相反。

**修复方向**: 交换方法体，或将 ITool 方法名重命名对齐 ToolHandler（`sendLock`→`sendUnlock`, `sendUnlock`→`sendLock`）

---

## 📋 TODO

### T1. Atlas SUBSCRIBE_DATA ACK 未处理

**位置**: `AtlasPFSeriesInBoundHandler.java:88-89`

```java
case SUBSCRIBE_DATA:
    // TODO: 这里需要仔细再确认返回值内容，然后完善正确的逻辑
    break;   // ← 未调用 addResultFuture
```

`subscribeTighteningData()` 的 CompletableFuture 永远等不到 → 超时 → Init pipeline 失败 → 重连（反复）。代码里标了 TODO，确认是未完成功能。

### T2. CurveDataReceived 消息无 Handler

**位置**: `lifecycle/message/DeviceEvent.java:15`, `LifecycleEngine.java:72-79`

`DeviceEvent.CurveDataReceived` 已定义，`registerDefaultHandlers()` 未注册 handler。曲线数据进 inbox 后静默丢弃。

### T3. FIT barcode 解析后未写入 DTO

**位置**: `FitTighteningDataParser.java:56-64`

barcode 被 GBK 解码、打日志，但不设到 `dto.setVin()`。DTO 已有 vin 字段。

### T4. FIT 初始化忽略 forceLock 结果

**位置**: `FitSeriesInitHandler.java:16-19`

与 Atlas init handler 不同 — FIT 的 `forceLock()` 返回值完全忽略，无错误处理。

### T5. 三个 Capability 为 Stub

| Capability | 现状 |
|------------|------|
| `SendArrangerSignal` | `precondition=false`, `execute=Skip` |
| `SendSetterSelector` | `precondition=false`, `execute=Skip` |
| `BoltBarCodeCheck` | `precondition=false`, `execute=Skip` |

均注册在 `OPERATION/SWITCH_BOLT`，目前只有 `SendPSet` 实际执行。

### T6. 三个 Exporter 均为 Stub

| Exporter | 返回 |
|----------|------|
| `StandardExcelExporter` | `ExportResult.ok("stub")` |
| `OuterDatabaseStorer` | `ExportResult.ok("stub")` |
| `PlcResultSender` | `ExportResult.ok("stub")` |

### T7. TaskContext 预留给未完成功能的字段

| 字段 | 用途 | 状态 |
|------|------|------|
| `checkpoint` | 崩溃恢复 | Flyway V1.0.12 已有 `context_snapshot` 列，写入逻辑在 `saveCheckpoint()`，反序列化恢复未实现 |
| `lockMessages` | LockStateMonitor | `LockStateMonitor` 已读取该集合，但无写入方，始终为空 |

### T8. 14 处 TODO 注释

| 位置 | 内容 |
|------|------|
| `TCPDeviceHandler.java:102` | channelFuture 重复处理策略 |
| `TCPDeviceHandler.java:114-116` | 重连逻辑完善 + i18n |
| `DeviceInitHandler.java:28,48,58` | 3 处 i18n |
| `AtlasPFSeriesInBoundHandler.java:88` | SUBSCRIBE_DATA 返回值确认 |
| `FitSeriesInBoundHandler.java:49,51,55,57` | task record 补充 + SSE 推送 |
| `AtlasCommandType.java:15` | i18n |
| `AtlasErrorCode.java:11` | i18n |
| `FitCommandType.java:15` | i18n |

### T9. CONTEXT.md 标记 [规划中] 但未实现

`WorkplaceStatus`、`Inspection / Challenge Task`、`Global Capability Pool`、`Attachment Point`、`DevicePreconditionMonitor`、`Trigger Signal`、`LifecycleResult`、`SudongJudgment`

---

## ⚠️ Gap

### G1. TaskContext 死字段

| 字段 | 问题 |
|------|------|
| `previousOperationData` | 仅 `StoreData` 写入（第 35 行），全项目无读取 |
| `pendingCurveData` | 声明的空 ArrayList，从不 add 或 read |
| `extras` | put 写入但无任何 Capability 按 key get |

### G2. TaskRecordDTO 缺少字段

**位置**: `dto/TaskRecordDTO.java` — 缺少 `contextSnapshot` 和 `faultMessage`（entity 有此二字段）

### G3. TaskRecord 缺少 partsCode 字段

**位置**: `entity/TaskRecord.java`, `service/TaskRecordService.java`

`createRecord` 只接收 `productCode`，物料条码无法持久化。

### G4. partsCode 绑定粒度不完整

**位置**: `entity/BoltPartsBarcode.java`, `lifecycle/TaskContext.java`

`TaskContext` 只有一个全局 `partsCode`，但 `BoltPartsBarcode` 表已定义 `productBoltId → barCodeMatchingRuleId` 关联。per-bolt 场景不支持。

### G5. ReceiveData 的 tighteningId 校验过于严格

**位置**: `lifecycle/capability/ReceiveData.java`

`data.getTighteningId() <= 0` → Fail。SudongX7 不回传 tighteningId → 所有数据被拦截。

### G6. TaskContext 缺少 currentPSet 字段

**位置**: `lifecycle/TaskContext.java`, `lifecycle/capability/SendPSet.java`

`SendPSet` 下发后不记录 PSet 编号，SudongX7 拧紧数据无法回填 `parameterSet`。

### G7. 硬编码常量

| 常量 | 文件 | 值 |
|------|------|-----|
| `CMD_TIMEOUT_MS` | `ToolConstants.java:5` | 5000ms |
| `RECONNECT_INTERVAL_MS` | `TCPDeviceConstants.java:4` | 3000ms |
| `REASSEMBLY_TIMEOUT_MS` | `FitCurveDataReassembler.java:26` | 10000ms |
| HeartbeatFuture timeout | `HeartbeatHandler.java:174` | 5000ms |

### G8. 五个未使用的枚举

`IoDeviceType`（对应规划的排列机/套筒选择器）、`ReworkStatus`、`InspectionScope`、`DeleteStatus`、`TCPCommand`

其中 `ReworkStatus`/`InspectionScope` 在实体中用 Integer 而非枚举，不一致。

### G9. 未使用的注解和配置

| 项目 | 文件 |
|------|------|
| `@FieldDescription` 注解 | `annotation/FieldDescription.java` — 零引用 |
| `monitoring.*` 4 属性 | `application.yaml:84-88` — 无对应 `@ConfigurationProperties` |
| `spring.jpa.hibernate.ddl-auto` | `application.yaml:28` — MyBatis-Plus 项目，无 JPA 依赖 |
| `tool-control.atlas:` 空段 | `application-dev.yml:16` — 无属性、无对应 Java 类 |
| `com.tightening.repository: DEBUG` | `application.yaml:81` — 包不存在，应为 `com.tightening.mapper` |

### G10. TCPDeviceHandler.close() 空实现

**位置**: `TCPDeviceHandler.java:289-291` — 实现 `Closeable` 但 body 为空，不清理 devices map、不关闭 channel。

### G11. Flyway V1.0.5 版本号缺失

序列从 V1.0.4 跳到 V1.0.6。Flyway 对严格顺序检查可能混淆。

### G12. Converter 和 JsonUtils 各自创建 ObjectMapper

**位置**: `util/JsonUtils.java:7`, `util/Converter.java:13` — 应共享单例。

---

## 汇总

| 分类 | 数量 | 关键项 |
|------|------|--------|
| 🐛 Bug | 2 | Atlas lock/unlock key 不匹配、ToolAdapter 语义颠倒 |
| 📋 TODO | 9 | Subscribe ACK、CurveData handler、FIT barcode、Stub Capability×3、Stub Exporter×3、Context 预留字段、14 TODO 注释、9 个 [规划中] 概念 |
| ⚠️ Gap | 12 | Context 死字段、DTO 缺字段、TaskRecord 缺 partsCode、partsCode 粒度、ReceiveData、currentPSet、硬编码常量、死枚举、未使用配置、close() 空、Flyway 版本号、重复 ObjectMapper |
| **合计** | **23** | |
