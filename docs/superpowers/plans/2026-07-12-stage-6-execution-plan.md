# Stage 6: Trigger Pipeline 接入修复 — Implementation Plan

> 基于: [Stage 6 设计文档](../specs/2026-07-10-stage-6-trigger-integration-fix.md)

**执行顺序**: 按依赖关系排列，TDD 应用于 §1，其余为删除/重命名。

---

## §1: 修复 trigger pipeline 未接入引擎 **[TDD]**

### 1a. RED — 写失败测试

**文件**: `src/test/java/com/tightening/lifecycle/LifecycleEngineFactoryTest.java`

新增测试 `shouldFaultWhenProductCodeRequiredButMissing`：

1. Mock `barCodeMatchingRuleService.listByTaskId(1L)` 返回一条 `PRODUCT_TRACE` 规则
2. Mock `ITool`（id、type），注册到 deviceRegistry（避免 WorkstationConfigCheck 失败）
3. `factory.createEngine(task, [bolt], {1L: tool}, false, null, null)` — productCode 为 null
4. 注册两个 latch：`onTriggered`（不应触发）和 `onFaulted`（应触发）
5. `engine.start(ctx)` + `postMessage(TriggerRequest(null, null))`
6. 断言 **`onFaulted` 被触发**（trigger pipeline 拦截）且 **`onTriggered` 未被触发**
7. `@AfterEach` 清理 `testEngine.shutdown()`（避免测试失败时线程泄漏）

当前 bug 行为：triggerCaps 为空 → pipeline Pass → checkCanActivate Pass → onTriggered 触发 → test FAIL（onFaulted latch 超时）。修复后：ProductBarCodeCheck Fail → onFaulted 触发 → test PASS。

### 1b. GREEN — 最小修复

**文件**: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java:81-82`

```java
// 替换 List.of() 为三个 TriggerCapability 实例
List<TriggerCapability> triggerCaps = List.of(
    new ProductBarCodeCheck(barCodeMatchingRuleService),
    new PartsBarCodeMatching(barCodeMatchingRuleService),
    new SkipScrewCheck()
);
LifecycleEngine engine = new LifecycleEngine(pipeline, taskRecordService,
        capabilities, monitors, triggerCaps);
```

### 1c. 附带：删除 `checkCanActivate()` 方法

TriggerCapability 接入后，`checkCanActivate()` 的逻辑变为检测能力存在性而非规则存在性——导致即使无规则也要求 productCode 不为 null（误判）。trigger pipeline 已做实际规则校验，该方法成为冗余门控。

**文件**: `LifecycleEngine.java` — 删除 `checkCanActivate()` 方法、调用点、`BarCodeRuleType` import

### 1d. 验证 — `mvn test` 全量通过

---

## §2: 删除 `TaskOrchestrator.startTask()`

**文件**: `src/main/java/com/tightening/lifecycle/TaskOrchestrator.java`

删除整个方法（约 44 行）。

**测试**: `TaskOrchestratorTest.java` — 3 个 `startTask()` 测试改为 `trigger()` 或删除（按 Stage 5 场景覆盖，trigger 已有足够测试覆盖）。

---

## §3: 删除 `InboundCommand.ActivateTask` 及 handler

**生产代码:**
- `InboundCommand.java`: 删除 `ActivateTask` record，清理不再需要的 import（BoltDeviceBinding、ProductBolt、ProductTask、ProductSide、List）
- `LifecycleEngine.java`: 删除 handler 注册行 + 删除 `handleActivateTask` 方法 + 内部方法重命名 `handleActivateTaskInternal`→`startNormalLifecycle`、`handleActivateTaskSkipScrew`→`startSkipScrewLifecycle`

**测试:**
- `LifecycleEngineTest.java`: `ActivateTask` 测试改为 `TriggerRequest`
- `InboundMessageTest.java`: 删除 `ActivateTask` record 测试

---

## §4: 删除 `InboundCommand.SelfLoop`

**生产代码**: `InboundCommand.java` — 删除 `SelfLoop` record

**测试**: `LifecycleEngineTest.java` — 删除 `SelfLoop` 测试

---

## §5: 删除 `ControllerStatusCheck`

**生产代码:**
- `ControllerStatusCheck.java` — 删除文件
- `LifecycleEngineFactory.java` — 删除 `new ControllerStatusCheck()` 注册行
- `TaskContext.java` — 删除 `tighteningStatus` 字段

**测试**: `ControllerStatusCheckTest.java` — 删除文件

---

## §6: 术语统一 enable/disable → lock/unlock

**映射**: `enableTool` → `unlockTool`, `disableTool` → `lockTool`

| 文件 | 改动 |
|---|---|
| `ToolHandler.java` | 抽象方法重命名 + 调用点 |
| `AtlasPFSeriesHandler.java` | override 方法 + frame factory 引用 |
| `FitSeriesHandler.java` | 同上 |
| `AtlasFrame.java` | 静态工厂方法重命名 |
| `FitFrame.java` | 同上 |

**不改**: `DeviceController` REST 端点（外部 API）

**测试**: 搜索 `enableTool\|disableTool` 确认无残留引用

---

## 验证检查点

- [ ] §1: `shouldFaultWhenProductCodeRequiredButMissing` 通过
- [ ] §2-5 每步后 `mvn compile -q` 无错误
- [ ] §6 后 `mvn test` 全量通过
- [ ] 最终 `grep -rn "enableTool\|disableTool" src/main` 无残留（ToolHandler 子类除外，已全部更新）
- [ ] `grep -rn "startTask\|ActivateTask\|SelfLoop\|ControllerStatusCheck" src` 无残留

---

## Simplify 审查修复

执行后通过 simplify 审查发现并修复了 4 个问题：

| 问题 | 修复 |
|---|---|
| 测试线程泄漏 | `LifecycleEngineFactoryTest` 加 `@AfterEach` shutdown + 去除无用 `taskRecordService` mock |
| `onCompleted` 先 cleanup 后读 ctx | 提前读取 `ctx.isShouldSelfLoop()` 到本地变量 |
| `ToolHandler.changeToolState` Javadoc 残留旧术语 | `@param targetEnabled` 改为"解锁/锁定" |
| 内部方法残留 `ActivateTask` 命名 | `handleActivateTaskInternal`→`startNormalLifecycle`、`handleActivateTaskSkipScrew`→`startSkipScrewLifecycle` |

---

## §7: 修复 SkipScrew 自循环泄漏

**缺陷**: `TaskOrchestrator.trigger()` 的 `onCompleted` 闭包用 `shouldSelfLoop`（settings 值）判断是否自循环，忽略了引擎 `ctx.setShouldSelfLoop(false)` 的决定。

**文件**: `src/main/java/com/tightening/lifecycle/TaskOrchestrator.java` — trigger() 方法 onCompleted 闭包

**修复**: onCompleted 闭包中提前读取 `ctx.isShouldSelfLoop()` 到本地变量，再加判断：

```java
boolean ctxShouldSelfLoop = ctx.isShouldSelfLoop();
cleanup(taskId);
if (shouldSelfLoop && ok && ctxShouldSelfLoop) {
```
