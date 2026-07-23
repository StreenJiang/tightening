# Task → Task 全局重命名设计

## 动机

项目初期将"拧紧任务"命名为 "task"，命名不准确。全局重命名为 "task"，不留死角。

## 范围

### 1. Java 文件重命名（34 个）

**主代码（22 个）：**

| 旧文件 | 新文件 |
|--------|--------|
| `entity/ProductTask.java` | `entity/ProductTask.java` |
| `entity/TaskRecord.java` | `entity/TaskRecord.java` |
| `entity/TaskPrerequisite.java` | `entity/TaskPrerequisite.java` |
| `entity/InspectionTaskBinding.java` | `entity/InspectionTaskBinding.java` |
| `mapper/ProductTaskMapper.java` | `mapper/ProductTaskMapper.java` |
| `mapper/TaskRecordMapper.java` | `mapper/TaskRecordMapper.java` |
| `mapper/TaskPrerequisiteMapper.java` | `mapper/TaskPrerequisiteMapper.java` |
| `mapper/InspectionTaskBindingMapper.java` | `mapper/InspectionTaskBindingMapper.java` |
| `service/ProductTaskService.java` | `service/ProductTaskService.java` |
| `service/TaskRecordService.java` | `service/TaskRecordService.java` |
| `service/TaskPrerequisiteService.java` | `service/TaskPrerequisiteService.java` |
| `service/InspectionTaskBindingService.java` | `service/InspectionTaskBindingService.java` |
| `service/TaskConfigValidator.java` | `service/TaskConfigValidator.java` |
| `controller/ProductTaskController.java` | `controller/ProductTaskController.java` |
| `controller/TaskLifecycleController.java` | `controller/TaskLifecycleController.java` |
| `dto/ProductTaskDTO.java` | `dto/ProductTaskDTO.java` |
| `dto/ProductTaskDetailDTO.java` | `dto/ProductTaskDetailDTO.java` |
| `dto/TaskStatus.java` | `dto/TaskStatus.java` |
| `constant/TaskResult.java` | `constant/TaskResult.java` |
| `lifecycle/TaskContext.java` | `lifecycle/TaskContext.java` |
| `lifecycle/TaskOrchestrator.java` | `lifecycle/TaskOrchestrator.java` |
| `lifecycle/capability/CreateTaskRecord.java` | `lifecycle/capability/CreateTaskRecord.java` |

**测试代码（12 个）：** 对应上述主代码的测试文件同步重命名。

### 2. 代码内标识符重命名（~35 个额外文件，~885 处引用）

这些文件文件名不含 "task"，但内部引用了 task 类型/字段/变量：

**关键标识符映射：**

| 旧 | 新 |
|----|----|
| `taskId` | `taskId` |
| `taskIds` | `taskIds` |
| `taskRecord` | `taskRecord` |
| `taskRecordId` | `taskRecordId` |
| `taskResult` | `taskResult` |
| `taskData` | `taskData` |
| `taskService` | `taskService` |
| `taskRecordService` | `taskRecordService` |
| `productTaskId` | `productTaskId` |
| `prerequisiteTaskId` | `prerequisiteTaskId` |
| `inspectionTaskId` | `inspectionTaskId` |
| `boundTaskId` / `boundTaskIds` | `boundTaskId` / `boundTaskIds` |
| `saveTask()` | `saveTask()` |
| `listByTaskId()` | `listByTaskId()` |
| `listByTaskRecordId()` | `listByTaskRecordId()` |
| `selectFirstSidePerTask()` | `selectFirstSidePerTask()` |
| `listByInspectionTaskId()` | `listByInspectionTaskId()` |
| `isInspectionTask()` | `isInspectionTask()` |
| `InterruptTask` (record) | `InterruptTask` |
| `wrongTask()` | `wrongTask()` |

**字符串字面量：**

- `"WRONG_MISSION"` → `"WRONG_TASK"`
- `"CreateTaskRecord"` → `"CreateTaskRecord"`
- `"taskId"` / `"taskRecordId"` / `"taskResult"` (ExportData payload keys)
- `"taskRecordId"` (Excel/TXT 列头)
- 所有日志消息和注释中的 "task"

### 3. API 路径

- `api/tasks` → `api/tasks`（两个 Controller 的 `@RequestMapping`）

### 4. 数据库

新增 Flyway 迁移 `V1.0.21__rename_task_to_task.sql`：

**表重命名（4 张）：**
```sql
ALTER TABLE product_task RENAME TO product_task;
ALTER TABLE task_record RENAME TO task_record;
ALTER TABLE task_prerequisite RENAME TO task_prerequisite;
ALTER TABLE inspection_task_binding RENAME TO inspection_task_binding;
```

**列重命名（7 个）：**
```sql
ALTER TABLE task_record RENAME COLUMN task_result TO task_result;
ALTER TABLE task_prerequisite RENAME COLUMN task_id TO task_id;
ALTER TABLE task_prerequisite RENAME COLUMN prerequisite_task_id TO prerequisite_task_id;
ALTER TABLE inspection_task_binding RENAME COLUMN inspection_task_id TO inspection_task_id;
ALTER TABLE inspection_task_binding RENAME COLUMN bound_task_id TO bound_task_id;
ALTER TABLE product_side RENAME COLUMN product_task_id TO product_task_id;
ALTER TABLE bar_code_matching_rule RENAME COLUMN product_task_id TO product_task_id;
```

**涉及 task_record_id 的表（3 个），因 FK 关联列名由字段名自动推导：**
```sql
ALTER TABLE tightening_data RENAME COLUMN task_record_id TO task_record_id;
ALTER TABLE curve_data RENAME COLUMN task_record_id TO task_record_id;
ALTER TABLE export_task RENAME COLUMN task_record_id TO task_record_id;
```

**索引重命名（7 个）：**
```sql
-- 在 SQLite 中索引随表重命名而自动迁移（索引与表关联），但显式命名的索引需要重建
-- 实际上 SQLite 在重命名表时索引会自动跟随，列重命名需要重建索引
```

### 5. 文档（14 个 .md 文件）

文件名和内容中的 "task" 全部替换为 "task"：

- `CLAUDE.md`
- `README.md`
- `docs/api-guide.md`
- `docs/2026-06-21-task-lifecycle-architecture-design.md`
- `docs/adr/0001-tightening-data-snapshots.md`
- `docs/adr/0002-ng-first-in-task-record.md`
- `docs/references/2026-06-21-task-lifecycle-analysis.md`
- `docs/testing/task-config-api-test-guide.md`
- `docs/superpowers/specs/` 下 5 个含 "task" 的文件
- `docs/superpowers/plans/` 下 5 个含 "task" 的文件

## 不在范围内

- `EnabledStatus` 枚举 — 不含 "task"，不改
- SSE、monitor、message、device handler 包 — 不含 "task"
- 已有 Flyway 迁移文件 — 历史不可变，不修改

## 执行策略

**IntelliJ IDEA 重构优先：** 利用 IDE 的 Rename 重构功能处理 Java 类/方法/变量重命名，确保所有引用自动更新。字符串字面量和文档手动替换。
