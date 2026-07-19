# Mission 统一保存接口设计

## 目标

将产品任务的新增/更新合并为两个统一的 multipart API，前端一个按钮完成保存，不再需要多次调用独立子接口。

## API 变更

### 新增/更新

```
POST   /api/missions          → 新增任务
PUT    /api/missions/{id}     → 更新任务（以路径 id 为准，忽略 DTO 中的 id）
```

请求格式：`multipart/form-data`

| part | 类型 | 说明 |
|------|------|------|
| `dto` | application/json | `ProductMissionSaveDTO` |
| `sides[0].image` | image/* | 产品面原图（可选） |
| `sides[0].renderedImage` | image/* | 产品面渲染图（可选） |
| `sides[0].thumbnail` | image/* | 产品面缩略图（可选） |
| `sides[1].image` ... | | 第二个面以此类推 |

图片文件按命名约定与 JSON 中的 sides 索引关联。

### 删除的接口

以下接口的 POST/PUT/DELETE 全部移除，GET 查询保留：

| 原端点 | 融入目标 |
|--------|---------|
| `POST/DELETE /api/missions/{id}/prerequisites/**` | SaveDTO.prerequisites |
| `POST/DELETE /api/missions/{id}/inspection-bindings/**` | SaveDTO.inspectionBoundMissionIds |
| `POST/DELETE /api/missions/{id}/barcode-rules/**` | SaveDTO.barcodeRules |
| `POST /api/sides` / `PUT /api/sides/{id}` / `DELETE /api/sides/{id}` | SaveDTO.sides |
| `POST /api/sides/{sideId}/uploadImage` / `uploadRenderedImage` / `uploadThumbnail` | multipart 文件 part |
| `POST/PUT/DELETE /api/bolts/**` | SaveDTO.sides[].bolts |

### 保留的接口

- 所有 GET 查询端点（list/get/check-name、prerequisites 查询、barcode-rules 查询、sides 查询、bolts 查询）
- 图片下载端点（`GET /api/sides/{sideId}/image`）

## DTO 结构

```java
// ProductMissionSaveDTO — 顶层 DTO
public class ProductMissionSaveDTO extends BaseDTO {
    // 基本字段（与 ProductMission 实体一致）
    private String name;
    private Integer maxNgCount;
    private Integer passwordRequiredNgCount;
    private Integer enabled;
    private Integer multiDeviceIndependent;
    private Integer skipScrew;
    private Integer isInspection;
    private InspectionScope inspectionScope;

    // 子项列表
    private List<Long> inspectionBoundMissionIds;       // 点检绑定，全量替换
    private List<PrerequisiteSaveItem> prerequisites;    // 前置任务
    private List<BarCodeRuleSaveItem> barcodeRules;      // 条码规则
    private List<ProductSideSaveItem> sides;             // 产品面（含螺栓）
}

// PrerequisiteSaveItem
public class PrerequisiteSaveItem extends BaseDTO {
    private Long prerequisiteMissionId;
    private Integer prerequisiteType;  // SAME_TRACE=1, MATERIAL_TRACE=2, INSPECTION_CHAIN=3
}

// BarCodeRuleSaveItem
public class BarCodeRuleSaveItem extends BaseDTO {
    private String name;
    private Integer ruleType;       // PRODUCT_TRACE=1, MATERIAL_BARCODE=2
    private String partNumber;
    private Integer expectedLength;
    private String segments;
}

// ProductSideSaveItem
public class ProductSideSaveItem extends BaseDTO {
    private String name;
    private List<ProductBoltSaveItem> bolts;
    // 不含图片字段 — 图片通过 multipart 文件 part 上传
}

// ProductBoltSaveItem
public class ProductBoltSaveItem extends BaseDTO {
    private Integer boltSerialNum;
    private String boltName;
    private Long parameterSetId;
    private Double torqueMin, torqueMax;
    private Double angleMin, angleMax;
    private String armLocation;
    private Double locationXPercent, locationYPercent;
    private Integer enabled;
    private List<BoltDeviceBindingSaveItem> deviceBindings;
    private List<BoltPartsBarcodeSaveItem> partsBarcodes;
}

// BoltDeviceBindingSaveItem
public class BoltDeviceBindingSaveItem extends BaseDTO {
    private Long deviceId;
    private Integer deviceRole;
    private Double deviceSpec;
    private Integer sortOrder;
}

// BoltPartsBarcodeSaveItem
public class BoltPartsBarcodeSaveItem extends BaseDTO {
    private Long barCodeMatchingRuleId;
}
```

所有 SaveItem 为独立的顶层类，放在 `dto` 包下。

## 更新策略：后端 Diff

新增时所有子项无 id，直接批量插入。

更新时后端对比 DTO 与数据库现有数据：

- DTO 子项无 id → 新增
- DTO 子项有 id 且 DB 存在 → 更新（null 字段不覆盖，MyBatis-Plus 默认行为）
- DB 有但 DTO 无 → 软删除

校验针对 diff 后的最终状态，而非仅看 DTO 输入。

## Service 层

`ProductMissionService.saveMission(SaveDTO, imageMap)` — 一个 `@Transactional` 方法：

1. **保存 Mission 基本字段** → 得到 missionId
2. **校验** `inspectionScope + binding`（`validateInspectionScope`）
3. **同步 inspectionBoundMissionIds** — 全量替换，批量查询目标任务后逐个校验
4. **diff barcodeRules** — `diffBarcodeRules` 处理增/改/删，循环内调用 `validateKeyCharLength` + `validateProductTraceUnique`，返回最终状态后调用 `validateBarcodeRules`（产品码 ≤1 条 + 物料码须有产品码）
5. **diff prerequisites** — `diffPrerequisites` 处理增/改/删，批量查询目标任务后逐个调用 `validateNoCircularDependency` + `validatePrerequisiteType`，最终调用 `validateInspectionChainSelfInspection`
6. **diff sides → bolts → deviceBindings / partsBarcodes** — `diffSides` → `diffBolts` → `diffDeviceBindings` / `diffPartsBarcodes` 三层嵌套 diff，图片从 imageMap 写入对应 side

已移除的旧方法：`addPrerequisite`、`addInspectionBinding`、`addBarcodeRule`（逻辑已内联到 diff 方法中）。

## 校验清单

| # | 校验 | 触发条件 | 已有/新增 |
|---|------|---------|----------|
| 1 | 任务名称唯一 | 总是 | 已有 |
| 2 | 产品码 ≤ 1 条 | 处理 barcodeRules，针对 diff 后最终状态 | 新增 |
| 3 | 物料码必须有产品码 | barcodeRules diff 后含 MATERIAL_BARCODE | 新增 |
| 4 | 循环依赖 BFS 检测 | prerequisites 变更 | 已有 |
| 5 | 前置类型约束（点检链↔点检任务，工艺依赖↔普通任务） | prerequisites 变更 | 已有 |
| 6 | INSPECTION_CHAIN 当前任务必须是点检 | prerequisites 含 INSPECTION_CHAIN | **新增** |
| 7 | inspectionScope + binding 校验 | isInspection=1 | 已有 |

## Controller 层

替换现有 `ProductMissionController` 中的 `POST /api/missions` 和 `PUT /api/missions/{id}` 为 multipart 版本。通过 `HttpServletRequest` 手动解析 `MultipartHttpServletRequest`，提取 `dto` 参数（JSON）和文件 parts。`asMultipart()` 共享守卫方法做类型安全检查。

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ApiResponse<String>> create(HttpServletRequest request) { ... }

@PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ApiResponse<String>> update(@PathVariable Long id, HttpServletRequest request) { ... }
```

更新时以路径 `{id}` 为准，覆盖 DTO 中的 id。

`ProductMissionController` 中以下端点已移除：
- `addPrerequisite` / `deletePrerequisite` / `addInspectionBinding` / `deleteInspectionBinding` / `addBarcodeRule` / `deleteBarcodeRule`
- `PrerequisiteRequest` / `InspectionBindingRequest` record 类型

`ProductSideController` / `ProductBoltController` 中 POST/PUT/DELETE 及图片上传端点已移除，仅保留 GET 查询和图片下载。

## 前置类型术语

| 枚举值 | code | 含义 |
|--------|------|------|
| `SAME_TRACE` | 1 | 产品码前置：两个任务共用同一产品码 |
| `MATERIAL_TRACE` | 2 | 物料码前置：指定物料码为主体，前置任务的产品码 = 当前物料码的值 |
| `INSPECTION_CHAIN` | 3 | 点检链：当前任务和前置任务都必须是点检任务 |

## 条码规则类型

| 枚举值 | code | 含义 |
|--------|------|------|
| `PRODUCT_TRACE` | 1 | 产品码（追溯码），每个任务最多 1 条 |
| `MATERIAL_BARCODE` | 2 | 物料码，0-n 条，须先有产品码 |

## 枚举重命名

| 旧名 | 新名 |
|------|------|
| `PARTS_TRACE` | `MATERIAL_TRACE` |
| `PARTS_BARCODE` | `MATERIAL_BARCODE` |

枚举 code 值不变（PARTS_TRACE 和 MATERIAL_TRACE 都是 2，PARTS_BARCODE 和 MATERIAL_BARCODE 都是 2），无需数据库迁移。

## Code Review 修正

实现后经三轮 review，修正以下问题：

| # | 严重 | 问题 | 修复 |
|---|------|------|------|
| 1 | 严重 | `diffBarcodeRules` 丢失旧校验 | 补回 `validateKeyCharLength` + `validateProductTraceUnique` |
| 2 | 重要 | `diffPrerequisites` if/else 重复校验 | 提取到 if/else 之前 |
| 3 | 重要 | `isInspectionMission` 三处内联 | 统一调用私有方法 |
| 4 | 重要 | `syncInspectionBindings`/`diffPrerequisites` N+1 查询 | 批量加载后映射查找 |
| 5 | 重要 | Controller `(MultipartHttpServletRequest) request` 无类型检查 | 提取 `asMultipart()` 共享守卫 |
| 6 | 次要 | `addPrerequisite`/`addInspectionBinding` 死代码 | 移除 |
| 7 | 次要 | saveMission 中 6 条注释重复代码语义 | 删除 |
| 8 | 次要 | `JsonUtils.OBJECT_MAPPER.readValue` 绕过封装 | 改用 `JsonUtils.parse` |

已知但未修的限制：
- 6 个 diff 方法的通用模板未提取（各自校验逻辑不同，强行泛型降低可读性）
- HTTP multipart 命名约定（`sides[0].image`）流入 Service 层（涉及 Controller/Service 接口重构）
- 批量 save/update SQL（SQLite 单连接场景下收益有限）
