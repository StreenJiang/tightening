# Stage 6: Trigger Pipeline 接入修复

> 设计日期: 2026-07-10
> 前置: Stage 5（触发阶段的条码校验和 pipeline）

---

## 1. 范围

**包含:**
- 修复 `LifecycleEngineFactory` 未注入 TriggerCapability 的缺陷
- 删除 `TaskOrchestrator.startTask()` 死代码（grill 审查发现）
- 删除 `InboundCommand.ActivateTask` 消息类型及 handler（grill 审查发现）
- 删除 `InboundCommand.SelfLoop` 消息类型（grill 审查发现）
- 术语统一：ToolHandler 内部抽象方法 `enableTool`/`disableTool` → `unlockTool`/`lockTool`（grill 审查发现）
- 删除 `ControllerStatusCheck` Capability——`ctx.tighteningStatus` 是只写字段，无任何读取方，JudgmentStrategy 直接从 DTO 取值（grill 审查发现）

**不含:**
- BoltBarCodeCheck / SendArrangerSignal / SendSetterSelector 补全（后续硬件适配 stage）
- SCANNER_INPUT / PLC_SIGNAL / PLC_READ（后续硬件适配 stage）
- ChallengeTaskCheck / 点检任务（独立 stage）

---

## 2. 缺陷描述

**文件**: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java:81-82`

Stage 5 实现了三个 TriggerCapability（`ProductBarCodeCheck`、`PartsBarCodeMatching`、`SkipScrewCheck`），各自有完整的匹配逻辑。`LifecycleEngine` 通过 `List<TriggerCapability> triggerCaps` 持有并执行触发管道。

但 `LifecycleEngineFactory.createEngine()` 未创建 TriggerCapability 实例——传了 `List.of()` 空列表：

```java
// 当前 Bug: triggerCapabilities 传空列表
LifecycleEngine engine = new LifecycleEngine(pipeline, taskRecordService,
        capabilities, monitors, List.of());
```

**影响**: `TaskOrchestrator.trigger()` 发送 `TriggerRequest` 进入引擎后，`executeTriggerPipeline()` 遍历空列表直接返回 Pass，条码校验在引擎层完全绕过。Controller 的 REST 校验端点（`validate-product-barcode` / `validate-parts-barcode`）虽正常工作，但引擎层的防御性二次门控缺失。

**为什么引擎内门控必要:**
- Controller 的 REST 校验是引擎外的预检，依赖前端正确调用
- 若前端绕过校验端点直接调 trigger，引擎应拦截未授权请求
- `SkipScrewCheck` 的 Interrupt 快速通道只能在引擎内实现（需跳转 FINALIZATION）

---

## 3. 修复方案

工厂已注入 `BarCodeMatchingRuleService`（第 33 行），创建三个 TriggerCapability 传入即可：

```java
List<TriggerCapability> triggerCaps = List.of(
    new ProductBarCodeCheck(barCodeMatchingRuleService),
    new PartsBarCodeMatching(barCodeMatchingRuleService),
    new SkipScrewCheck()
);
LifecycleEngine engine = new LifecycleEngine(pipeline, taskRecordService,
        capabilities, monitors, triggerCaps);
```

---

## 4. 验证

修复后 trigger pipeline 应覆盖 Stage 5 设计文档 §8 的所有场景（7 条）。

回归测试：`mvn test` 全量通过。

---

## 5. 附带清理：删除 `startTask()`

`TaskOrchestrator.startTask()` 是 Stage 5 之前（pre-trigger）的旧入口，存在三个问题：

1. **零调用方** — 没有生产代码或测试引用它
2. **设备绑定时机错误** — 在引擎启动前就绑定了 `deviceToTaskId`，不管激活是否成功
3. **缺少并发保护** — 使用 `put()` 而非 `putIfAbsent()`，无法防御并发触发

Stage 5 引入的 `trigger()` 已完全替代其功能（含条码校验、并发保护、延迟设备绑定）。直接删除该方法。

同步更新 `TaskOrchestratorTest.java` 中 3 个 `startTask()` 测试（lines 82-132），改为测试 `trigger()` 或删除。

## 6. 附带清理：删除 `ActivateTask` 消息类型

`InboundCommand.ActivateTask` 在 `startTask()` 删除后不再被任何人投递到 inbox。其 handler `handleActivateTask` 只是忽略 msg 参数直接调用 `handleActivateTaskInternal`——而 `handleTriggerRequest` 已经在直接调用 `handleActivateTaskInternal`。

清理内容：
- 删除 `ActivateTask` record 定义及其关联 import（BoltDeviceBinding、ProductBolt、ProductTask、ProductSide、List）
- 删除 handler 注册行
- 删除 `handleActivateTask` 方法（内部调用链仍然完整）

同步更新测试：
- `LifecycleEngineTest.java`：`ActivateTask` 测试（line 150）改为 `TriggerRequest`
- `InboundMessageTest.java`：`ActivateTask` record 测试（lines 17-21）删除

## 7. 附带清理：删除 `SelfLoop` 消息类型

`InboundCommand.SelfLoop` 从未被实例化、post 或注册 handler。自循环的实际路径是 `TaskCompletedEvent` → `@EventListener handleRestart` → `trigger()`（创建全新引擎而非在引擎内自重启）。

同步更新 `LifecycleEngineTest.java` 中 `SelfLoop` 测试（lines 68-72），删除。

## 8. 术语统一：enable/disable → lock/unlock

CONTEXT.md 已将 `enable / disable` 和 `Lock / Unlock` 合并为单一 `Lock / Unlock` 条目。代码层仍存在旧命名：

**涉及文件（6 个）：**

| 文件 | 改动 |
|---|---|
| `ToolHandler.java` | 抽象方法 `enableTool`→`unlockTool`、`disableTool`→`lockTool`；调用点同步更新 |
| `AtlasPFSeriesHandler.java` | override 方法 + `AtlasFrame::enableTool`→`AtlasFrame::unlockTool` |
| `FitSeriesHandler.java` | override 方法 + `FitFrame::enableTool`→`FitFrame::unlockTool` |
| `AtlasFrame.java` | 静态工厂 `enableTool()`→`unlockTool()`、`disableTool()`→`lockTool()` |
| `FitFrame.java` | 同上 |

**不改：** `DeviceController` 的 `/enable` `/disable` REST 端点（外部 API，单独决定）。

## 9. 附带清理：删除 `ControllerStatusCheck`

`ControllerStatusCheck` 的唯一逻辑是将 `data.getTighteningStatus()` 写入 `ctx.setTighteningStatus()`。但 `ctx.tighteningStatus` 字段无任何读取方——`AtlasJudgment` 和 `FitJudgment` 均直接从 `dto.getTighteningStatus()` 取值，不读 context。

删除内容：
- `ControllerStatusCheck.java`
- `LifecycleEngineFactory.createEngine()` 中的 `new ControllerStatusCheck()` 注册行
- `TaskContext.tighteningStatus` 字段
- `ControllerStatusCheckTest.java`

## 10. 附带修复：SkipScrew 自循环泄漏

`TaskOrchestrator.trigger()` 的 `onCompleted` 闭包判断自循环时使用捕获的 `shouldSelfLoop`（settings 值），忽略了引擎通过 `ctx.setShouldSelfLoop(false)` 对 SkipScrew 快速通道禁用的自循环。导致 SkipScrew 空转 1000 次。

**修复**: onCompleted 闭包中提前读取 `ctx.isShouldSelfLoop()` 到本地变量，再加判断：

```java
boolean ctxShouldSelfLoop = ctx.isShouldSelfLoop();
cleanup(taskId);
if (shouldSelfLoop && ok && ctxShouldSelfLoop) {
```

## 11. Simplify 审查修复

| 问题 | 修复 |
|---|---|
| `LifecycleEngineFactoryTest` 测试线程泄漏 | 加 `@AfterEach` shutdown |
| `TaskOrchestrator.onCompleted` 先 cleanup 后读 ctx | 提前读 `ctx.isShouldSelfLoop()` 到本地变量 |
| `ToolHandler.changeToolState` Javadoc 残留 `@param targetEnabled true=启用，false=禁用` | 改为"解锁/锁定" |
| `LifecycleEngine` 内部方法残留 `ActivateTask` 命名 | `handleActivateTaskInternal`→`startNormalLifecycle`、`handleActivateTaskSkipScrew`→`startSkipScrewLifecycle` |
| 附带：`checkCanActivate()` 误判导致引擎层 barrier 过严 | 删除方法及 `BarCodeRuleType` import，trigger pipeline 已做实际校验 |
