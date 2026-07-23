# 技术债务清理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清理 gap tracker 中 11 项技术债务，含 CurveData 匹配、导出实现、BoltBarCodeCheck、TODO 清理、常量配置化等

**Architecture:** 9 个独立任务 + 2 个记录项。大部分无文件交叠，可并行。

**Tech Stack:** Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.9, Netty 4.1.129, Apache POI（新增）, Maven

## Global Constraints

- 修改后 `mvn compile` 必须通过
- 修改后 `mvn test` 必须通过
- 匹配现有代码风格（Lombok、4 空格缩进）
- 只删不再使用的 import

---

### Task 1: T3 — FIT forceLock 返回值忽略

**Files:**
- Modify: `src/main/java/com/tightening/netty/protocol/handler/fit/FitSeriesInitHandler.java`

- [ ] **Step 1: forceLock 返回值处理**

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

- [ ] **Step 2: 编译验证** — `mvn compile`

---

### Task 2: G1 — TaskRecordDTO 补字段

**Files:**
- Modify: `src/main/java/com/tightening/dto/TaskRecordDTO.java`

- [ ] **Step 1: 加字段**

```java
private String contextSnapshot;
private String faultMessage;
```

- [ ] **Step 2: 编译验证** — `mvn compile`

---

### Task 3: G4 — 硬编码常量配置化

**Files:**
- Modify: `src/main/java/com/tightening/config/ToolCommonConfig.java`
- Modify: `src/main/java/com/tightening/config/FitConfig.java`
- Modify: `src/main/java/com/tightening/config/DeviceConfig.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application.yaml`
- Modify: `src/main/resources/application-dev.yml`
- Modify: `src/main/java/com/tightening/device/handler/impl/TCPDeviceHandler.java`
- Modify: `src/main/java/com/tightening/netty/protocol/handler/DeviceInitHandler.java`
- Modify: `src/main/java/com/tightening/device/handler/HeartbeatHandler.java`
- Modify: `src/main/java/com/tightening/netty/protocol/codec/fit/FitCurveDataReassembler.java`
- Modify: `src/main/java/com/tightening/device/handler/impl/FitSeriesHandler.java`
- Delete: `src/main/java/com/tightening/constant/ToolConstants.java`
- Delete: `src/main/java/com/tightening/constant/TCPDeviceConstants.java`

- [ ] **Step 1: config 类加字段**

```java
// ToolCommonConfig.java — 加
private long cmdTimeoutMs = 5000;

// FitConfig.java — 加
private long reassemblyTimeoutMs = 10000;
private long heartBeatTimeoutMs = 5000;

// DeviceConfig.java — 加
private int reconnectIntervalMs = 3000;
```

- [ ] **Step 2: YAML 加配置**

main + test `application.yaml` 加 `com.tightening.mapper` 上面已有的，在 `mybatis-plus` 段下面追加：

```yaml
device-config:
  reconnect-interval-ms: 3000

tool-control:
  common:
    lock_unlock_cooldown_ms: 5000
    cmd_timeout_ms: 5000
  fit:
    heart-beat-interval-ms: 30000
    heart-beat-retry-max: 3
    reassembly-timeout-ms: 10000
    heart-beat-timeout-ms: 5000
```

test yaml 只加 device-config 和 tool-control 段（保持一致）。

`application-dev.yml` 在 `fit:` 下加两行：

```yaml
    reassembly-timeout-ms: 10000
    heart-beat-timeout-ms: 5000
```

- [ ] **Step 3: 替换引用 — TCPDeviceHandler**

删除 `ToolConstants` import，注入 `ToolCommonConfig`：

```java
// 字段
private final ToolCommonConfig toolCommonConfig;

// sendCmdAsync 中 CMD_TIMEOUT_MS → toolCommonConfig.getCmdTimeoutMs()
```

TCPDeviceHandler 已有 `ToolCommonConfig` 注入（部分子类构造传入），改造为直接接收 config。

- [ ] **Step 4: 替换引用 — DeviceInitHandler**

删除 `TCPDeviceConstants` import。注入 `DeviceConfig` 替代：

```java
// channelInactive 中
TCPDeviceConstants.RECONNECT_INTERVAL_MS → deviceConfig.getReconnectIntervalMs()
```

- [ ] **Step 5: 替换引用 — HeartbeatHandler**

构造加 `long timeoutMs` 参数：

```java
public HeartbeatHandler(int maxRetryCount, long timeoutMs,
                         Function<Long, CompletableFuture<Boolean>> heartbeatFunc) {
    this.timeoutMs = timeoutMs;
    // ...
}

// orTimeout(5, SECONDS) → orTimeout(timeoutMs, MILLISECONDS)
```

FitSeriesHandler 传参：

```java
new HeartbeatHandler(fitConfig.getHeartBeatRetryMax(),
    fitConfig.getHeartBeatTimeoutMs(), deviceId -> sendHeartbeat(deviceId))
```

- [ ] **Step 6: 替换引用 — FitCurveDataReassembler**

`REASSEMBLY_TIMEOUT_MS` static → instance field。构造加 `long reassemblyTimeoutMs` 参数：

```java
public FitCurveDataReassembler(long reassemblyTimeoutMs) {
    this.reassemblyTimeoutMs = reassemblyTimeoutMs;
}
```

FitSeriesHandler 传参：

```java
new FitCurveDataReassembler(fitConfig.getReassemblyTimeoutMs())
```

- [ ] **Step 7: 删除常量文件**

```bash
rm src/main/java/com/tightening/constant/ToolConstants.java
rm src/main/java/com/tightening/constant/TCPDeviceConstants.java
```

- [ ] **Step 8: main + test yaml 清理**

`application.yaml` main + test：确认没有 `spring.jpa.hibernate.ddl-auto` 和 `monitoring` 残留。test yaml 补 `default-enum-type-handler`（如上次未同步）。

- [ ] **Step 9: 编译 + 全量测试**

```bash
mvn compile && mvn test
```

预期: COMPILE SUCCESS, all tests PASS

---

### Task 4: T7 — TODO 注释清理 + SSE 回调

**Files:**
- Modify: `src/main/java/com/tightening/netty/protocol/handler/DeviceInitHandler.java`
- Modify: `src/main/java/com/tightening/constant/atlas/AtlasCommandType.java`
- Modify: `src/main/java/com/tightening/constant/fit/FitCommandType.java`
- Modify: `src/main/java/com/tightening/constant/atlas/AtlasErrorCode.java`
- Modify: `src/main/java/com/tightening/netty/protocol/handler/fit/FitSeriesInBoundHandler.java`
- Modify: `src/main/java/com/tightening/device/handler/impl/TCPDeviceHandler.java`
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngine.java`
- Modify: `src/main/java/com/tightening/lifecycle/TaskOrchestrator.java`
- Modify: `src/main/java/com/tightening/constant/SseEventType.java`

- [ ] **Step 1: 删 i18n TODO（6 处）**

`DeviceInitHandler.java:28/48/58`、`AtlasCommandType.java:15`、`FitCommandType.java:15`、`AtlasErrorCode.java:11` — 删除 `// TODO: need i18n` 注释行

- [ ] **Step 2: FIT handler TODO（4 处）**

`FitSeriesInBoundHandler.java:49/55` — 删除（task record 已在 LifecycleEngine/ToolHandler 中补充）
`FitSeriesInBoundHandler.java:51/57` — 删除（SSE 推送改为 engine callback，见 Step 3-4）

- [ ] **Step 3: LifecycleEngine 加 onTighteningJudged 回调**

```java
// 新字段（放 onFaulted 旁）
private Consumer<TighteningData> onTighteningJudged;

public void onTighteningJudged(Consumer<TighteningData> callback) {
    this.onTighteningJudged = callback;
}

// advancePipeline() — 在 JUDGING caps 完成后、过渡到 ADVANCING 前：
// （放在 stage/subState 过渡检测代码附近）
if (onTighteningJudged != null
        && context.getJudgeResult() != null
        && context.getCurrentOperationData() != null
        && context.getCurrentStage() == Stage.OPERATION
        && context.getCurrentSubState() == SubState.JUDGING) {
    onTighteningJudged.accept(context.getCurrentOperationData());
}
```

- [ ] **Step 4: TaskOrchestrator 挂载 SSE**

```java
// trigger() 方法内，onFaulted 之后
engine.onTighteningJudged(data -> {
    TighteningDataDTO dto = Converter.entity2Dto(data, TighteningDataDTO::new);
    sseService.emit(new SseEvent(SseEventType.TIGHTENING_DATA, dto, LocalDateTime.now()));
});
```

TaskOrchestrator 已有 `SseService` 字段（从构造函数注入）。

- [ ] **Step 5: SseEventType 加枚举值**

```java
TIGHTENING_DATA
```

- [ ] **Step 6: TCPDeviceHandler TODO**

`TCPDeviceHandler.java:101` — 删 TODO 注释（已有代码 lines 102-105 处理）
`TCPDeviceHandler.java:113-115` — 改为使用 `deviceConfig.getReconnectIntervalMs()`（Task 3 已覆盖），删 TODO/i18n 注释

- [ ] **Step 7: 编译 + 全量测试**

```bash
mvn compile && mvn test
```

---

### Task 5: T2 — CurveData 缓存匹配 + 删死代码

**Files:**
- Modify: `src/main/java/com/tightening/device/handler/ToolHandler.java`
- Modify: `src/main/java/com/tightening/lifecycle/message/DeviceEvent.java`
- Modify: `src/main/java/com/tightening/device/contract/ITool.java`
- Modify: `src/main/java/com/tightening/device/contract/ToolAdapter.java`

- [ ] **Step 1: DeviceTighteningCache 内部类**

在 `ToolHandler` 内新增：

```java
private static class DeviceTighteningCache {
    final Map<Integer, TighteningDataDTO> byId = new LinkedHashMap<>(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<Integer, TighteningDataDTO> eldest) {
            return size() > 20;
        }
    };
    volatile TighteningDataDTO latest;
}
```

字段：`private final Map<Long, DeviceTighteningCache> cacheByDevice = new ConcurrentHashMap<>();`

- [ ] **Step 2: handleTighteningData 写缓存**

在方法末尾追加：

```java
long deviceId = channel.attr(TCPDeviceHandler.DEVICE_ID).get();
DeviceTighteningCache cache = cacheByDevice.computeIfAbsent(deviceId, k -> new DeviceTighteningCache());
cache.byId.put(dto.getTighteningId(), dto);
cache.latest = dto;
```

- [ ] **Step 3: handleCurveData 匹配逻辑**

```java
public void handleCurveData(CurveDataDTO dto, Channel channel) {
    CurveData data = Converter.dto2Entity(dto, CurveData::new);

    long deviceId = channel.attr(TCPDeviceHandler.DEVICE_ID).get();
    DeviceTighteningCache cache = cacheByDevice.get(deviceId);
    if (cache != null) {
        TighteningDataDTO matched = cache.byId.get(dto.getTighteningId());
        if (matched == null) matched = cache.latest;
        if (matched != null) {
            data.setTaskRecordId(matched.getTaskRecordId());
            data.setBoltSerialNum(matched.getBoltSerialNum());
            data.setWorkstationName(matched.getWorkstationName());
            data.setParameterSet(matched.getParameterSet());
        }
    }

    curveDataService.save(data);
}
```

删除 `toolAdapter.fireCurveData(dto)` 调用。

- [ ] **Step 4: 删除死代码**

`DeviceEvent.java` — 删除 `CurveDataReceived` record、`CurveData` import。如 `sealed` 报编译错则改为普通 `interface`。

`ITool.java` — 删除 `void onCurveData(Consumer<CurveDataDTO> callback);`

`ToolAdapter.java` — 删除 `curveDataListeners` 字段、`onCurveData` 方法、`fireCurveData` 方法

- [ ] **Step 5: 编译 + 全量测试**

---

### Task 6: T4 — BoltBarCodeCheck + BoltConfig

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/BoltConfig.java`
- Modify: `src/main/java/com/tightening/lifecycle/TaskContext.java`
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java`
- Modify: `src/main/java/com/tightening/lifecycle/capability/BoltBarCodeCheck.java`
- Modify: `src/main/java/com/tightening/constant/LockReason.java`
- Modify: `src/main/java/com/tightening/controller/TaskLifecycleController.java`

**影响面（BoltConfig 类型变更导致）：** 所有引用 `ctx.currentBolt()`、`ctx.getBoltConfigs()`、`ctx.currentBolt().getParameterSetId()` 的地方需适配 `→ .getDefinition()`。

- [ ] **Step 1: 创建 BoltConfig.java**

```java
package com.tightening.lifecycle;

import com.tightening.entity.ProductBolt;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BoltConfig {
    ProductBolt definition;
    @Builder.Default Long barcodeRuleId = null;
}
```

- [ ] **Step 2: TaskContext 改类型**

`boltConfigs`: `List<ProductBolt>` → `List<BoltConfig>`
`currentBolt()` 返回: `BoltConfig`（通过 `boltConfigs.get(currentBoltIndex)` 拿到 `BoltConfig`，再 `.getDefinition()` 取 `ProductBolt`）

加便捷方法：

```java
public ProductBolt currentBoltDef() {
    BoltConfig bc = currentBolt();
    return bc != null ? bc.getDefinition() : null;
}
```

- [ ] **Step 3: BoltConfig 遍历适配**

所有 `ctx.getBoltConfigs()` 遍历 → `.getDefinition()` 取 `ProductBolt`
所有 `ctx.currentBolt().getXxx()` → `ctx.currentBolt().getDefinition().getXxx()`
或使用新便捷方法 `ctx.currentBoltDef().getXxx()`

涉及文件（grep 确认完整列表）：
- `LifecycleEngine.java` — `startNormalLifecycle` 中 bolt 遍历
- `PrepareBolts.java` — bolt 初始化
- `SendPSet.java` — `bolt.getParameterSetId()`
- `AdvanceBolt.java` — bolt 计数
- `WorkstationConfigCheck.java` — 如有引用

- [ ] **Step 4: LifecycleEngineFactory 组装 BoltConfig**

Factory 注入 `BoltPartsBarcodeService`：

```java
private final BoltPartsBarcodeService partsBarcodeService;

List<ProductBolt> bolts = boltService.listByTaskId(task.getId());
List<Long> boltIds = bolts.stream().map(ProductBolt::getId).toList();
Map<Long, Long> barcodeMap = partsBarcodeService.lambdaQuery()
    .in(BoltPartsBarcode::getProductBoltId, boltIds)
    .list().stream()
    .collect(Collectors.toMap(
        BoltPartsBarcode::getProductBoltId,
        BoltPartsBarcode::getBarCodeMatchingRuleId));

List<BoltConfig> boltConfigs = bolts.stream()
    .map(b -> BoltConfig.builder()
        .definition(b)
        .barcodeRuleId(barcodeMap.getOrDefault(b.getId(), null))
        .build())
    .toList();
```

- [ ] **Step 5: LockReason 加 BARCODE_REQUIRED**

```java
BARCODE_REQUIRED("barcodeRequired", "请录入物料码");
```

- [ ] **Step 6: BoltBarCodeCheck 实现**

```java
@Override
public boolean precondition(TaskContext ctx) {
    BoltConfig bc = ctx.currentBolt();
    return bc != null && bc.getBarcodeRuleId() != null;
}

@Override
public CapabilityResult execute(TaskContext ctx) {
    if (ctx.getPartsCode() == null || ctx.getPartsCode().isEmpty()) {
        ctx.getLockReasons().add(LockReason.BARCODE_REQUIRED);
        return CapabilityResult.Pass;
    }
    ctx.getLockReasons().remove(LockReason.BARCODE_REQUIRED);
    return CapabilityResult.Pass;
}
```

- [ ] **Step 7: API 校验通过后移除锁**

`TaskLifecycleController.validatePartsBarcode` — 校验通过后：

```java
engine.ifPresent(e -> e.getContext().getLockReasons().remove(LockReason.BARCODE_REQUIRED));
```

- [ ] **Step 8: 编译 + 全量测试**

---

### Task 7: G2 — TaskRecord 加 partsCode

**Files:**
- Modify: `src/main/java/com/tightening/entity/TaskRecord.java`
- Modify: `src/main/java/com/tightening/dto/TaskRecordDTO.java`
- Modify: `src/main/java/com/tightening/service/TaskRecordService.java`
- Modify: `src/main/java/com/tightening/lifecycle/capability/CreateTaskRecord.java`
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngine.java`
- Create: `src/main/resources/db/migration/V1.0.15__add_parts_code_to_task_record.sql`

- [ ] **Step 1: Entity**

```java
// TaskRecord.java
private String partsCode;
```

- [ ] **Step 2: DTO**

```java
// TaskRecordDTO.java
private String partsCode;
```

- [ ] **Step 3: Service**

`createRecord` 签名加 `String partsCode` 参数，`setPartsCode(partsCode)`。

- [ ] **Step 4: 调用方**

`CreateTaskRecord.java:23-24` — 改为 `createRecord(ctx.getProductTaskId(), ctx.getProductCode(), ctx.getPartsCode(), 0)`（同时修 productCode 传 null 的 bug）

`LifecycleEngine.startSkipScrewLifecycle()` — 改为 `createRecord(ctx.getProductTaskId(), ctx.getProductCode(), ctx.getPartsCode(), 0)`

- [ ] **Step 5: Flyway 迁移**

```sql
ALTER TABLE task_record ADD COLUMN parts_code TEXT;
```

- [ ] **Step 6: 编译 + 全量测试**

---

### Task 8: T5 — 导出实现

**Files:**
- Modify: `pom.xml`（加 POI 依赖）
- Modify: `src/main/java/com/tightening/export/StandardExcelExporter.java`
- Create: `src/main/java/com/tightening/export/TxtExporter.java`
- Modify: `src/main/java/com/tightening/lifecycle/capability/ExportData.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/java/com/tightening/config/LocalSettings.java`

- [ ] **Step 1: pom.xml 加 POI**

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
```

- [ ] **Step 2: StandardExcelExporter**

注入 `TighteningDataService`，按 `taskRecordId` 查询拧紧数据：

```java
@Component
public class StandardExcelExporter implements Exporter {
    private static final String TYPE = "standard_excel";
    private static final Path EXPORT_DIR = Path.of(
        System.getProperty("user.home"), "tightening_system", "exports");

    private final TighteningDataService tighteningDataService;

    @Override
    public ExportResult execute(ExportPayload payload) {
        // 1. 查询拧紧数据
        List<TighteningData> rows = tighteningDataService.listByTaskRecordId(payload.taskRecordId());
        // 2. 创建导出目录
        Files.createDirectories(EXPORT_DIR);
        // 3. 写 .xlsx（POI XSSFWorkbook）
        // 4. 返回 ExportResult.ok(文件名)
    }
}
```

- [ ] **Step 3: TxtExporter**

同 StandardExcelExporter 模式，CSV 格式写 `.txt`：

```java
@Component
public class TxtExporter implements Exporter {
    private static final String TYPE = "txt";
    // 同上，写 CSV 格式（逗号分隔，双引号转义）
}
```

- [ ] **Step 4: ExportData Capability**

Payload 保持轻量，不传拧紧数据。确认 `settings.exportTypes()` 默认包含 `"standard_excel"` 和 `"txt"`。

`LocalSettings` — `exportTypes` 默认改为 `List.of("standard_excel", "txt")`。

- [ ] **Step 5: YAML**

`application.yaml` + `application.yml`（如有）确认 `export-types` 配置段：

```yaml
local-settings:
  self-loop-enabled: false
  export-types:
    - standard_excel
    - txt
```

- [ ] **Step 6: 编译 + 全量测试**

---

### Task 9: N7 — SudongX7 测试

**Files:** Create 6 test files under `src/test/java/com/tightening/device/handler/impl/sudong/`

- [ ] **Step 1: SudongX7FrameTest**

测试 `SudongX7Frame` 的构造、字段 getter/setter、边界值。

- [ ] **Step 2: SudongX7FrameCodecTest**

测试编解码双向转换、完整帧 → bytes → 完整帧一致性。

- [ ] **Step 3: SudongX7InitHandlerTest**

测试 `channelActive` 触发连接初始化逻辑。

- [ ] **Step 4: SudongX7InBoundHandlerTest**

测试不同消息类型的 channelRead0 分发。

- [ ] **Step 5: SudongSeriesHandlerTest**

测试 `connect`、`disconnect`、`getStatus`、`setupChannelInitializer`。

- [ ] **Step 6: SudongX7HandlerTest**

测试 `getSupportedTypes`、`unlockTool`、`lockTool`、`sendPSetCmd`、`sendHeartbeat`。

- [ ] **Step 7: 运行 SudongX7 相关测试** — `mvn test -Dtest="com.tightening.device.handler.impl.sudong.*"`

---

### 记录项（不实现）

**T6 — checkpoint 恢复**：记录到 gap tracker，后续实现。
**G8 — Flyway V1.0.5**：跳过，不影响任何功能。

---

## 执行顺序

独立任务可并行：

```
Phase 1 (并行): Task 1(T3), Task 2(G1), Task 3(G4)
Phase 2 (G4 就绪后): Task 4(T7) TCP 部分
Phase 3 (并行): Task 5(T2), Task 6(T4), Task 7(G2), Task 8(T5), Task 9(N7)
```

Task 6 (BoltConfig) 影响面最大（所有 currentBolt 调用方），建议先做编译验证再做其他。
