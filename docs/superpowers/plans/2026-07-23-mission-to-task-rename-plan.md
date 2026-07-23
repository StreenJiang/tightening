# Mission → Task 全局重命名 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将项目中所有 "mission" 标识符重命名为 "task"，包括 Java 类名/方法名/变量名/字符串/注释、API 路径、数据库表/列/索引、文档文件名和内容。

**Architecture:** 分四阶段：1) Flyway 数据库迁移（独立）、2) Java 文件重命名+内容替换（核心批量操作）、3) 文档重命名+内容更新、4) 构建验证。Java 内容替换使用 `sed` 从最长→最短匹配顺序执行，避免双重替换。

**Tech Stack:** sed, git mv, Maven, SQLite, Flyway

## Global Constraints

- "task" 全部替换为 "task"，不留任何残余（匹配大小写：Mission → Task, task → task, MISSION → TASK）
- 已有 Flyway 迁移文件不修改（历史不可变）
- `EnabledStatus` 枚举不含 "task"，不修改
- 替换必须从最长字符串到最短字符串顺序执行，防止部分匹配导致错误替换

---

### Task 1: Flyway 数据库迁移 V1.0.21

**Files:**
- Create: `src/main/resources/db/migration/V1.0.21__rename_task_to_task.sql`

**Interfaces:**
- Consumes: 无
- Produces: 数据库表名/列名/索引名从 task 变为 task

- [ ] **Step 1: 创建迁移文件**

```sql
-- 表重命名（4 张）
ALTER TABLE product_task RENAME TO product_task;
ALTER TABLE task_record RENAME TO task_record;
ALTER TABLE task_prerequisite RENAME TO task_prerequisite;
ALTER TABLE inspection_task_binding RENAME TO inspection_task_binding;

-- 列重命名 — task_record
ALTER TABLE task_record RENAME COLUMN task_result TO task_result;

-- 列重命名 — task_prerequisite
ALTER TABLE task_prerequisite RENAME COLUMN task_id TO task_id;
ALTER TABLE task_prerequisite RENAME COLUMN prerequisite_task_id TO prerequisite_task_id;

-- 列重命名 — inspection_task_binding
ALTER TABLE inspection_task_binding RENAME COLUMN inspection_task_id TO inspection_task_id;
ALTER TABLE inspection_task_binding RENAME COLUMN bound_task_id TO bound_task_id;

-- 列重命名 — FK 关联列
ALTER TABLE product_side RENAME COLUMN product_task_id TO product_task_id;
ALTER TABLE bar_code_matching_rule RENAME COLUMN product_task_id TO product_task_id;
ALTER TABLE tightening_data RENAME COLUMN task_record_id TO task_record_id;
ALTER TABLE curve_data RENAME COLUMN task_record_id TO task_record_id;
ALTER TABLE export_task RENAME COLUMN task_record_id TO task_record_id;

-- 索引重建（SQLite 不支持 RENAME INDEX，需 DROP + CREATE）
DROP INDEX IF EXISTS idx_product_task_name;
CREATE UNIQUE INDEX idx_product_task_name ON product_task(name) WHERE deleted = 0;

DROP INDEX IF EXISTS idx_product_side_product_task_id;
CREATE INDEX idx_product_side_product_task_id ON product_side(product_task_id);

DROP INDEX IF EXISTS idx_task_prerequisite_task_id;
CREATE INDEX idx_task_prerequisite_task_id ON task_prerequisite(task_id);

DROP INDEX IF EXISTS idx_task_prerequisite_prerequisite_task_id;
CREATE INDEX idx_task_prerequisite_prerequisite_task_id ON task_prerequisite(prerequisite_task_id);

DROP INDEX IF EXISTS idx_inspection_task_binding_inspection_task_id;
CREATE INDEX idx_inspection_task_binding_inspection_task_id ON inspection_task_binding(inspection_task_id);

DROP INDEX IF EXISTS idx_inspection_task_binding_bound_task_id;
CREATE INDEX idx_inspection_task_binding_bound_task_id ON inspection_task_binding(bound_task_id);

DROP INDEX IF EXISTS idx_bar_code_matching_rule_product_task_id;
CREATE INDEX idx_bar_code_matching_rule_product_task_id ON bar_code_matching_rule(product_task_id);
```

- [ ] **Step 2: 验证迁移文件语法**

```bash
sqlite3 ~/tightening_system/tightening.db < src/main/resources/db/migration/V1.0.21__rename_task_to_task.sql && echo "OK"
```

Expected: `OK`

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/db/migration/V1.0.21__rename_task_to_task.sql
git commit -m "feat: rename task tables/columns/indexes to task (V1.0.21)"
```

---

### Task 2: Java 文件重命名（34 个文件）

**Files:**
- Rename: 22 个主源文件 + 12 个测试文件（完整列表见下）

**Interfaces:**
- Consumes: 无（纯文件重命名）
- Produces: 新文件名就位，后续 sed 替换在重命名后的文件上执行

- [ ] **Step 1: 主源文件重命名（22 个 git mv）**

```bash
BASE="src/main/java/com/tightening"

# entity (4)
git mv $BASE/entity/ProductTask.java $BASE/entity/ProductTask.java
git mv $BASE/entity/TaskRecord.java $BASE/entity/TaskRecord.java
git mv $BASE/entity/TaskPrerequisite.java $BASE/entity/TaskPrerequisite.java
git mv $BASE/entity/InspectionTaskBinding.java $BASE/entity/InspectionTaskBinding.java

# mapper (4)
git mv $BASE/mapper/ProductTaskMapper.java $BASE/mapper/ProductTaskMapper.java
git mv $BASE/mapper/TaskRecordMapper.java $BASE/mapper/TaskRecordMapper.java
git mv $BASE/mapper/TaskPrerequisiteMapper.java $BASE/mapper/TaskPrerequisiteMapper.java
git mv $BASE/mapper/InspectionTaskBindingMapper.java $BASE/mapper/InspectionTaskBindingMapper.java

# service (5)
git mv $BASE/service/ProductTaskService.java $BASE/service/ProductTaskService.java
git mv $BASE/service/TaskRecordService.java $BASE/service/TaskRecordService.java
git mv $BASE/service/TaskPrerequisiteService.java $BASE/service/TaskPrerequisiteService.java
git mv $BASE/service/InspectionTaskBindingService.java $BASE/service/InspectionTaskBindingService.java
git mv $BASE/service/TaskConfigValidator.java $BASE/service/TaskConfigValidator.java

# controller (2)
git mv $BASE/controller/ProductTaskController.java $BASE/controller/ProductTaskController.java
git mv $BASE/controller/TaskLifecycleController.java $BASE/controller/TaskLifecycleController.java

# dto (3)
git mv $BASE/dto/ProductTaskDTO.java $BASE/dto/ProductTaskDTO.java
git mv $BASE/dto/ProductTaskDetailDTO.java $BASE/dto/ProductTaskDetailDTO.java
git mv $BASE/dto/TaskStatus.java $BASE/dto/TaskStatus.java

# constant (1)
git mv $BASE/constant/TaskResult.java $BASE/constant/TaskResult.java

# lifecycle (3)
git mv $BASE/lifecycle/TaskContext.java $BASE/lifecycle/TaskContext.java
git mv $BASE/lifecycle/TaskOrchestrator.java $BASE/lifecycle/TaskOrchestrator.java
git mv $BASE/lifecycle/capability/CreateTaskRecord.java $BASE/lifecycle/capability/CreateTaskRecord.java
```

- [ ] **Step 2: 测试文件重命名（12 个 git mv）**

```bash
BASE="src/test/java/com/tightening"

git mv $BASE/constant/TaskResultTest.java $BASE/constant/TaskResultTest.java
git mv $BASE/controller/TaskLifecycleControllerTest.java $BASE/controller/TaskLifecycleControllerTest.java
git mv $BASE/controller/ProductTaskControllerTest.java $BASE/controller/ProductTaskControllerTest.java
git mv $BASE/dto/ProductTaskDTOTest.java $BASE/dto/ProductTaskDTOTest.java
git mv $BASE/entity/InspectionTaskBindingTest.java $BASE/entity/InspectionTaskBindingTest.java
git mv $BASE/entity/TaskPrerequisiteTest.java $BASE/entity/TaskPrerequisiteTest.java
git mv $BASE/entity/ProductTaskTest.java $BASE/entity/ProductTaskTest.java
git mv $BASE/lifecycle/capability/CreateTaskRecordTest.java $BASE/lifecycle/capability/CreateTaskRecordTest.java
git mv $BASE/lifecycle/TaskContextLockReasonsTest.java $BASE/lifecycle/TaskContextLockReasonsTest.java
git mv $BASE/lifecycle/TaskContextTest.java $BASE/lifecycle/TaskContextTest.java
git mv $BASE/lifecycle/TaskOrchestratorTest.java $BASE/lifecycle/TaskOrchestratorTest.java
git mv $BASE/service/TaskConfigValidatorTest.java $BASE/service/TaskConfigValidatorTest.java
```

- [ ] **Step 3: 验证文件重命名完成**

```bash
find src -name "*[Mm]ission*" -type f
```

Expected: 空输出（除 Flyway 迁移文件外）

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "refactor: rename task Java files to task (34 files)"
```

---

### Task 3: Java 内容替换（所有 Java 文件中的标识符、字符串、注释）

**Files:**
- Modify: 所有 `src/main/java/**/*.java` 和 `src/test/java/**/*.java`（约 70 个文件）

**Interfaces:**
- Consumes: Task 2 重命名后的所有文件
- Produces: 所有 Java 文件中不再出现 "task"

**替换顺序规则：** 必须从最长/最具体的字符串开始替换，防止短路匹配。例如先替换 `TaskRecord` 再替换 `Task`。

- [ ] **Step 1: 执行 PascalCase 类型名替换（最长→最短）**

```bash
# 需要处理的文件范围
JAVA_FILES=$(find src -name "*.java" -type f)

# PascalCase — 复合类型名先替换（最长优先）
sed -i '' 's/InspectionTaskBinding/InspectionTaskBinding/g' $JAVA_FILES
sed -i '' 's/TaskConfigValidator/TaskConfigValidator/g' $JAVA_FILES
sed -i '' 's/TaskPrerequisiteService/TaskPrerequisiteService/g' $JAVA_FILES
sed -i '' 's/TaskLifecycleController/TaskLifecycleController/g' $JAVA_FILES
sed -i '' 's/ProductTaskDetailDTO/ProductTaskDetailDTO/g' $JAVA_FILES
sed -i '' 's/ProductTaskController/ProductTaskController/g' $JAVA_FILES
sed -i '' 's/ProductTaskService/ProductTaskService/g' $JAVA_FILES
sed -i '' 's/ProductTaskMapper/ProductTaskMapper/g' $JAVA_FILES
sed -i '' 's/ProductTaskDTO/ProductTaskDTO/g' $JAVA_FILES

sed -i '' 's/TaskRecordService/TaskRecordService/g' $JAVA_FILES
sed -i '' 's/TaskRecordMapper/TaskRecordMapper/g' $JAVA_FILES

sed -i '' 's/TaskPrerequisiteMapper/TaskPrerequisiteMapper/g' $JAVA_FILES
sed -i '' 's/TaskPrerequisite/TaskPrerequisite/g' $JAVA_FILES

sed -i '' 's/TaskOrchestrator/TaskOrchestrator/g' $JAVA_FILES
sed -i '' 's/CreateTaskRecord/CreateTaskRecord/g' $JAVA_FILES
sed -i '' 's/InterruptTask/InterruptTask/g' $JAVA_FILES

sed -i '' 's/TaskEnabledStatus/TaskEnabledStatus/g' $JAVA_FILES
sed -i '' 's/TaskContext/TaskContext/g' $JAVA_FILES
sed -i '' 's/TaskStatus/TaskStatus/g' $JAVA_FILES
sed -i '' 's/TaskResult/TaskResult/g' $JAVA_FILES
sed -i '' 's/TaskRecord/TaskRecord/g' $JAVA_FILES
sed -i '' 's/ProductTask/ProductTask/g' $JAVA_FILES

# 最后：孤立的 Mission → Task（可能出现在注释或 import 已处理完的残余中）
sed -i '' 's/Task/Task/g' $JAVA_FILES
```

- [ ] **Step 2: 执行 camelCase 标识符替换（最长→最短）**

```bash
JAVA_FILES=$(find src -name "*.java" -type f)

# 方法名
sed -i '' 's/selectFirstSidePerTask/selectFirstSidePerTask/g' $JAVA_FILES
sed -i '' 's/listByInspectionTaskId/listByInspectionTaskId/g' $JAVA_FILES
sed -i '' 's/listByTaskRecordId/listByTaskRecordId/g' $JAVA_FILES
sed -i '' 's/isInspectionTask/isInspectionTask/g' $JAVA_FILES
sed -i '' 's/listByTaskId/listByTaskId/g' $JAVA_FILES
sed -i '' 's/wrongTask/wrongTask/g' $JAVA_FILES
sed -i '' 's/saveTask/saveTask/g' $JAVA_FILES

# 变量/字段名 - 多词组合先替换
sed -i '' 's/taskRecordService/taskRecordService/g' $JAVA_FILES
sed -i '' 's/prerequisiteTaskId/prerequisiteTaskId/g' $JAVA_FILES
sed -i '' 's/inspectionTaskId/inspectionTaskId/g' $JAVA_FILES
sed -i '' 's/productTaskId/productTaskId/g' $JAVA_FILES
sed -i '' 's/boundTaskIds/boundTaskIds/g' $JAVA_FILES
sed -i '' 's/boundTaskId/boundTaskId/g' $JAVA_FILES
sed -i '' 's/taskRecordId/taskRecordId/g' $JAVA_FILES
sed -i '' 's/taskRecord/taskRecord/g' $JAVA_FILES
sed -i '' 's/taskResult/taskResult/g' $JAVA_FILES
sed -i '' 's/taskService/taskService/g' $JAVA_FILES
sed -i '' 's/taskData/taskData/g' $JAVA_FILES
sed -i '' 's/taskIds/taskIds/g' $JAVA_FILES
sed -i '' 's/taskId/taskId/g' $JAVA_FILES
```

- [ ] **Step 3: 替换字符串字面量和 API 路径**

```bash
JAVA_FILES=$(find src -name "*.java" -type f)

# 字符串常量
sed -i '' 's/"WRONG_MISSION"/"WRONG_TASK"/g' $JAVA_FILES
sed -i '' 's/"CreateTaskRecord"/"CreateTaskRecord"/g' $JAVA_FILES

# ExportData payload Map key 字面量
sed -i '' 's/"taskId"/"taskId"/g' $JAVA_FILES
sed -i '' 's/"taskRecordId"/"taskRecordId"/g' $JAVA_FILES
sed -i '' 's/"taskResult"/"taskResult"/g' $JAVA_FILES

# Excel/TXT 列头
sed -i '' "s/\"taskRecordId\"/\"taskRecordId\"/g" $JAVA_FILES

# @TableName 注解中的表名
sed -i '' 's/"product_task"/"product_task"/g' $JAVA_FILES
sed -i '' 's/"task_record"/"task_record"/g' $JAVA_FILES
sed -i '' 's/"task_prerequisite"/"task_prerequisite"/g' $JAVA_FILES
sed -i '' 's/"inspection_task_binding"/"inspection_task_binding"/g' $JAVA_FILES

# ProductSideMapper 原生 SQL 中的列名
sed -i '' 's/product_task_id/product_task_id/g' $JAVA_FILES

# API 路径
sed -i '' 's|"api/tasks"|"api/tasks"|g' $JAVA_FILES
sed -i '' 's|"/api/tasks"|"/api/tasks"|g' $JAVA_FILES
sed -i '' 's|api/tasks|api/tasks|g' $JAVA_FILES
```

- [ ] **Step 4: 替换日志消息和注释中的 "task"（纯文本，不影响代码）**

```bash
JAVA_FILES=$(find src -name "*.java" -type f)

# 日志消息和注释中的 task → task（注意保留边界以避免误替换已处理的内容）
# 空格包围的独立单词
sed -i '' 's/active task/active task/g' $JAVA_FILES
sed -i '' 's/task context/task context/g' $JAVA_FILES
sed -i '' 's/task data/task data/g' $JAVA_FILES
sed -i '' 's/task {}/task {}/g' $JAVA_FILES
sed -i '' 's/for task/for task/g' $JAVA_FILES
sed -i '' 's/of task/of task/g' $JAVA_FILES
sed -i '' 's/Task {}/Task {}/g' $JAVA_FILES
sed -i '' 's/"Task/"Task/g' $JAVA_FILES
sed -i '' 's/Create task/Create task/g' $JAVA_FILES
sed -i '' 's/Update task/Update task/g' $JAVA_FILES

# 注释中的中文描述
sed -i '' 's/中断当前 Task/中断当前 Task/g' $JAVA_FILES
sed -i '' 's/当前 Task/当前 Task/g' $JAVA_FILES
sed -i '' 's/活跃 Task/活跃 Task/g' $JAVA_FILES

# 最后兜底：替换所有残余的小写 task 和大写 Task
# 由于已从最长匹配开始替换，且项目中无 "transtask" 等含 task 子串的词，此操作安全
sed -i '' 's/task/task/g' $JAVA_FILES
sed -i '' 's/Task/Task/g' $JAVA_FILES
```

- [ ] **Step 5: 验证 Java 文件中无残留 "task"**

```bash
# 应只输出 Flyway 迁移文件名和 migration 这个单词本身
grep -rn "task\|Task\|MISSION" src --include="*.java"
```

Expected: 空输出

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "refactor: replace all task identifiers, strings, and comments with task in Java"
```

---

### Task 4: 文档重命名和内容更新

**Files:**
- Rename: docs 下 15 个含 "task" 的 .md 文件
- Modify: ~20 个不含 "task" 文件名但内容引用 "task" 的 .md 文件
- Modify: `README.md`

**Interfaces:**
- Consumes: 无（文档独立）
- Produces: 所有文档文件名和内容中不再出现 "task"

- [ ] **Step 1: 文档文件重命名（15 个）**

```bash
# 架构/参考/测试文档
git mv docs/2026-06-21-task-lifecycle-architecture-design.md docs/2026-06-21-task-lifecycle-architecture-design.md
git mv docs/adr/0002-ng-first-in-task-record.md docs/adr/0002-ng-first-in-task-record.md
git mv docs/references/2026-06-21-task-lifecycle-analysis.md docs/references/2026-06-21-task-lifecycle-analysis.md
git mv docs/testing/task-config-api-test-guide.md docs/testing/task-config-api-test-guide.md

# superpowers specs
git mv docs/superpowers/specs/2026-06-18-task-record-design.md docs/superpowers/specs/2026-06-18-task-record-design.md
git mv docs/superpowers/specs/2026-06-24-task-config-tables-design.md docs/superpowers/specs/2026-06-24-task-config-tables-design.md
git mv docs/superpowers/specs/2026-07-17-task-name-check-search-design.md docs/superpowers/specs/2026-07-17-task-name-check-search-design.md
git mv docs/superpowers/specs/2026-07-19-task-save-response-design.md docs/superpowers/specs/2026-07-19-task-save-response-design.md
git mv docs/superpowers/specs/2026-07-19-task-save-unified-design.md docs/superpowers/specs/2026-07-19-task-save-unified-design.md

# superpowers plans
git mv docs/superpowers/plans/2026-06-18-task-record-plan.md docs/superpowers/plans/2026-06-18-task-record-plan.md
git mv docs/superpowers/plans/2026-06-24-task-config-tables.md docs/superpowers/plans/2026-06-24-task-config-tables.md
git mv docs/superpowers/plans/2026-07-17-task-name-check-search-plan.md docs/superpowers/plans/2026-07-17-task-name-check-search-plan.md
git mv docs/superpowers/plans/2026-07-19-task-save-response-plan.md docs/superpowers/plans/2026-07-19-task-save-response-plan.md
git mv docs/superpowers/plans/2026-07-19-task-save-unified-plan.md docs/superpowers/plans/2026-07-19-task-save-unified-plan.md

# 设计文档和计划文档本身不重命名（文件名描述的就是 task-to-task 这个操作）
```

- [ ] **Step 2: 文档内容替换（所有 .md 文件中的 task → task）**

```bash
DOC_FILES=$(find docs -name "*.md" -type f)

# PascalCase
sed -i '' 's/InspectionTaskBinding/InspectionTaskBinding/g' $DOC_FILES
sed -i '' 's/ProductTask/ProductTask/g' $DOC_FILES
sed -i '' 's/TaskRecord/TaskRecord/g' $DOC_FILES
sed -i '' 's/TaskPrerequisite/TaskPrerequisite/g' $DOC_FILES
sed -i '' 's/TaskContext/TaskContext/g' $DOC_FILES
sed -i '' 's/TaskOrchestrator/TaskOrchestrator/g' $DOC_FILES
sed -i '' 's/TaskConfigValidator/TaskConfigValidator/g' $DOC_FILES
sed -i '' 's/TaskLifecycleController/TaskLifecycleController/g' $DOC_FILES
sed -i '' 's/TaskResult/TaskResult/g' $DOC_FILES
sed -i '' 's/TaskStatus/TaskStatus/g' $DOC_FILES
sed -i '' 's/Task/Task/g' $DOC_FILES

# camelCase
sed -i '' 's/taskId/taskId/g' $DOC_FILES
sed -i '' 's/taskRecord/taskRecord/g' $DOC_FILES
sed -i '' 's/task/task/g' $DOC_FILES

# README.md
sed -i '' 's/task/task/g' README.md
sed -i '' 's/Task/Task/g' README.md
```

- [ ] **Step 3: 验证文档中无残留 "task"**

```bash
grep -rn "task\|Task" docs README.md --include="*.md" | grep -v "mission-to-task-rename" | grep -v "flyway_schema_history"
```

Expected: 空输出（除了设计文档自身的文件名）

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "docs: rename task to task in all documentation"
```

---

### Task 5: 构建和测试验证

**Files:**
- 无新建/修改（验证任务）

**Interfaces:**
- Consumes: Task 1-4 的所有变更
- Produces: 确认所有修改正确，编译通过，测试通过

- [ ] **Step 1: Maven 编译验证**

```bash
mvn compile -q 2>&1
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行全部测试**

```bash
mvn test 2>&1
```

Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 3: 检查是否有任何遗漏的 "task"（非 Flyway 历史文件）**

```bash
grep -rn "task\|Task\|MISSION" src docs README.md --include="*.java" --include="*.md" --include="*.yml" --include="*.yaml" --include="*.xml" --include="*.properties" | grep -v "db/migration/V1" | grep -v "mission-to-task-rename"
```

Expected: 空输出

- [ ] **Step 4: 最终提交**

```bash
git add -A
git commit -m "chore: verify task to task rename - build and tests pass"
```

---

### 回滚方案

如需回滚：`git revert` 从后往前逐个提交即可。数据库需单独处理（Flyway 不支持自动回滚，需手动反向重命名）。
