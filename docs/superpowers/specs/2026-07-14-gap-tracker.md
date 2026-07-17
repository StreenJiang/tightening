# Gap Tracker（2026-07-14）

合并来源：旧 tracker 未解决项 + 2026-07-14 会话设计决策。

> **分类**：
> - **🐛 Bug** — 代码逻辑错误
> - **📋 TODO** — 功能未完成/Stub/TODO 注释
> - **⚠️ Gap** — 不一致、遗漏、死代码、硬编码
> - **🆕 New** — 本次会话产生的新待办

---

> **更新 2026-07-17**: T2-T7, G1-G4, G8, N5, N7 纳入 [技术债务清理设计文档](2026-07-17-tech-debt-cleanup-design.md) 和 [实现计划](../plans/2026-07-17-tech-debt-cleanup-plan.md)。T1/T4(部分)/T5(部分)/T8 排除。体检后发现 G5/G6 有漏网之鱼（`IoDeviceType`、`ReworkStatus`、`@FieldDescription`），已于同日补充删除。CLAUDE.md 及 [code-cleanup-design](2026-07-15-code-cleanup-design.md) 同步更新。

## 🐛 Bug

（无）

---

## 📋 TODO

### T1. Atlas SUBSCRIBE_DATA ACK 未处理 🔴 排除

**位置**: `AtlasPFSeriesInBoundHandler.java:88-89`

```java
case SUBSCRIBE_DATA:
    // TODO: 这里需要仔细再确认返回值内容，然后完善正确的逻辑
    break;   // ← 未调用 addResultFuture
```

`subscribeTighteningData()` 的 CompletableFuture 永远等不到 → 超时 → Init pipeline 失败 → 重连反复。

### T2. CurveDataReceived 消息无 Handler 🔧

**位置**: `DeviceEvent.java:15`, `LifecycleEngine.java:72-79`

`DeviceEvent.CurveDataReceived` 已定义，`registerDefaultHandlers()` 未注册 handler。曲线数据进 inbox 后静默丢弃。

### T3. FIT 初始化忽略 forceLock 结果 🔧

**位置**: `FitSeriesInitHandler.java:16-19`

与 Atlas init handler 不同——FIT 的 `forceLock()` 返回值完全忽略，无错误处理。

### T4. 三个 Capability 为 Stub 🔧

| Capability | 现状 |
|------------|------|
| `SendArrangerSignal` | `precondition=false`, `execute=Skip` — 🔴 排除 |
| `SendSetterSelector` | `precondition=false`, `execute=Skip` — 🔴 排除 |
| `BoltBarCodeCheck` | `precondition=false`, `execute=Skip` — 🔧 本次实现 |

### T5. 三个 Exporter 均为 Stub 🔧

| Exporter | 返回 |
|----------|------|
| `StandardExcelExporter` | 🔧 本次实现 |
| `OuterDatabaseStorer` | 🔴 排除 |
| `PlcResultSender` | 🔴 排除 |

### T6. MissionContext 预留字段 📋 记录

| 字段 | 状态 |
|------|------|
| `checkpoint` | 写入逻辑在 `saveCheckpoint()`，反序列化恢复未实现 |
| `lockMsgs` | `LockStateMonitor` 已读取，但无写入方（所有 Capability 绕过了它） |

### T7. 14 处 TODO 注释 🔧

| 位置 | 内容 |
|------|------|
| `TCPDeviceHandler.java:102` | channelFuture 重复处理策略 |
| `TCPDeviceHandler.java:114-116` | 重连逻辑完善 + i18n |
| `DeviceInitHandler.java:28,48,58` | 3 处 i18n |
| `AtlasPFSeriesInBoundHandler.java:88` | SUBSCRIBE_DATA 返回值确认 |
| `FitSeriesInBoundHandler.java:49,51,55,57` | mission record 补充 + SSE 推送 |
| `AtlasCommandType.java:15` | i18n |
| `AtlasErrorCode.java:11` | i18n |
| `FitCommandType.java:15` | i18n |

### T8. CheckInspection TriggerCapability 未实现 🔴 排除

**设计**: 点检任务运行时强制检查——今日无 inspection_record 则 Interrupt。

---

## ⚠️ Gap

### G1. MissionRecordDTO 缺少字段 🔧

**位置**: `dto/MissionRecordDTO.java`

Entity 有 `contextSnapshot` 和 `faultMessage`，DTO 没有。

### G2. MissionRecord 缺少 partsCode 字段 🔧

**位置**: `entity/MissionRecord.java`, `service/MissionRecordService.java`

`createRecord` 只接收 `productCode`，物料条码无法持久化。

### G3. partsCode 绑定粒度不完整 ✅

**位置**: `entity/BoltPartsBarcode.java`, `lifecycle/MissionContext.java`

`MissionContext` 只有一个全局 `partsCode`，但 `BoltPartsBarcode` 表已定义 per-bolt 关联。

### G4. 硬编码常量 🔧

| 常量 | 文件 | 值 |
|------|------|-----|
| `CMD_TIMEOUT_MS` | `ToolConstants.java:5` | 5000ms |
| `RECONNECT_INTERVAL_MS` | `TCPDeviceConstants.java:4` | 3000ms |
| `REASSEMBLY_TIMEOUT_MS` | `FitCurveDataReassembler.java:26` | 10000ms |
| HeartbeatFuture timeout | `HeartbeatHandler.java:174` | 5000ms |

### G5. 五个未使用的枚举 ✅

`IoDeviceType`、`ReworkStatus`、`InspectionScope`、`DeleteStatus`、`TCPCommand`

其中 `ReworkStatus`/`InspectionScope` 在实体中用 Integer 而非枚举，不一致。

**最终处理**: `DeleteStatus`、`TCPCommand` 于 e8a61c9 删除；`IoDeviceType`、`ReworkStatus` 于 2026-07-17 体检后删除；`InspectionScope` 保留（ProductMission/ProductMissionDTO 实际使用）。

### G6. 未使用的注解和配置 ✅

| 项目 | 文件 | 处理 |
|------|------|------|
| `@FieldDescription` 注解 | `annotation/FieldDescription.java` — 零引用 | 于 2026-07-17 删除 |
| `monitoring.*` 4 属性 | `application.yaml:84-88` — 无对应 `@ConfigurationProperties` | 已删除 |
| `spring.jpa.hibernate.ddl-auto` | `application.yaml:28` — MyBatis-Plus 项目，无 JPA | 已删除 |
| `tool-control.atlas:` 空段 | `application-dev.yml:16` — 无属性、无对应 Java 类 | 已删除 |
| `com.tightening.repository: DEBUG` | `application.yaml:81` — 包不存在，应为 `com.tightening.mapper` | 已修正 |

### G7. TCPDeviceHandler.close() 空实现

**位置**: `TCPDeviceHandler.java:289-291` — 实现 `Closeable` 但 body 为空。

### G8. Flyway V1.0.5 版本号缺失 ⏭️ 跳过

序列从 V1.0.4 跳到 V1.0.6。不影响 Flyway（宽松序列检查），但缺少不可逆。

---

## 🆕 New（2026-07-14 会话新增）

### N1. 删除 Self-loop 后端逻辑

Self-loop 改为纯前端配置，后端不做自动重启。

**代码清理**:
- `MissionOrchestrator.java`: 删除 `shouldSelfLoop`、`maxSelfLoops`、`loopCount`、`ctxShouldSelfLoop` 相关逻辑
- `LifecycleEngineFactory.java`: 删除 `shouldSelfLoop` 参数
- `MissionContext.java`: 删除 `shouldSelfLoop` 字段
- `InboundCommand.java`: 删除 `SelfLoop` record
- `LocalSettings.java`: 保留 `selfLoopEnabled`（作为 getSettings API 返回的配置）
- `LifecycleEngine.java`: 删除 `ctx.setShouldSelfLoop(false)` 调用

### N2. LockMessage.java 重建 ✅ 已完成

旧 `LockMessage`（含 `source`/`isManual()` 优先级逻辑）已删除，由 `LockReason` 枚举替代：PSET_SENDING(pSetSending, "程序号下发中")、ARRANGER_POSITIONING(arrangerPositioning, "送钉中")、SOCKET_SELECTING(socketSelecting, "套筒选择中")、ADMIN_CONFIRM(adminConfirm, "需管理员确认")。

### N3. WorkplaceStatus 实现 ✅ 已完成

`WorkplaceStatusService` + `WorkplaceStatus` 枚举 + SSE 推送已实现。4 状态平级，`transitionTo()` + `reset()`。`LifecycleEngine` 触发成功和 shutdown 时调用。

### N4. boltUnlockOverride ✅ 已完成

`MissionContext.boltUnlockOverride` 字段 + `LockStateMonitor` 跳过逻辑 + `AdvanceBolt` SWITCH_BOLT 重置已实现。

### N5. LockStateMonitor lockMsgs 写入方 🔧 部分完成

`SendPSet` 已改为写入 lockReasons（PSET_SENDING）。`SendArrangerSignal` 和 `SendSetterSelector` 仍为 stub（precondition=false），待激活后同步改造。

### N6. DeviceConnectionMonitor 升级 ✅ 已完成

已重写为系统级监控器（`@Component`，独立 `ScheduledExecutorService`），从 `LifecycleEngineFactory` monitors 列表移除。登录启动/登出停止 + SSE 推送状态变更。

### N7. SudongX7 测试缺失 🔧

上次提交只覆盖了 3 个类（Crc16Utils、FrameDecoder、Parser），缺失 6 个测试：

| 缺失 |
|------|
| `SudongX7FrameTest` |
| `SudongX7FrameCodecTest` |
| `SudongX7InitHandlerTest` |
| `SudongX7InBoundHandlerTest` |
| `SudongSeriesHandlerTest` |
| `SudongX7HandlerTest` |

### N8. SSE 推送框架搭建 ✅ 已完成

`SseService`（单 SseEmitter + 30s keepalive 心跳）+ `SseController`（GET /api/events）已实现。当前支持 WORKPLACE_STATUS 和 DEVICE_STATUS 事件类型，TIGHTENING_DATA 待后续接入。

---

## 汇总

> **更新 2026-07-17**: 技术债务清理已完成，9 项实现详见 [设计文档](2026-07-17-tech-debt-cleanup-design.md)。

| 分类 | 数量 | 关键项 |
|------|------|--------|
| 📋 TODO | 8 | T1 🔴 / T2 ✅ / T3 ✅ / T4 BoltBarCodeCheck ✅ + 2 🔴 / T5 StandardExcel+Txt ✅ + 2 🔴 / T6 📋 / T7 ✅ / T8 🔴 |
| ⚠️ Gap | 8 | G1 ✅ / G2 ✅ / G3 ✅ / G4 ✅ / G5 ✅ / G6 ✅ / G7 ✅ / G8 ⏭️ |
| 🆕 New | 8 | N1 ✅ / N2 ✅ / N3 ✅ / N4 ✅ / N5 ✅ / N6 ✅ / N7 ✅ / N8 ✅ |
| **已完成** | **16.5** | +本次 11 项（9 实现 + 1 记录 + 1 跳过） |
| **排除** | **5.5** | T1/T4×2/T5×2/T8（业务相关） + N5 部分（SendArranger/SendSetter） |
| **合计** | **24** | |
