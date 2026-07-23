# 阶段 0：代码质量完善实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 生命周期引擎实施前，修复当前代码中影响可维护性的问题 —— Service 方法封装、MyBatis-Plus Page 分页、统一 ApiResponse、FK 可空迁移、崩溃恢复字段

**Architecture:** 5 个独立子任务按依赖序执行。0.1-0.3 修改 Controller/Service 层，0.4-0.5 是数据库迁移 + Entity 字段变更。所有变更向后兼容

**Tech Stack:** Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.9, SQLite, Flyway

## Global Constraints

- 不改 DeviceController（DeferredResult 异步模式）和 LoginController（测试桩）
- 不改现有测试文件的断言逻辑
- SQLite 不支持 ALTER COLUMN，需重建表
- MyBatis-Plus 已配置分页插件

---

### Task 1: 0.1 Service 方法封装

**Files:**
- Modify: `src/main/java/com/tightening/service/TaskPrerequisiteService.java`
- Modify: `src/main/java/com/tightening/service/InspectionTaskBindingService.java`
- Modify: `src/main/java/com/tightening/service/BarCodeMatchingRuleService.java`
- Modify: `src/main/java/com/tightening/service/TighteningDataService.java`
- Modify: `src/main/java/com/tightening/service/TaskRecordService.java`
- Modify: `src/main/java/com/tightening/service/ProductBoltService.java`
- Modify: `src/main/java/com/tightening/controller/ProductTaskController.java`

**Interfaces:**
- Produces:
  - `TaskPrerequisiteService.listByTaskId(Long taskId) → List<TaskPrerequisite>`
  - `InspectionTaskBindingService.listByInspectionTaskId(Long inspectionTaskId) → List<InspectionTaskBinding>`
  - `BarCodeMatchingRuleService.listByTaskId(Long taskId) → List<BarCodeMatchingRule>`
  - `TighteningDataService.listByTaskRecordId(Long taskRecordId) → List<TighteningData>`
  - `TaskRecordService.createRecord(Long productTaskId, String productCode, Integer isRework) → TaskRecord`
  - `TaskRecordService.markAsOk(Long recordId) → void`
  - `ProductBoltService.listByTaskId(Long taskId) → List<ProductBolt>`

- [ ] **Step 1: 向空壳 Service 添加查询方法**

`TaskPrerequisiteService`:
```java
import java.util.List;
// 在类体内添加:
public List<TaskPrerequisite> listByTaskId(Long taskId) {
    return lambdaQuery()
            .eq(TaskPrerequisite::getTaskId, taskId)
            .eq(TaskPrerequisite::getDeleted, 0)
            .list();
}
```

`InspectionTaskBindingService`:
```java
import java.util.List;
// 在类体内添加:
public List<InspectionTaskBinding> listByInspectionTaskId(Long inspectionTaskId) {
    return lambdaQuery()
            .eq(InspectionTaskBinding::getInspectionTaskId, inspectionTaskId)
            .eq(InspectionTaskBinding::getDeleted, 0)
            .list();
}
```

`BarCodeMatchingRuleService`:
```java
import java.util.List;
// 在类体内添加:
public List<BarCodeMatchingRule> listByTaskId(Long taskId) {
    return lambdaQuery()
            .eq(BarCodeMatchingRule::getProductTaskId, taskId)
            .eq(BarCodeMatchingRule::getDeleted, 0)
            .list();
}
```

`TighteningDataService`:
```java
import java.util.List;
// 在类体内添加:
public List<TighteningData> listByTaskRecordId(Long taskRecordId) {
    return lambdaQuery()
            .eq(TighteningData::getTaskRecordId, taskRecordId)
            .eq(TighteningData::getDeleted, 0)
            .list();
}
```

- [ ] **Step 2: 向 TaskRecordService 添加激活/完成方法**

```java
import com.tightening.constant.TaskResult;
// 在类体内添加:
public TaskRecord createRecord(Long productTaskId, String productCode, Integer isRework) {
    TaskRecord record = new TaskRecord()
            .setProductTaskId(productTaskId)
            .setProductCode(productCode)
            .setIsRework(isRework)
            .setTaskResult(TaskResult.NG.getCode());
    save(record);
    return record;
}

public void markAsOk(Long recordId) {
    lambdaUpdate().eq(TaskRecord::getId, recordId)
            .set(TaskRecord::getTaskResult, TaskResult.OK.getCode())
            .update();
}
```

- [ ] **Step 3: 向 ProductBoltService 添加按 taskId 查询**

```java
// 在 cascadeDelete 方法之后添加:
public List<ProductBolt> listByTaskId(Long taskId) {
    Set<Long> sideIds = new java.util.HashSet<>(sideService.listSideIdsByTaskId(taskId));
    if (sideIds.isEmpty()) return List.of();
    return lambdaQuery()
            .in(ProductBolt::getProductSideId, sideIds)
            .eq(ProductBolt::getDeleted, 0)
            .orderByAsc(ProductBolt::getBoltSerialNum)
            .list();
}
```

- [ ] **Step 4: 替换 Controller 中的 lambdaQuery 调用**

`ProductTaskController.listPrerequisites()`:
```java
// 替换前:
List<TaskPrerequisite> prerequisites = prerequisiteService.lambdaQuery()
        .eq(TaskPrerequisite::getTaskId, taskId)
        .eq(TaskPrerequisite::getDeleted, 0)
        .list();
// 替换后:
List<TaskPrerequisite> prerequisites = prerequisiteService.listByTaskId(taskId);
```

`ProductTaskController.listInspectionBindings()`:
```java
// 替换前:
List<InspectionTaskBinding> bindings = bindingService.lambdaQuery()
        .eq(InspectionTaskBinding::getInspectionTaskId, taskId)
        .eq(InspectionTaskBinding::getDeleted, 0)
        .list();
// 替换后:
List<InspectionTaskBinding> bindings = bindingService.listByInspectionTaskId(taskId);
```

`ProductTaskController.listBarcodeRules()`:
```java
// 替换前:
List<BarCodeMatchingRule> rules = barcodeRuleService.lambdaQuery()
        .eq(BarCodeMatchingRule::getProductTaskId, taskId)
        .eq(BarCodeMatchingRule::getDeleted, 0)
        .list();
// 替换后:
List<BarCodeMatchingRule> rules = barcodeRuleService.listByTaskId(taskId);
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile
```
Expected: BUILD SUCCESS

---

### Task 2: 0.2 分页改用 MyBatis-Plus Page

**Files:**
- Modify: `src/main/java/com/tightening/service/ProductTaskService.java`
- Modify: `src/main/java/com/tightening/controller/ProductTaskController.java`

**Interfaces:**
- Consumes: MyBatis-Plus `Page` 分页插件（已全局配置）
- Produces: `ProductTaskService.listByPage(int page, int size) → Page<ProductTask>`

- [ ] **Step 1: 向 ProductTaskService 添加分页查询方法**

```java
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
// 在 cascadeDelete 之前添加:
public Page<ProductTask> listByPage(int page, int size) {
    return lambdaQuery()
            .eq(ProductTask::getDeleted, 0)
            .orderByDesc(ProductTask::getId)
            .page(new Page<>(page, size));
}
```

- [ ] **Step 2: 替换 Controller 中的手动 SQL 拼接**

```java
// 替换前:
@GetMapping
public ResponseEntity<List<ProductTaskDTO>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "100") int size) {
    int safePage = Math.min(Math.max(1, page), 1000);
    int safeSize = Math.min(Math.max(1, size), 500);
    List<ProductTask> tasks = taskService.lambdaQuery()
            .eq(ProductTask::getDeleted, 0)
            .orderByDesc(ProductTask::getId)
            .last("LIMIT " + safeSize + " OFFSET " + ((safePage - 1) * safeSize))
            .list();
    return ResponseEntity.ok(Converter.entity2Dto(tasks, ProductTaskDTO::new));
}

// 替换后:
@GetMapping
public ResponseEntity<List<ProductTaskDTO>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "100") int size) {
    var resultPage = taskService.listByPage(page, size);
    return ResponseEntity.ok(Converter.entity2Dto(resultPage.getRecords(), ProductTaskDTO::new));
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile
```
Expected: BUILD SUCCESS

---

### Task 3: 0.3 统一 Controller 响应格式

**Files:**
- Create: `src/main/java/com/tightening/dto/ApiResponse.java`
- Modify: `src/main/java/com/tightening/controller/ProductTaskController.java`
- Modify: `src/main/java/com/tightening/controller/ProductBoltController.java`
- Modify: `src/main/java/com/tightening/controller/ProductSideController.java`
- Modify: `src/main/java/com/tightening/controller/UserAccountInfoController.java`

**Interfaces:**
- Produces: `ApiResponse<T>(int code, String message, T data)` record，静态工厂 `ok(T)`, `ok()`, `fail(String)`

- [ ] **Step 1: 创建 ApiResponse 类**

```java
package com.tightening.dto;

public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "ok", data);
    }

    public static ApiResponse<String> ok() {
        return new ApiResponse<>(200, "ok", null);
    }

    public static ApiResponse<String> fail(String message) {
        return new ApiResponse<>(500, message, null);
    }
}
```

- [ ] **Step 2: 改造 ProductTaskController（17 个方法）**

所有方法返回类型从 `ResponseEntity<X>` 改为 `ResponseEntity<ApiResponse<X>>`，返回值包裹 `ApiResponse.ok()` / `ApiResponse.fail()`。

示例 —— `list()`:
```java
public ResponseEntity<ApiResponse<List<ProductTaskDTO>>> list(...) {
    var resultPage = taskService.listByPage(page, size);
    return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(resultPage.getRecords(), ProductTaskDTO::new)));
}
```

示例 —— `get()`（null → fail）:
```java
public ResponseEntity<ApiResponse<ProductTaskDTO>> get(@PathVariable Long id) {
    ProductTask task = taskService.getById(id);
    if (task == null) return ResponseEntity.ok(ApiResponse.fail("not found"));
    return ResponseEntity.ok(ApiResponse.ok(Converter.entity2Dto(task, ProductTaskDTO::new)));
}
```

示例 —— `delete()`（无数据返回 → ok()）:
```java
public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
    taskService.cascadeDelete(id);
    return ResponseEntity.ok(ApiResponse.ok());
}
```

完整方法签名变更清单:
| 方法 | 旧返回类型 | 新返回类型 |
|------|-----------|-----------|
| `list` | `ResponseEntity<List<ProductTaskDTO>>` | `ResponseEntity<ApiResponse<List<ProductTaskDTO>>>` |
| `get` | `ResponseEntity<ProductTaskDTO>` | `ResponseEntity<ApiResponse<ProductTaskDTO>>` |
| `create` | `ResponseEntity<String>` | `ResponseEntity<ApiResponse<String>>` |
| `update` | `ResponseEntity<String>` | `ResponseEntity<ApiResponse<String>>` |
| `delete` | `ResponseEntity<String>` | `ResponseEntity<ApiResponse<String>>` |
| `addPrerequisite` | `ResponseEntity<String>` | `ResponseEntity<ApiResponse<String>>` |
| `listPrerequisites` | `ResponseEntity<List<TaskPrerequisiteDTO>>` | `ResponseEntity<ApiResponse<List<TaskPrerequisiteDTO>>>` |
| `deletePrerequisite` | `ResponseEntity<String>` | `ResponseEntity<ApiResponse<String>>` |
| `addInspectionBinding` | `ResponseEntity<String>` | `ResponseEntity<ApiResponse<String>>` |
| `listInspectionBindings` | `ResponseEntity<List<InspectionTaskBindingDTO>>` | `ResponseEntity<ApiResponse<List<InspectionTaskBindingDTO>>>` |
| `deleteInspectionBinding` | `ResponseEntity<String>` | `ResponseEntity<ApiResponse<String>>` |
| `addBarcodeRule` | `ResponseEntity<String>` | `ResponseEntity<ApiResponse<String>>` |
| `listBarcodeRules` | `ResponseEntity<List<BarCodeMatchingRuleDTO>>` | `ResponseEntity<ApiResponse<List<BarCodeMatchingRuleDTO>>>` |
| `deleteBarcodeRule` | `ResponseEntity<String>` | `ResponseEntity<ApiResponse<String>>` |

- [ ] **Step 3: 改造 ProductBoltController（4 个方法）**

同样的 `ApiResponse<T>` 包裹模式。

- [ ] **Step 4: 改造 ProductSideController（5 个 CRUD 方法）**

图片接口（`getImage`、`uploadImage`、`uploadRenderedImage`、`uploadThumbnail`）保持 `ResponseEntity<byte[]>` / `ResponseEntity<String>` 不变，不做 ApiResponse 包裹。

- [ ] **Step 5: 改造 UserAccountInfoController（3 个方法）**

`deleteUserById` 返回类型从 `ResponseEntity<Boolean>` 改为 `ResponseEntity<ApiResponse<Boolean>>`。

- [ ] **Step 6: 编译验证**

```bash
mvn compile
```
Expected: BUILD SUCCESS

---

### Task 4: 0.4 task_record_id FK 改为可空

**Files:**
- Create: `src/main/resources/db/migration/V1.0.11__make_task_record_id_nullable.sql`
- Modify: `src/main/java/com/tightening/entity/TighteningData.java:18`
- Modify: `src/main/java/com/tightening/entity/CurveData.java:18`
- Modify: `src/main/java/com/tightening/dto/TighteningDataDTO.java:15`
- Modify: `src/main/java/com/tightening/dto/CurveDataDTO.java:15`

**Interfaces:**
- Produces: `TighteningData.taskRecordId: Long`（可为 null），`CurveData.taskRecordId: Long`（可为 null）

- [ ] **Step 1: 创建 Flyway 迁移 V1.0.11**

SQLite 不支持 ALTER COLUMN，需重建两个表:

```sql
-- tightening_data: NOT NULL → nullable
ALTER TABLE tightening_data RENAME TO tightening_data_old;
CREATE TABLE tightening_data (
    -- 完整列定义，task_record_id INTEGER (无 NOT NULL)
    ...
);
INSERT INTO tightening_data SELECT * FROM tightening_data_old;
DROP TABLE tightening_data_old;

-- curve_data: 同理
ALTER TABLE curve_data RENAME TO curve_data_old;
CREATE TABLE curve_data (
    -- 完整列定义，task_record_id INTEGER (无 NOT NULL)
    ...
);
INSERT INTO curve_data SELECT * FROM curve_data_old;
DROP TABLE curve_data_old;
```

完整 SQL 见 `src/main/resources/db/migration/V1.0.11__make_task_record_id_nullable.sql`。

- [ ] **Step 2: 更新 Entity 字段类型**

```java
// TighteningData.java: long → Long
private Long taskRecordId;

// CurveData.java: long → Long
private Long taskRecordId;
```

- [ ] **Step 3: 更新 DTO 字段类型**

```java
// TighteningDataDTO.java: long → Long
private Long taskRecordId;

// CurveDataDTO.java: long → Long
private Long taskRecordId;
```

- [ ] **Step 4: 编译验证**

```bash
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 5: 运行测试**

```bash
mvn test
```
Expected: 现有测试全部通过（测试中已使用 `1L` Long 字面量，无需修改）

---

### Task 5: 0.5 task_record 崩溃恢复字段

**Files:**
- Create: `src/main/resources/db/migration/V1.0.12__add_task_record_crash_recovery.sql`
- Modify: `src/main/java/com/tightening/entity/TaskRecord.java:21-22`

**Interfaces:**
- Produces: `TaskRecord.contextSnapshot: String`（JSON，关键转换点的 Context 摘要），`TaskRecord.faultMessage: String`（崩溃异常信息）

- [ ] **Step 1: 创建 Flyway 迁移 V1.0.12**

```sql
ALTER TABLE task_record ADD COLUMN context_snapshot TEXT;
ALTER TABLE task_record ADD COLUMN fault_message TEXT;
```

SQLite 支持 ADD COLUMN，直接执行。

- [ ] **Step 2: 更新 TaskRecord Entity**

```java
// TaskRecord.java，在 taskResult 之后添加:
private String contextSnapshot;
private String faultMessage;
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile
```
Expected: BUILD SUCCESS

---

## Verification

全部 5 个 Task 完成后：

```bash
# 编译
mvn compile

# 运行全部测试
mvn test

# 启动应用验证 Flyway 迁移
mvn spring-boot:run
# 观察日志: V1.0.11 和 V1.0.12 迁移成功执行
```

## 变更统计

| 类型 | 数量 |
|------|------|
| 新建文件 | 3（ApiResponse.java, V1.0.11, V1.0.12） |
| 修改文件 | 16 |
| 新增代码行 | ~143 |
| 删除代码行 | ~79 |
