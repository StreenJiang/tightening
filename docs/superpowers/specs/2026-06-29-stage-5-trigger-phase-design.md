# Stage 5: 触发阶段 (Trigger Phase) — 设计文档

> 设计日期: 2026-06-29
> 基于: [Task 生命周期架构设计](../docs/2026-06-21-task-lifecycle-architecture-design.md) §2.3
> 前置: Stage 0-4（LifecycleEngine、Capability 管道、TaskOrchestrator）

---

## 1. 范围

实现 Task 生命周期的触发入口。触发阶段在 VALIDATION 之前执行，负责条码校验、SkipScrew 快速通道、激活门控。

**包含:**
- 3 个 REST 端点（产品码校验、物料码校验、触发激活）
- 3 个新 Capability（ProductBarCodeCheck、PartsBarCodeMatching、SkipScrewCheck）
- 引擎触发管道（pre-VALIDATION）
- CheckCanActivate 门控（引擎内置）
- TaskContext 新增产品码/物料码字段

**不含:**
- SCANNER_INPUT / PLC_SIGNAL / PLC_READ（硬件适配层待后续）
- ChallengeTaskCheck / 点检任务（独立 stage）
- PlcBarcodeSelfLoop（PLC 硬件依赖）

---

## 2. 触发流程

### 2.1 整体链路

```
前端扫描产品码 ──► validate-product-barcode 端点（引擎外，纯查询）
                       │
             ┌─────────┴──────────┐
             ▼                    ▼
        MATCHED             WRONG_MISSION / NOT_MATCHED
             │               （前端决定是否切换任务或提示错误）
             │
   前端解锁物料码输入框（前端概念 — "锁定"仅 UI 状态，后端不缓存）
             │
             ▼
前端录入物料码 ──► validate-parts-barcode 端点（引擎外，纯查询）
                       │
             ┌─────────┴──────────┐
             ▼                    ▼
           PASS                  FAIL
             │                    （前端提示不匹配）
   前端自动调用 trigger 端点
             │
             ▼
      引擎 inbox → 触发管道 → 激活成功/失败
```

无条码工位：前端直接调用 trigger，跳过校验端点。

### 2.2 触发管道（引擎内，pre-VALIDATION）

```
TriggerRequest { productCode, partsCode }
  │
  ├─► ProductBarCodeCheck     有 PRODUCT_TRACE 规则 → 匹配 → Pass
  │                            有规则+未提供 → Fail
  │                            无规则 → Skip
  │
  ├─► PartsBarCodeMatching    有 PARTS_BARCODE 规则 → 匹配 → Pass
  │                            有规则+未提供 → Fail
  │                            无规则 → Skip
  │
  ├─► SkipScrewCheck          task.skipScrew=true → Interrupt（快速通道）
  │                            否则 → Pass
  │
  └─► CheckCanActivate        引擎内置门控
                               有条码规则但未提供 → 拒绝激活
                               全部满足 → 进入 VALIDATION
```

**快速通道**: SkipScrewCheck 返回 Interrupt → 引擎跳过 VALIDATION/ACTIVATION/OPERATION → 直接进入 FINALIZATION → 创建 OK TaskRecord → 导出 → Completed(OK)。不自循环。

---

## 3. REST API

### 3.1 产品码校验

```
POST /api/tasks/{id}/validate-product-barcode
Body: { "productCode": "ABC123" }

响应:
  { "result": "MATCHED" }                  // 匹配当前 Task
  { "result": "WRONG_MISSION", "suggestedTaskId": 42 }  // 匹配到其它 Task
  { "result": "NOT_MATCHED" }              // 无任何匹配
```

**逻辑**:
1. 查询当前 Task 的 `BarCodeMatchingRule`（ruleType=PRODUCT_TRACE）
2. 无规则 → 直接返回 MATCHED（该 Task 无需产品码）
3. 有规则 → 对 productCode 做位置匹配（keyStartPosition/keyEndPosition/keyChar）
4. 匹配 → MATCHED
5. 不匹配 → 遍历其它 Task 的 PRODUCT_TRACE 规则
6. 找到匹配 → WRONG_MISSION(suggestedTaskId)
7. 无匹配 → NOT_MATCHED

### 3.2 物料码校验

```
POST /api/tasks/{id}/validate-parts-barcode
Body: { "partsCode": "MAT456" }
// productCode 不需要传 — 此接口调用时任务已锁定，产品码已在步骤1确认匹配

响应:
  { "result": "PASS" }
  { "result": "FAIL", "reason": "物料码不匹配" }
```

**逻辑**:
1. 查询当前 Task 的 `BarCodeMatchingRule`（ruleType=PARTS_BARCODE）
2. 无规则 → 直接返回 PASS
3. 有规则 → 对 partsCode 做位置匹配
4. 匹配 → PASS
5. 不匹配 → FAIL

### 3.3 触发激活

```
POST /api/tasks/{id}/trigger
Body: { "productCode": "ABC123", "partsCode": "MAT456" }

响应:
  202 Accepted { "message": "trigger request accepted" }
  400 Bad Request { "message": "task already active" }
```

**逻辑**: 将 `TriggerRequest` 投递到引擎 inbox → 管道串行处理 → 激活成功/失败通过 SSE 通知前端。

---

## 4. 数据模型变更

### 4.1 TaskContext 新增字段

```java
/** 产品追溯码（触发阶段写入，生命周期内不可变） */
@Setter private String productCode;
/** 物料码（触发阶段写入，生命周期内不可变） */
@Setter private String partsCode;
```

条码仅在内存中持有。自循环时条码行为：**无** PRODUCT_TRACE 规则 → 以 null 触发；**有** 规则 → 复用上次 productCode（前端缓存，任务不变无需重新扫码）。

### 4.2 已有实体复用（不变更）

- `BarCodeMatchingRule` — 位置匹配规则（keyStartPosition, keyEndPosition, keyChar）
- `BarCodeRuleType` — PRODUCT_TRACE(1), PARTS_BARCODE(2)
- `ProductTask.skipScrew` — SkipScrew 快速通道标志

---

## 5. 引擎变更

### 5.1 触发管道与 4 阶段管道的关系

触发管道是**独立于 4 阶段管道**的 pre-gate。触发 Capability 不在 `PipelineDefinition` 的 4 阶段注册表中，而是由 `LifecycleEngineFactory` 作为单独列表注入引擎。引擎在处理 TriggerRequest 时**先跑触发管道**，全部通过后才进入正常的 4 阶段生命周期。

```
TriggerRequest 进入 inbox
  → executeTriggerPipeline()  // 独立管道，非 4 阶段
    ├─ ProductBarCodeCheck
    ├─ PartsBarCodeMatching
    └─ SkipScrewCheck
  → 任一 Fail → Aborted，生命周期结束
  → SkipScrewCheck Interrupt → 跳转 FINALIZATION
  → 全部 Pass → CheckCanActivate → 进入 VALIDATION → 正常 4 阶段流程
```

### 5.2 新消息类型

```java
// InboundCommand 新增
record TriggerRequest(
    @Nullable String productCode,
    @Nullable String partsCode
) implements InboundCommand {}
```

### 5.3 onTriggered 回调

引擎新增 `onTriggered` 回调（Consumer），在触发管道通过、进入 VALIDATION 时通知 `TaskOrchestrator` 执行后续初始化（设置 device mapping、注册完成回调、启动 tick 等）。拆分为两步的原因：`TriggerRequest` 到达时尚未绑定设备，管道通过后确认不会中止，才建立设备关联。

### 5.4 CheckCanActivate（引擎内置，非 Capability）

```
CheckCanActivate(ctx):
    productRules = getRules(ctx.taskId, PRODUCT_TRACE)
    partsRules  = getRules(ctx.taskId, PARTS_BARCODE)
    if productRules 非空 and ctx.productCode == null → reject("需要产品追溯码")
    if partsRules 非空 and ctx.partsCode == null   → reject("需要物料码")
    → 放行，进入 VALIDATION
```

---

## 6. 触发 Capability 清单

触发 Capability 实现 `TriggerCapability` 标记接口（继承 `Capability`），**不属于 4 阶段管道**。由 `LifecycleEngineFactory` 作为独立 `List<TriggerCapability>` 注入引擎，引擎通过 `executeTriggerPipeline()` 执行。`TriggerCapability` 的类型隔离确保触发 Capability 不会被误注册到 `PipelineDefinition` 中。

### ProductBarCodeCheck

| 属性 | 值 |
|------|-----|
| priority | 1 |

**precondition**: 无（始终执行）

**execute**:
- 无 PRODUCT_TRACE 规则 → Skip
- 有规则 + productCode 为空 → Fail("需要产品追溯码")
- 有规则 + 位置匹配成功 → Pass
- 有规则 + 位置匹配失败 → Fail("产品追溯码不匹配")

### PartsBarCodeMatching

| 属性 | 值 |
|------|-----|
| priority | 2 |

**precondition**: 无（始终执行）

**execute**: 逻辑同 ProductBarCodeCheck，校验 PARTS_BARCODE 规则

### SkipScrewCheck

| 属性 | 值 |
|------|-----|
| priority | 3 |

**precondition**: ctx.taskData.skipScrew == true

**execute**: 返回 Interrupt("SkipScrew fast track")，引擎识别后跳转 FINALIZATION。快速通道中 `shouldSelfLoop=false`，不绑定设备。引擎在 `handleActivateTaskSkipScrew` 中直接创建 OK TaskRecord 后走 FINALIZATION 管道。

### 6.1 TriggerCapability 标记接口

```java
/** 触发阶段的 Capability，不属于 4 阶段管道。继承 Capability 但不用于 PipelineDefinition 注册。 */
public interface TriggerCapability extends Capability {
}
```

三个触发 Capability 实现 `TriggerCapability`。`LifecycleEngine` 持有 `List<TriggerCapability>`，类型系统确保它们不会被误传入 `PipelineDefinition.registerCapabilities()`。

---

## 7. Controller 变更

`TaskLifecycleController` 新增 3 个端点，原有 `/activate` 替换为 `/trigger`：

- 删除: `POST /{id}/activate`
- 新增: `POST /{id}/validate-product-barcode`
- 新增: `POST /{id}/validate-parts-barcode`
- 新增: `POST /{id}/trigger`
- 保留: `POST /{id}/interrupt`、`GET /{id}/status`

Controller 依赖注入新增: `BarCodeMatchingRuleService`、`BarcodeValidationService`（用于跨 Task 查询）

### 7.1 并发防护

`TaskOrchestrator.trigger()` 末尾使用 `activeEngines.putIfAbsent()` 替代 `put()`，防止并发触发导致同一个 Task 被激活两次。`putIfAbsent` 返回非 null 时表示竞态失败，关闭引擎并返回 null。

---

## 8. 场景覆盖

| # | 场景 | productCode | partsCode | 预期结果 |
|---|------|-------------|-----------|----------|
| 1 | 无条码工位直接触发 | null | null | 条码规则 Skip → CheckCanActivate 放行 → VALIDATION |
| 2 | 有条码工位直接触发（未扫码） | null | null | ProductBarCodeCheck Fail → 激活失败 |
| 3 | 产品码通过，无物料码配置 | "ABC" | null | ProductBarCodeCheck Pass → PartsBarCodeMatching Skip → 进入 VALIDATION |
| 4 | 产品码通过，有物料码但未提供 | "ABC" | null | ProductBarCodeCheck Pass → PartsBarCodeMatching Fail → 激活失败 |
| 5 | 产品码+物料码全部通过 | "ABC" | "MAT" | 全部 Pass → CheckCanActivate → VALIDATION |
| 6 | SkipScrew | "ABC" | "MAT" | SkipScrewCheck Interrupt → 快速通道 → FINALIZATION |
| 7 | 产品码匹配到其它任务 | "XYZ" | - | validate-product-barcode 返回 WRONG_MISSION |

---

## 9. 已知问题

**Trigger pipeline 未接入引擎**（2026-07-10 审查发现）。

三个 TriggerCapability（ProductBarCodeCheck、PartsBarCodeMatching、SkipScrewCheck）已实现，但 `LifecycleEngineFactory.createEngine()` 未创建并注入——传了 `List.of()` 空列表。结果是 `TaskOrchestrator.trigger()` 进入引擎后 trigger pipeline 空转、直接返回 Pass。

REST 层的条码校验端点（BarcodeValidationService）仍正常工作，但引擎层的防御性二次门控缺失。

**下一 stage 修复**: 见 [Stage 6 设计文档](2026-07-10-stage-6-trigger-integration-fix.md)。
