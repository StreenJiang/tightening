# 技术债务清理 — 设计文档

> 设计日期: 2026-07-17
> 基于: `docs/superpowers/specs/2026-07-14-gap-tracker.md`
> 补充: 2026-07-17 会话设计决策

---

## 1. T2 — CurveData 匹配逻辑

### 1.1 现状

`ToolHandler.handleCurveData()` 已直接持久化曲线数据到 `curve_data` 表，但 `DeviceEvent.CurveDataReceived` 事件、`fireCurveData`/`onCurveData` listener 机制是死代码——无人注册监听器，无人投递事件到 inbox。

曲线数据的 `tighteningId` 来自协议帧：
- Atlas: MID 0900 Byte 21-30 "Result Data Identifier"
- FIT: CURVE 帧数据中携带

对拧紧数据的 `tighteningId`（MID 0061 Byte 220-231 "Tightening ID"），两个 ID 的对应关系尚无实机确认。

### 1.2 缓存匹配机制

在 `ToolHandler` 中维护 per-device 缓存：

```java
class DeviceTighteningCache {
    final Map<Integer, TighteningDataDTO> byId =
        new LinkedHashMap<>(16, 0.75f, true) {
            protected boolean removeEldestEntry(...) { return size() > 20; }
        };
    volatile TighteningDataDTO latest;  // 兜底：最新一条拧紧数据
}
```

`ToolHandler` 持有 `ConcurrentHashMap<Long, DeviceTighteningCache>`，key 为 deviceId。deviceId 从 `channel.attr(TCPDeviceHandler.DEVICE_ID).get()` 获取。

**`handleTighteningData`** — 写入缓存（原有逻辑基础上追加）：

```java
public void handleTighteningData(TighteningDataDTO dto, Channel channel) {
    // ... 现有逻辑不删 ...
    long deviceId = channel.attr(TCPDeviceHandler.DEVICE_ID).get();
    DeviceTighteningCache cache = cacheByDevice
        .computeIfAbsent(deviceId, k -> new DeviceTighteningCache());
    cache.byId.put(dto.getTighteningId(), dto);
    cache.latest = dto;
}
```

**`handleCurveData`** — 匹配 + 持久化：

```java
public void handleCurveData(CurveDataDTO dto, Channel channel) {
    CurveData data = Converter.dto2Entity(dto, CurveData::new);
    long deviceId = channel.attr(TCPDeviceHandler.DEVICE_ID).get();

    // 1. 按 tighteningId 匹配
    DeviceTighteningCache cache = cacheByDevice.get(deviceId);
    if (cache != null) {
        TighteningDataDTO matched = cache.byId.get(dto.getTighteningId());
        if (matched != null) {
            fillTaskContext(data, matched);
        } else if (cache.latest != null) {
            // 2. 未命中 → fallback 最新一条
            fillTaskContext(data, cache.latest);
        }
    }

    curveDataService.save(data);
}
```

`fillTaskContext` 从匹配的拧紧数据拷贝 `taskRecordId`、`boltSerialNum`、`workstationName`、`parameterSet` 等上下文字段到 CurveData。

LRU 上限 20 条，设备断线重连后旧缓存自然被挤出，无需主动清理。

### 1.3 删除死代码

- `DeviceEvent.CurveDataReceived` record — 删除
- `ITool.onCurveData()` + `ToolAdapter.onCurveData()` / `fireCurveData()` + `curveDataListeners` 字段 — 删除
- `ToolHandler.handleCurveData()` 中 `toolAdapter.fireCurveData(dto)` 调用 — 删除
- `CurveData` import — 删（`DeviceEvent.java` 中不再引用）

### 1.4 DeviceEvent sealed interface

删除 `CurveDataReceived` 后只剩两个 record，`sealed` 保留（如编译不通过则改为普通 interface）。

---

## 2. T3 — FIT forceLock 返回值忽略

### 2.1 现状

`FitSeriesInitHandler.afterChannelActive()` 调用 `forceLock(deviceId)` 但完全忽略返回的 `CompletableFuture<Boolean>`。

对比 Atlas：`AtlasPFSeriesInitHandler` 对 forceLock 失败至少做 `log.warn`。

### 2.2 修改

```java
@Override
protected void afterChannelActive(ChannelHandlerContext ctx) {
    long deviceId = ctx.channel().attr(DEVICE_ID).get();
    ((ToolHandler) deviceHandler).forceLock(deviceId)
        .thenAccept(result -> {
            if (result == null || !result) {
                log.warn("Force lock failed after connect, deviceId={}", deviceId);
            }
        });
}
```

---

## 3. T4 — BoltBarCodeCheck + BoltConfig

### 3.1 现状

Stub：`precondition=false`，`execute=Skip`。

### 3.2 业务逻辑

在 SWITCH_BOLT 阶段（SendPSet 之后，priority 3），检查当前 bolt 是否有关联的 `BoltPartsBarcode` 规则：

1. **有规则且 partsCode 未设置** → 添加 `LockReason.BARCODE_REQUIRED` → LockStateMonitor 锁工具
   → WorkplaceStatusService 推送 SSE（含 lockReasons） → 前端弹出扫码界面
2. **有规则且 partsCode 已匹配** → 移除锁 → 解锁 → 前进
3. **有规则且 partsCode 不匹配** → 保持锁（用户扫码按"不匹配 → 重新扫"循环）
4. **无规则** → Skip

用户扫码 → `POST /api/tasks/{id}/validate-parts-barcode` → `BarcodeValidationService` 校验
→ 通过后 Controller 移除活跃引擎 `TaskContext.lockReasons` 中 `BARCODE_REQUIRED`
→ LockStateMonitor 解锁 → 操作工拧紧

### 3.3 新增枚举值

```java
// LockReason.java
BARCODE_REQUIRED("barcodeRequired", "请录入物料码")
```

SSE 通知复用现有链路：`LockStateMonitor` → `WorkplaceStatusService` → SSE `WORKPLACE_STATUS` 事件携带 lockReasons。

### 3.4 BoltConfig 聚合类

新建运行时配置类，在 `LifecycleEngineFactory.createEngine()` 中组装，避免 Capability 直接注入 Service：

```java
@Value
@Builder
public class BoltConfig {
    ProductBolt definition;          // 持久化定义
    @Builder.Default Long barcodeRuleId = null;  // BoltPartsBarcode 关联的规则 ID
    // 后续扩展: arrangerConfig, bitSelectorConfig...
}
```

**组装（LifecycleEngineFactory.createEngine()）：**

```java
List<ProductBolt> bolts = boltService.listByTaskId(task.getId());
// 批量加载 BoltPartsBarcode
List<Long> boltIds = bolts.stream().map(ProductBolt::getId).toList();
Map<Long, Long> barcodeMap = partsBarcodeService.lambdaQuery()
    .in(BoltPartsBarcode::getProductBoltId, boltIds)
    .list().stream()
    .collect(toMap(BoltPartsBarcode::getProductBoltId, BoltPartsBarcode::getBarCodeMatchingRuleId));

List<BoltConfig> boltConfigs = bolts.stream()
    .map(b -> BoltConfig.builder()
        .definition(b)
        .barcodeRuleId(barcodeMap.getOrDefault(b.getId(), null))
        .build())
    .toList();
```

**TaskContext 改动：**

- `boltConfigs` 类型：`List<ProductBolt>` → `List<BoltConfig>`
- `currentBolt()` 返回类型：`ProductBolt` → `BoltConfig`
- 所有调用 `ctx.currentBolt().getParameterSetId()` 等处适配为 `ctx.currentBolt().getDefinition().getParameterSetId()`

### 3.5 BoltBarCodeCheck 实现

无需注入任何 Service，直接从 `BoltConfig` 读取：

```java
@Override
public boolean precondition(TaskContext ctx) {
    BoltConfig bc = ctx.currentBolt();
    return bc != null && bc.getBarcodeRuleId() != null;
}

@Override
public CapabilityResult execute(TaskContext ctx) {
    BoltConfig bc = ctx.currentBolt();
    if (ctx.getPartsCode() == null || ctx.getPartsCode().isEmpty()) {
        ctx.getLockReasons().add(LockReason.BARCODE_REQUIRED);
        return CapabilityResult.Pass;
    }
    // partsCode 已设置 → 校验在 API 层，这里只移除锁
    ctx.getLockReasons().remove(LockReason.BARCODE_REQUIRED);
    return CapabilityResult.Pass;
}
```

---

## 4. T5 — 导出实现

### 4.1 StandardExcelExporter

从 Stub 改为真实 Excel 导出：

- 使用 Apache POI 写 `.xlsx`（pom.xml 新增 `org.apache.poi:poi-ooxml` 依赖）
- 注入 `TighteningDataService`，按 `taskRecordId` 查询实际拧紧数据行
- 导出列：TighteningData entity 全部字段（列定义后续通过配置定制）
- 导出目录：`~/tightening_system/exports/`（首次导出时 `Files.createDirectories()` 自动创建）
- 文件名：`{taskRecordId}_{timestamp}.xlsx`

### 4.2 TxtExporter

新增 `TxtExporter implements Exporter`，type = `"txt"`：

- CSV 格式（逗号分隔，字段值加双引号转义）
- 后缀 `.txt`
- 列与 Excel 导出一致
- 同目录输出
- 也注入 `TighteningDataService` 查数据

### 4.3 默认导出目录

`~/tightening_system/exports/`，在首次导出时 `Files.createDirectories()`。

### 4.4 payload 设计

`ExportData` Capability 的 payload 保持轻量（元数据），不搬运拧紧数据。Exporter 注入 `TighteningDataService`，按 `taskRecordId` 自行查库。

---

## 5. T6 — checkpoint（记录，本次不做）

恢复所需改动量：
- `ContextCheckpoint` 扩展 5-6 个字段（boltStates、lockReasons、productCode、partsCode 等）
- `TaskOrchestrator.recover()` 新方法
- `LifecycleEngine` 支持从断点冷启动

预估 ~300 行，涉及 4-5 个文件。记录到 gap tracker 后续处理。

---

## 6. T7 — TODO 注释清理

### 6.1 i18n（6 处）

| 文件 | 行 | 处理 |
|------|-----|------|
| `DeviceInitHandler.java` | 28, 48, 58 | 删 `// TODO: need i18n`，日志已是英文 |
| `AtlasCommandType.java` | 15 | 同上 |
| `FitCommandType.java` | 15 | 同上 |
| `AtlasErrorCode.java` | 11 | 同上 |

### 6.2 FIT handler（4 处）

| 文件 | 行 | 处理 |
|------|-----|------|
| `FitSeriesInBoundHandler.java` | 49 | 拧紧数据已走 inbox → LifecycleEngine `handleTighteningData` 回填 vin/pset 等字段，taskRecordId 由 `StoreData` 写入，删 TODO |
| `FitSeriesInBoundHandler.java` | 51 | SSE 推送拧紧结果：LifecycleEngine 新增 `onTighteningJudged` 回调（与 onCompleted/onFaulted 同模式），`advancePipeline` 在 JUDGING 完成后触发。TaskOrchestrator.trigger() 中挂载 SSE 推送逻辑，SseService 不出 lifecycle 包 |
| `FitSeriesInBoundHandler.java` | 55 | 曲线数据在 ToolHandler 经 T2 缓存匹配后填 task record 字段，删 TODO |
| `FitSeriesInBoundHandler.java` | 57 | 同 51，共用 onTighteningJudged 回调 |

**onTighteningJudged 回调**：

```java
// LifecycleEngine — 与现有回调同模式
private Consumer<TighteningData> onTighteningJudged;
public void onTighteningJudged(Consumer<TighteningData> callback) { this.onTighteningJudged = callback; }

// advancePipeline — 在 JUDGING caps 完成后、过渡到 ADVANCING 前触发
if (onTighteningJudged != null && context.getJudgeResult() != null
        && context.getCurrentOperationData() != null) {
    onTighteningJudged.accept(context.getCurrentOperationData());
}

// TaskOrchestrator.trigger() — 挂载 SSE
engine.onTighteningJudged(data ->
    sseService.emit(new SseEvent(SseEventType.TIGHTENING_DATA, Converter.entity2Dto(data, TighteningDataDTO::new), now())));
```

### 6.3 TCPDeviceHandler（2 处）

| 文件 | 行 | 内容 |
|------|-----|------|
| `TCPDeviceHandler.java` | 101 | 已有代码（lines 102-105）关闭旧 channel，删 TODO 注释即可 |
| `TCPDeviceHandler.java` | 113-115 | 重连逻辑：`TCPDeviceConstants.RECONNECT_INTERVAL_MS` → `deviceConfig.getReconnectIntervalMs()`（G4），删 TODO/i18n 注释 |

---

## 7. G1 — TaskRecordDTO 补字段

```java
// TaskRecordDTO.java 新增
private String contextSnapshot;
private String faultMessage;
```

---

## 8. G2 — TaskRecord 加 partsCode

### 8.1 Entity

```java
// TaskRecord.java 新增
private String partsCode;
```

### 8.2 DTO

```java
// TaskRecordDTO.java 新增
private String partsCode;
```

### 8.3 Service

```java
public TaskRecord createRecord(Long productTaskId, String productCode,
                                   String partsCode, Integer isRework) {
    TaskRecord record = new TaskRecord()
        .setProductTaskId(productTaskId)
        .setProductCode(productCode)
        .setPartsCode(partsCode)
        .setIsRework(isRework)
        .setTaskResult(TaskResult.NG.getCode());
    save(record);
    return record;
}
```

### 8.4 调用方修改

**CreateTaskRecord.java**（顺手修 productCode 传 null 的 bug）：

```java
var record = taskRecordService.createRecord(
    ctx.getProductTaskId(), ctx.getProductCode(), ctx.getPartsCode(), 0);
```

**LifecycleEngine.startSkipScrewLifecycle()**：

```java
var record = taskRecordService.createRecord(
    ctx.getProductTaskId(), ctx.getProductCode(), ctx.getPartsCode(), 0);
```

### 8.5 数据库

新增 Flyway V1.0.15 迁移，`task_record` 表加 `parts_code TEXT` 列。

---

## 9. G3 — partsCode 绑定粒度

无需改动。当前全局 `partsCode` 满足业务需求。

---

## 10. G4 — 硬编码常量配置化

### 10.1 映射

| 常量 | → 配置类 | YAML 路径 | 默认值 |
|------|---------|-----------|--------|
| `ToolConstants.CMD_TIMEOUT_MS` | `ToolCommonConfig` | `tool-control.common.cmd-timeout-ms` | 5000 |
| `TCPDeviceConstants.RECONNECT_INTERVAL_MS` | `DeviceConfig` | `device-config.reconnect-interval-ms` | 3000 |
| `FitCurveDataReassembler.REASSEMBLY_TIMEOUT_MS` | `FitConfig` | `tool-control.fit.reassembly-timeout-ms` | 10000 |
| `HeartbeatHandler orTimeout(5, SECONDS)` | `FitConfig` | `tool-control.fit.heart-beat-timeout-ms` | 5000 |

### 10.2 config 类改动

```java
// ToolCommonConfig.java
private long cmdTimeoutMs;
```

```java
// FitConfig.java
private long reassemblyTimeoutMs;
private long heartBeatTimeoutMs;
```

```java
// DeviceConfig.java
private int reconnectIntervalMs;
```

### 10.3 YAML

```yaml
tool-control:
  common:
    lock_unlock_cooldown_ms: 5000
    cmd_timeout_ms: 5000
  fit:
    heart-beat-interval-ms: 30000
    heart-beat-retry-max: 3
    reassembly-timeout-ms: 10000
    heart-beat-timeout-ms: 5000

device-config:
  reconnect-interval-ms: 3000
```

### 10.4 引用替换

- `ToolConstants.CMD_TIMEOUT_MS` → `ToolCommonConfig.cmdTimeoutMs`（注入到 `TCPDeviceHandler.sendCmdAsync`）
- `TCPDeviceConstants.RECONNECT_INTERVAL_MS` → `DeviceConfig.reconnectIntervalMs`（注入到 `DeviceInitHandler`）
- `FitCurveDataReassembler` → 非 static 字段 `reassemblyTimeoutMs`，通过构造注入 `fitConfig`（`FitSeriesHandler` 已持有 `fitConfig`）
- `HeartbeatHandler` → 构造加 `long timeoutMs` 参数，`FitSeriesHandler` 传 `fitConfig.getHeartBeatTimeoutMs()`

### 10.5 删除文件

- `ToolConstants.java`
- `TCPDeviceConstants.java`

### 10.6 main + test yaml 同步

两个 `application.yaml` 文件同步添加新配置项段。

---

## 11. G8 — Flyway V1.0.5

跳过。Flyway 不校验版本号连续性，补空文件无意义。

---

## 12. N5 — lockMsgs 写入方

SendArrangerSignal、SendSetterSelector 排除（业务相关，待设备加入后再改造）。
BoltBarCodeCheck（T4）作为 BARCODE_REQUIRED 写入方。

---

## 13. N7 — SudongX7 测试

缺失 6 个测试类，参照现有 `FitCurveDataReassemblerTest` 的模式（JUnit 5 + AssertJ）：

| 测试类 |
|--------|
| `SudongX7FrameTest` |
| `SudongX7FrameCodecTest` |
| `SudongX7InitHandlerTest` |
| `SudongX7InBoundHandlerTest` |
| `SudongSeriesHandlerTest` |
| `SudongX7HandlerTest` |

---

## 汇总

| 编号 | 内容 | 方式 |
|------|------|------|
| T2 | CurveData 缓存匹配 + 删死代码 | ToolHandler 改造 |
| T3 | FIT forceLock 返回值 | .thenAccept 日志 |
| T4 | BoltBarCodeCheck + BoltConfig | 新 LockReason + 预加载 BoltConfig |
| T5 | Excel + txt 导出 | POI .xlsx + CSV .txt，注入 TighteningDataService 查库 |
| T6 | checkpoint 恢复 | 记录，不做 |
| T7 | TODO 清理 + SSE 推送 | 删注释 + engine onTighteningJudged 回调 |
| G1 | TaskRecordDTO 补字段 | +contextSnapshot +faultMessage |
| G2 | TaskRecord + partsCode | entity/dto/service/migration/调用方 |
| G3 | partsCode 粒度 | 无需改动 |
| G4 | 常量配置化 | 4 常量 → 3 config 类 |
| G8 | Flyway V1.0.5 | 跳过 |
| N5 | lockMsgs | T4 覆盖 |
| N7 | SudongX7 测试 | 6 单测 |

---

## 实现说明

### 与设计文档的差异

| 设计 | 实现 | 原因 |
|------|------|------|
| `BoltConfig` 聚合类替换 `List<ProductBolt>` | `TaskContext` 加 `Map<Long, Long> boltBarcodeRuleIds` | 避免 6+ 文件调用方适配 |
| `NotifyTighteningResult` Capability 做 SSE 推送 | `LifecycleEngine.onTighteningJudged` 回调 | 低耦合，SSE 不出 lifecycle 包 |
| SSE `BARCODE_REQUIRED` 事件类型 | 复用 `WorkplaceStatusService` 现成链路 | LockStateMonitor 已推送 lockReasons |
