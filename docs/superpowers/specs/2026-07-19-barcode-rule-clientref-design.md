# 条码规则 clientRef 关联设计

## 问题

`saveMission` 单次请求中，前端同时提交新增的物料条码规则和引用该规则的前置条件。新规则尚未入库、没有 DB ID，前置条件又需要 `barcodeRuleId` 来关联。判断标准是**条码规则本身是否新增**，与任务是否新增无关——编辑已有任务时也可能添加新规则。

## 方案

前端为新规则生成客户端唯一标识（`clientRef`），前置条件通过 `barcodeRuleRef` 引用。`diffBarcodeRules` 先保存规则拿到真实 ID 并构建映射，`diffPrerequisites` 再用映射解析引用。

### 领域约束

- `barcodeRuleId` 始终引用**当前任务自身**的 MATERIAL_BARCODE 规则——不会跨任务引用
- 只有 MATERIAL_TRACE 类型的前置条件需要 `barcodeRuleId`。SAME_TRACE 每个任务至多一个产品码规则可通过任务定位，INSPECTION_CHAIN 是纯任务-任务依赖不涉及条码匹配
- 只有新增规则（`id == null`）才设置 `clientRef`，已有规则直接用 `barcodeRuleId`
- 被前置条件引用的规则不可删除——前端保证，后端不加防御性校验

### 数据流

```
请求:
  barcodeRules: [
    { clientRef: "abc-123", ... }   // 新增，无 id
    { id: 5, ... }                  // 已有，无 clientRef
  ]
  prerequisites: [
    { barcodeRuleRef: "abc-123", prerequisiteMissionId: 3, prerequisiteType: MATERIAL_TRACE }
  ]

saveMission():
  result = diffBarcodeRules(missionId, dto.getBarcodeRules())
  // result.rules()       = [rule(42), rule(5)]
  // result.clientRefMap() = { "abc-123" → 42 }
  validator.validateBarcodeRules(result.rules())
  diffPrerequisites(missionId, dto.getPrerequisites(), result)
```

### 改动范围

| 文件 | 改动 |
|---|---|
| `BarCodeRuleSaveItem` | 新增 `clientRef: String` |
| `PrerequisiteSaveItem` | 新增 `barcodeRuleRef: String` |
| `ProductMissionService` | 新增 `private record BarcodeDiffResult`；`diffBarcodeRules` 返回该类型；`diffPrerequisites` 接收 `BarcodeDiffResult` |
| `MissionConfigValidator` | 新增 `validateBarcodeRuleForPrerequisite` |

### 不变

- `MissionPrerequisite` 实体、DB 表不变（`barcodeRuleId` 最终存真实 ID）
- `BarCodeMatchingRule` 实体不变

### 解析与校验

`diffPrerequisites` 中对每个前置条件，按顺序：

1. `barcodeRuleRef` 和 `barcodeRuleId` 同时有值 → 抛 `IllegalArgumentException`
2. `barcodeRuleRef` 有值 → 从 `result.clientRefMap()` 解析，找不到抛异常
3. `barcodeRuleRef` 为空 → 直接用 `barcodeRuleId`
4. 非 MATERIAL_TRACE 但 barcodeRuleId 非空 → 调用 `validator.validateBarcodeRuleForPrerequisite` 抛异常
5. MATERIAL_TRACE 但 barcodeRuleId 为空 → 同上抛异常
6. barcodeRuleId 非空 → 在 `result.rules()` 中查找，调用 `validator.validateBarcodeRuleForPrerequisite` 校验类型为 MATERIAL_BARCODE
