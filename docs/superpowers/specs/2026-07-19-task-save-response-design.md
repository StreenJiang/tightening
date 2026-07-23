# Task Save 接口返回完整数据

## 背景

当前 `create` 和 `update` 接口只返回 `ApiResponse<String>`（task ID），前端"暂存"场景需要在保存后获得所有新建子实体的真实 ID，以便后续更新操作能精确命中已有记录而非重复创建。

## 设计

### 返回数据结构

和入参 `ProductTaskSaveDTO` 对称：前端发什么结构，后端返回什么结构，每个节点补上后端生成的 `id`。

所有 save item DTO 已继承 `BaseDTO`（含 `id` 字段），无需新建响应类。

### 改动点

**BoltPartsBarcodeSaveItem** — 新增 `barcodeRuleRef` 字段，用于引用同批次新增的条码规则（当前任务自身的规则，不跨任务）。解析逻辑与 `PrerequisiteSaveItem.barcodeRuleRef` 一致：`barcodeRuleRef` 和 `barCodeMatchingRuleId` 互斥，有 `barcodeRuleRef` 则从 `clientRefMap` 解析为真实 ID。不校验规则类型（PartsBarcode 默认对应物料码，无须额外校验）。

**ProductTaskService**:
- `saveTask` — 返回类型从 `Long` 改为 `ProductTaskSaveDTO`；`saveOrUpdate(task)` 后回填 `dto.id`
- `diffBarcodeRules` — 新增的 `BarCodeRuleSaveItem` 回填 `entity.getId()`
- `diffPrerequisites` — 同理
- `diffSides` → `diffBolts` → `diffPartsBarcodes` — 逐层回填 ID；`BarcodeDiffResult` 透传到 `diffPartsBarcodes` 以解析 `barcodeRuleRef`
- `diffDeviceBindings` — 新增的 item 回填 `entity.getId()`

**ProductTaskController** — `create` 和 `update` 返回 `ApiResponse<ProductTaskSaveDTO>`

### 不改的

- `inspectionBoundTaskIds` 入参即是 `List<Long>`，无新 ID 可补
- 校验逻辑、事务边界不动
- 零新类
