# Task 配置表设计

> 设计日期: 2026-06-24
> 设计范围: 从旧系统 `aneng` MySQL 迁移 `product_task`、`product_side`、`product_bolt` 三张表到当前项目 SQLite，优化字段命名和类型

## 概述

从旧系统 `aneng` MySQL 迁移四张表到当前项目 SQLite，构成拧紧任务的**配置层**——定义任务模板、产品面、螺栓点位、条码规则。与已有的运行时记录层（`task_record` + `tightening_data`）通过 `product_task_id` 关联。

```
product_task (任务配置模板)
  ├── task_prerequisite (新增 - 任务前置依赖)
  ├── inspection_task_binding (新增 - 点检任务绑定)
  ├── bar_code_matching_rule (新增 - 条码匹配规则)
  ├── product_side (产品面 + 图片)
  │     └── product_bolt (螺栓点位)
  │           ├── bolt_device_binding (新增 - 螺栓设备关联)
  │           └── bolt_parts_barcode (新增 - 螺栓物料码关联)
  └── task_record (已存在 - 运行时记录)
```

## 表结构

### product_task — 任务配置模板

```sql
CREATE TABLE product_task (
    id                            INTEGER PRIMARY KEY AUTOINCREMENT,
    name                          TEXT,
    max_ng_count                  INTEGER,
    password_required_after_ng    INTEGER,
    enabled                       INTEGER DEFAULT 1,
    multi_device_independent      INTEGER DEFAULT 0,
    skip_screw                    INTEGER DEFAULT 0,
    is_inspection                 INTEGER DEFAULT 0,
    inspection_scope              INTEGER,            -- 1=ALL 2=CHOSEN；is_inspection=0 时此字段为 NULL
    deleted                       INTEGER DEFAULT 0,
    creator_id                    INTEGER,
    modifier_id                   INTEGER,
    create_time                   TEXT,
    modify_time                   TEXT
);
```

相比源表移除的字段：`pn_code`、`macs_id`、`is_challenge_task`、`is_first_task`、`challenge_task_id`、`predecessor_task_id`、`predecessor_part_task_ids`、`user_id`。前置依赖逻辑拆入 `task_prerequisite` 表。

### task_prerequisite — 任务前置依赖

```sql
CREATE TABLE task_prerequisite (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id                  INTEGER NOT NULL,
    prerequisite_task_id     INTEGER NOT NULL,
    prerequisite_type           INTEGER NOT NULL,   -- 1=SAME_TRACE 2=PARTS_TRACE 3=INSPECTION_CHAIN
    deleted                     INTEGER DEFAULT 0,
    creator_id                  INTEGER,
    modifier_id                 INTEGER,
    create_time                 TEXT,
    modify_time                 TEXT
);
```

**依赖类型枚举**：

| code | 类型 | 含义 |
|---|---|---|
| 1 | `SAME_TRACE` | 当前任务与前置任务使用同一个追溯码 |
| 2 | `PARTS_TRACE` | 当前任务的物料码 = 前置任务的追溯码 |
| 3 | `INSPECTION_CHAIN` | 点检任务之间的先后顺序 |

**后端校验规则**：
- SAME_TRACE / PARTS_TRACE 的 `prerequisite_task_id` 必须指向 `is_inspection=0` 的任务
- INSPECTION_CHAIN 的 `prerequisite_task_id` 必须指向 `is_inspection=1` 的任务
- 配置保存时做循环依赖检测

### inspection_task_binding — 点检任务绑定

```sql
CREATE TABLE inspection_task_binding (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    inspection_task_id       INTEGER NOT NULL,
    bound_task_id            INTEGER NOT NULL,
    deleted                     INTEGER DEFAULT 0,
    creator_id                  INTEGER,
    modifier_id                 INTEGER,
    create_time                 TEXT,
    modify_time                 TEXT
);
```

- `inspection_scope=ALL(1)` 时，运行时遍历全部 `is_inspection=0` 的任务，不使用此表。
- `inspection_scope=CHOSEN(2)` 时，必须在此表配置被点检的任务（至少一行），运行时仅点检指定的任务。

### bar_code_matching_rule — 条码匹配规则

```sql
CREATE TABLE bar_code_matching_rule (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    name                  TEXT,
    product_task_id    INTEGER,              -- FK → product_task.id
    rule_type             INTEGER NOT NULL,     -- 1=PRODUCT_TRACE 2=PARTS_BARCODE
    part_number           TEXT,                 -- 零件号（物料码规则专用）
    expected_length       INTEGER,              -- 条码期望长度
    key_start_position    INTEGER,              -- 起始位置 (1-indexed)
    key_end_position      INTEGER,              -- 结束位置，NULL=单字符
    key_char              TEXT,                 -- 关键字符
    deleted               INTEGER DEFAULT 0,
    creator_id            INTEGER,
    modifier_id           INTEGER,
    create_time           TEXT,
    modify_time           TEXT
);
```

**校验逻辑**：
- `expected_length`：条码长度必须完全匹配
- `key_start_position` + `key_end_position` + `key_char`：指定位置字符必须在允许集合中。单字符校验只填 `key_start_position`；范围校验填起止位置，`key_char` 长度等于 `end-start+1`
- `key_char` 长度与 position 范围不匹配 → 保存时拒绝

**物料码绑定规则**：
- 任务级：通过 `product_task_id` 绑定（`rule_type=PARTS_BARCODE`），任务激活前校验
- 螺栓级：通过 `bolt_parts_barcode` 额外绑定到特定螺栓，拧该螺栓时校验
- 两类物料码独立存在，互不替代——同时配置时，任务激活校验任务级，螺栓拧紧时额外校验螺栓级

### product_side — 产品面

```sql
CREATE TABLE product_side (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    product_task_id    INTEGER NOT NULL,
    name                  TEXT,
    image_data            BLOB,              -- 原图（不可变，用于重新编辑）
    rendered_image_data   BLOB,              -- 前端编辑后导出的成品图
    thumbnail_data        BLOB,              -- 缩略图
    deleted               INTEGER DEFAULT 0,
    creator_id            INTEGER,
    modifier_id           INTEGER,
    create_time           TEXT,
    modify_time           TEXT
);
```

图片策略：前端完成所有编辑（旋转、缩放、裁剪）后通过 `canvas.toBlob()` 导出成品图上传，后端只做存取，不处理像素。原图保留用于重新编辑。

> **BLOB 大小限制**：SQLite 默认最大 BLOB 为 1GB，但单张图片建议不超过 5MB。过大的图片影响写入性能和数据库文件体积，建议在应用层校验限制。

### product_bolt — 螺栓点位

```sql
CREATE TABLE product_bolt (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    product_side_id       INTEGER NOT NULL,
    bolt_serial_num       INTEGER NOT NULL,
    bolt_name             TEXT,
    parameter_set_id      INTEGER,
    torque_min            REAL,
    torque_max            REAL,
    angle_min             REAL,
    angle_max             REAL,
    arm_location          TEXT,
    location_x_percent    REAL DEFAULT 0,
    location_y_percent    REAL DEFAULT 0,
    enabled               INTEGER DEFAULT 1,
    deleted               INTEGER DEFAULT 0,
    creator_id            INTEGER,
    modifier_id           INTEGER,
    create_time           TEXT,
    modify_time           TEXT
);
```

`location_x_percent` / `location_y_percent` 为百分比（0-100），前端用 CSS 百分比定位渲染螺栓标记，与图片缩放无关。

### bolt_device_binding — 螺栓设备关联

```sql
CREATE TABLE bolt_device_binding (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    product_bolt_id       INTEGER NOT NULL,
    device_id             INTEGER NOT NULL,        -- FK → device.id
    device_role           INTEGER NOT NULL,   -- 1=ARRANGER 2=SETTER_SELECTOR
    device_spec           REAL,
    sort_order            INTEGER DEFAULT 0,
    deleted               INTEGER DEFAULT 0,
    creator_id            INTEGER,
    modifier_id           INTEGER,
    create_time           TEXT,
    modify_time           TEXT
);
```

替代源表写死的 `arranger_id/arranger_id2/setter_selector_id/bit_specification` 字段，支持任意数量的排列机和套筒选择器。

### bolt_parts_barcode — 螺栓物料码关联

```sql
CREATE TABLE bolt_parts_barcode (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    product_bolt_id             INTEGER NOT NULL,
    bar_code_matching_rule_id   INTEGER NOT NULL,  -- FK → bar_code_matching_rule.id
    deleted                     INTEGER DEFAULT 0,
    creator_id                  INTEGER,
    modifier_id                  INTEGER,
    create_time                 TEXT,
    modify_time                 TEXT
);
```

替代源表的 `parts_bar_code_ids` 逗号分隔字段。

## 索引建议

外键列是 JOIN 查询的频繁条件，建议对以下列建立索引以提升性能：

| 表 | 索引列 | 说明 |
|---|---|---|
| `task_prerequisite` | `task_id` | 按任务查前置依赖 |
| `task_prerequisite` | `prerequisite_task_id` | 按前置任务反查 |
| `inspection_task_binding` | `inspection_task_id` | 按点检任务查绑定 |
| `inspection_task_binding` | `bound_task_id` | 按被点检任务反查 |
| `product_side` | `product_task_id` | 按任务查面列表 |
| `product_bolt` | `product_side_id` | 按面查螺栓列表 |
| `bolt_device_binding` | `product_bolt_id` | 按螺栓查设备绑定 |
| `bolt_device_binding` | `device_id` | 按设备反查绑定 |
| `bar_code_matching_rule` | `product_task_id` | 按任务查条码规则 |
| `bolt_parts_barcode` | `product_bolt_id` | 按螺栓查物料码 |
| `bolt_parts_barcode` | `bar_code_matching_rule_id` | 按条码规则反查 |

> 索引命名建议：`idx_表名_列名`，例如 `idx_task_prerequisite_task_id`。

## 新增枚举

| 枚举类 | 值 |
|---|---|
| `PrerequisiteType` | SAME_TRACE(1), PARTS_TRACE(2), INSPECTION_CHAIN(3) |
| `InspectionScope` | ALL(1), CHOSEN(2) |
| `IoDeviceType` | ARRANGER(1), SETTER_SELECTOR(2) |
| `BarCodeRuleType` | PRODUCT_TRACE(1), PARTS_BARCODE(2) |
| `ImageType` | ORIGINAL("original"), RENDERED("rendered"), THUMBNAIL("thumbnail") |

## 约定

- 所有表使用蛇形命名，对齐现有迁移风格
- 布尔语义字段统一使用 `INTEGER DEFAULT 0`（SQLite 无原生布尔）
- 浮点数使用 `REAL`（对齐 SQLite + 现有 `tightening_data`）
- 时间字段使用 `TEXT`
- Entity 继承 `BaseEntity`，字段使用 `Integer` 而非枚举类型，与现有 `TaskRecord` 模式一致

## 迁移文件

| 序号 | 文件 | 内容 |
|---|---|---|
| V1.0.8 | `add_table_product_task.sql` | product_task + task_prerequisite + inspection_task_binding + bar_code_matching_rule |
| V1.0.9 | `add_table_product_side.sql` | product_side |
| V1.0.10 | `add_table_product_bolt.sql` | product_bolt + bolt_device_binding + bolt_parts_barcode |

> 注意：V1.0.5 在项目历史中被跳过（从未提交），V1.0.6 和 V1.0.7 已存在，因此新迁移从 V1.0.8 开始编号。

## Java 组件

所有组件遵循现有分层模式。Entity 继承 `BaseEntity`，字段使用 `Integer` 而非枚举；DTO 继承 `BaseDTO`。BLOB 字段在 Java 侧使用 `byte[]`。

### Entity

```java
// com.tightening.entity.ProductTask.java
@Setter @Getter @ToString(callSuper = true) @EqualsAndHashCode(callSuper = true)
@Accessors(chain = true) @TableName("product_task")
public class ProductTask extends BaseEntity {
    private String name;
    private Integer maxNgCount;
    private Integer passwordRequiredAfterNg;
    private Integer enabled;
    private Integer multiDeviceIndependent;
    private Integer skipScrew;
    private Integer isInspection;
    private Integer inspectionScope;
}
```

```java
// com.tightening.entity.TaskPrerequisite.java
@Setter @Getter @ToString(callSuper = true) @EqualsAndHashCode(callSuper = true)
@Accessors(chain = true) @TableName("task_prerequisite")
public class TaskPrerequisite extends BaseEntity {
    private Long taskId;
    private Long prerequisiteTaskId;
    private Integer prerequisiteType;
}
```

```java
// com.tightening.entity.InspectionTaskBinding.java
@Setter @Getter @ToString(callSuper = true) @EqualsAndHashCode(callSuper = true)
@Accessors(chain = true) @TableName("inspection_task_binding")
public class InspectionTaskBinding extends BaseEntity {
    private Long inspectionTaskId;
    private Long boundTaskId;
}
```

```java
// com.tightening.entity.BarCodeMatchingRule.java
@Setter @Getter @ToString(callSuper = true) @EqualsAndHashCode(callSuper = true)
@Accessors(chain = true) @TableName("bar_code_matching_rule")
public class BarCodeMatchingRule extends BaseEntity {
    private String name;
    private Long productTaskId;
    private Integer ruleType;
    private String partNumber;
    private Integer expectedLength;
    private Integer keyStartPosition;
    private Integer keyEndPosition;
    private String keyChar;
}
```

```java
// com.tightening.entity.ProductSide.java
@Setter @Getter @ToString(callSuper = true) @EqualsAndHashCode(callSuper = true)
@Accessors(chain = true) @TableName("product_side")
public class ProductSide extends BaseEntity {
    private Long productTaskId;
    private String name;
    private byte[] imageData;
    private byte[] renderedImageData;
    private byte[] thumbnailData;
}
```

```java
// com.tightening.entity.ProductBolt.java
@Setter @Getter @ToString(callSuper = true) @EqualsAndHashCode(callSuper = true)
@Accessors(chain = true) @TableName("product_bolt")
public class ProductBolt extends BaseEntity {
    private Long productSideId;
    private Integer boltSerialNum;
    private String boltName;
    private Long parameterSetId;
    private Double torqueMin;
    private Double torqueMax;
    private Double angleMin;
    private Double angleMax;
    private String armLocation;
    private Double locationXPercent;
    private Double locationYPercent;
    private Integer enabled;
}
```

```java
// com.tightening.entity.BoltDeviceBinding.java
@Setter @Getter @ToString(callSuper = true) @EqualsAndHashCode(callSuper = true)
@Accessors(chain = true) @TableName("bolt_device_binding")
public class BoltDeviceBinding extends BaseEntity {
    private Long productBoltId;
    private Long deviceId;
    private Integer deviceRole;
    private Double deviceSpec;
    private Integer sortOrder;
}
```

```java
// com.tightening.entity.BoltPartsBarcode.java
@Setter @Getter @ToString(callSuper = true) @EqualsAndHashCode(callSuper = true)
@Accessors(chain = true) @TableName("bolt_parts_barcode")
public class BoltPartsBarcode extends BaseEntity {
    private Long productBoltId;
    private Long barCodeMatchingRuleId;
}
```

### DTO

```java
// com.tightening.dto.ProductTaskDTO.java
@Setter @Getter @ToString(callSuper = true) @EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class ProductTaskDTO extends BaseDTO {
    private String name;
    private Integer maxNgCount;
    private Integer passwordRequiredAfterNg;
    private Integer enabled;
    private Integer multiDeviceIndependent;
    private Integer skipScrew;
    private Integer isInspection;
    private Integer inspectionScope;
}
```

其余 7 个 DTO 字段与对应 Entity 一致（不含 BLOB 字段——DTO 不传输图片二进制数据，图片通过独立上传接口处理），省略重复列举。

### Mapper

```java
// com.tightening.mapper/ProductTaskMapper.java
@Mapper
public interface ProductTaskMapper extends BaseMapper<ProductTask> {}

@Mapper public interface TaskPrerequisiteMapper extends BaseMapper<TaskPrerequisite> {}
@Mapper public interface InspectionTaskBindingMapper extends BaseMapper<InspectionTaskBinding> {}
@Mapper public interface BarCodeMatchingRuleMapper extends BaseMapper<BarCodeMatchingRule> {}
@Mapper public interface ProductSideMapper extends BaseMapper<ProductSide> {}
@Mapper public interface ProductBoltMapper extends BaseMapper<ProductBolt> {}
@Mapper public interface BoltDeviceBindingMapper extends BaseMapper<BoltDeviceBinding> {}
@Mapper public interface BoltPartsBarcodeMapper extends BaseMapper<BoltPartsBarcode> {}
```

### Service

```java
// com.tightening.service/ProductTaskService.java
@Slf4j @Service @RequiredArgsConstructor
public class ProductTaskService extends ServiceImpl<ProductTaskMapper, ProductTask> {
    private final TaskConfigValidator validator;
    private final ProductSideService sideService;
    // ... 其他 Service 注入

    @Transactional
    public void cascadeDelete(Long taskId) {
        // 批删模式：先收集 sideIds → 批量删除 bolts 及其关联 → 按 taskId 清空其他关联表 → 最后删除 task
        List<Long> sideIds = sideService.lambdaQuery().select(ProductSide::getId)...
        if (!sideIds.isEmpty()) {
            sideService.deleteBoltsBySideIds(sideIds);  // 批量删除螺栓及关联
            sideService.lambdaUpdate().in(ProductSide::getId, sideIds).remove();
        }
        // 清理前置依赖、点检绑定、条码规则
        prerequisiteService.lambdaUpdate()...remove();
        bindingService.lambdaUpdate()...remove();
        barcodeRuleService.lambdaUpdate()...remove();
        removeById(taskId);
    }
}

@Slf4j @Service public class TaskPrerequisiteService extends ServiceImpl<TaskPrerequisiteMapper, TaskPrerequisite> {}
@Slf4j @Service public class InspectionTaskBindingService extends ServiceImpl<InspectionTaskBindingMapper, InspectionTaskBinding> {}
@Slf4j @Service public class BarCodeMatchingRuleService extends ServiceImpl<BarCodeMatchingRuleMapper, BarCodeMatchingRule> {}

@Slf4j @Service @RequiredArgsConstructor
public class ProductSideService extends ServiceImpl<ProductSideMapper, ProductSide> {
    /** BLOB 高效读取：按需查询单字段，避免加载整行 BLOB */
    public byte[] getImageData(Long sideId, ImageType type) { ... }
    /** BLOB 高效写入：lambdaUpdate.set() 仅更新目标列，不传输无关数据 */
    public void updateImageData(Long sideId, byte[] data) { ... }
    public void updateRenderedImageData(Long sideId, byte[] data) { ... }
    public void updateThumbnailData(Long sideId, byte[] data) { ... }
    /** 批量删除螺栓关联（给 ProductTaskService.cascadeDelete 调用），不修改当前 side */
    public void deleteBoltsBySideIds(List<Long> sideIds) { boltService.deleteBoltsBySideIds(sideIds); }
}

@Slf4j @Service
public class ProductBoltService extends ServiceImpl<ProductBoltMapper, ProductBolt> {
    /** 批量删除螺栓及其 device_binding 和 parts_barcode 关联 */
    public void deleteBoltsBySideIds(List<Long> sideIds) { ... }
    /** 单螺栓级联删除 */
    @Transactional public void cascadeDelete(Long boltId) { ... }
}

@Slf4j @Service public class BoltDeviceBindingService extends ServiceImpl<BoltDeviceBindingMapper, BoltDeviceBinding> {}
@Slf4j @Service public class BoltPartsBarcodeService extends ServiceImpl<BoltPartsBarcodeMapper, BoltPartsBarcode> {}
```

> **级联删除模式**：`@Transactional` 仅标注在入口方法（`ProductTaskService.cascadeDelete`、`ProductSideService.cascadeDelete`、`ProductBoltService.cascadeDelete`）。内部的 `deleteBoltsBySideIds` 不标注 `@Transactional`，由调用方的事务管理（同一线程内 Propagation.REQUIRED）。

> **软删除策略**：当前代码使用**物理删除**（`removeById` / `lambdaUpdate().remove()`）+ 查询时手动 `.eq(Entity::getDeleted, 0)` 过滤，而非 MyBatis-Plus `@TableLogic` 注解。原因是 SQLite 下逻辑删除字段缺乏唯一约束支持，且无软删除查询的历史数据迁移需求。

### 新增枚举

| 枚举 | 文件路径 | 值 |
|---|---|---|
| `PrerequisiteType` | `constant/PrerequisiteType.java` | SAME_TRACE(1), PARTS_TRACE(2), INSPECTION_CHAIN(3) |
| `InspectionScope` | `constant/InspectionScope.java` | ALL(1), CHOSEN(2) |
| `IoDeviceType` | `constant/IoDeviceType.java` | ARRANGER(1), SETTER_SELECTOR(2) |
| `BarCodeRuleType` | `constant/BarCodeRuleType.java` | PRODUCT_TRACE(1), PARTS_BARCODE(2) |
| `ImageType` | `constant/ImageType.java` | ORIGINAL("original"), RENDERED("rendered"), THUMBNAIL("thumbnail") |

每个枚举使用 `Map` 预构建查找表实现常量解析（`fromCode(int) -> Optional<Enum>`），避免流式遍历的性能开销。注意 `ImageType` 使用 `String value` + `fromValue(String)` 模式（值分别为 `"original"`、`"rendered"`、`"thumbnail"`），其余枚举使用 `int code` + `fromCode(int)` 模式。

### 校验规则

`TaskConfigValidator` 负责配置保存时的应用层校验：

| 规则 | 说明 |
|---|---|
| 前置类型约束 | SAME_TRACE/PARTS_TRACE 的 `prerequisite_task_id` 必须指向 `is_inspection=0` 的任务；INSPECTION_CHAIN 必须指向 `is_inspection=1` 的任务 |
| 循环依赖检测 | 使用 **BFS**（`Deque` + `Set`）沿 `task_prerequisite` 图广度遍历所有可达路径（不再限定单链），检测到环则拒绝保存 |
| key_char 长度 | `key_char.length()` 必须等于 `end_position - start_position + 1`（范围）或 1（单值），不匹配拒绝保存 |
| bolt_serial_num 唯一性 | 同一 `product_task` 下所有 `bolt_serial_num` 不能重复，跨 side 也冲突 |
| 点检绑定约束 | `inspection_task_binding.bound_task_id` 必须指向 `is_inspection=0` 的任务 |
| 追溯码唯一 | 同一个 task 最多一条 `rule_type=PRODUCT_TRACE` 的条码规则 |
| 图片大小 | 单张图片 ≤ 5MB，上传时校验 |

### 级联删除

SQLite 无外键约束，应用层（Service）负责级联行为。使用**物理删除**（`removeById` / `lambdaUpdate().remove()`），查询时手动过滤 `deleted=0`。

`ProductTaskService.cascadeDelete(taskId)` @Transactional 时的级联路径：

| 被级联的表 | 删除方式 |
|---|---|
| `product_side` | 先调用 `sideService.deleteBoltsBySideIds(sideIds)` 批量删除子螺栓及关联，再 `removeByIds(sideIds)` |
| `product_bolt` | 通过 `deleteBoltsBySideIds`：先收集 boltIds，再批量删除 `bolt_device_binding` 和 `bolt_parts_barcode`，最后 `removeByIds(boltIds)` |
| `task_prerequisite` | `lambdaUpdate().eq(taskId).or().eq(prerequisiteTaskId).remove()` |
| `inspection_task_binding` | `lambdaUpdate().eq(inspectionTaskId).or().eq(boundTaskId).remove()` |
| `bar_code_matching_rule` | `lambdaUpdate().eq(productTaskId).remove()` |

`ProductSideService.cascadeDelete(sideId)` 单独删除 side 时级联删除其下所有 bolt。
`ProductBoltService.cascadeDelete(boltId)` 单独删除 bolt 时级联删除其 `bolt_device_binding` 和 `bolt_parts_barcode`。

### Controller

| Controller | 包路径 | 映射路径 | 职责 |
|---|---|---|---|---|
| `ProductTaskController` | `controller/ProductTaskController.java` | `api/tasks` | Task CRUD（`GET` 列表、`GET /{id}` 详情、`POST` 创建、`PUT /{id}` 更新、`DELETE /{id}` 删除）、前置依赖管理（`POST /{taskId}/prerequisites`、`GET /{taskId}/prerequisites`、`DELETE /{taskId}/prerequisites/{id}`）、点检绑定管理（`POST /{inspectionTaskId}/inspection-bindings`、`GET /{taskId}/inspection-bindings`、`DELETE /{taskId}/inspection-bindings/{id}`）、条码规则管理（`POST /{taskId}/barcode-rules`、`GET /{taskId}/barcode-rules`、`DELETE /{taskId}/barcode-rules/{id}`） |
| `ProductSideController` | `controller/ProductSideController.java` | `api/sides` | Side CRUD（`GET ?taskId=` 列表、`GET /{id}` 详情、`POST` 创建、`PUT /{id}` 更新、`DELETE /{id}` 删除）、图片下载（`GET /{sideId}/image`）、图片上传（`PUT /{sideId}/image` 原图、`PUT /{sideId}/image/rendered` 成品图、`PUT /{sideId}/image/thumbnail` 缩略图） |
| `ProductBoltController` | `controller/ProductBoltController.java` | `api/bolts` | Bolt CRUD（`GET ?sideId=` 列表、`POST` 创建、`PUT /{id}` 更新、`DELETE /{id}` 删除） |

Controller 路径前缀 `/api`（`@RequestMapping("api/...")`）。所有端点遵循 RESTful HTTP 方法约定（GET 查询、POST 创建、PUT 更新、DELETE 删除），返回同步 `ResponseEntity`。

### 图片接口

ProductSide 的三个 BLOB 字段不通过普通 DTO CRUD 传输。需要独立的图片上传/下载接口：

| 接口 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 获取图片 | GET | `/api/sides/{sideId}/image` | `?type=original\|rendered\|thumbnail` 参数选择 BLOB 类型；通过 `ImageType.fromValue()` 解析；使用服务层 BLOB 读取方法 `getImageData` |
| 上传原图 | PUT | `/api/sides/{sideId}/image` | multipart, 校验 ≤5MB + 文件类型, 使用 `updateImageData` 写入 |
| 上传成品图 | PUT | `/api/sides/{sideId}/image/rendered` | multipart, 使用 `updateRenderedImageData` 写入 |
| 上传缩略图 | PUT | `/api/sides/{sideId}/image/thumbnail` | multipart, 使用 `updateThumbnailData` 写入 |

### 文件清单

| # | 文件 | 路径 |
|---|---|---|
| 1 | SQL 迁移 x3 | `src/main/resources/db/migration/V1.0.{8,9,10}__*.sql` |
| 2 | Entity x8 | `src/main/java/com/tightening/entity/*.java` |
| 3 | DTO x8 | `src/main/java/com/tightening/dto/*.java` |
| 4 | Mapper x8 | `src/main/java/com/tightening/mapper/*.java` |
| 5 | Service x8 | `src/main/java/com/tightening/service/*.java` |
| 6 | 校验器 | `src/main/java/com/tightening/service/TaskConfigValidator.java` |
| 7 | Controller x3 | `src/main/java/com/tightening/controller/ProductTaskController.java` 等 |
| 8 | 枚举 x5 | `src/main/java/com/tightening/constant/*.java` |

总计 44 个文件。
