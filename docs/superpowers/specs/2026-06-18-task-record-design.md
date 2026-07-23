# Task Record 表设计

## 概述

新增 `task_record` 表，作为拧紧任务的父级记录。一次 task 对应一个产品的完整拧紧流程，包含多个螺栓拧紧步骤（`tightening_data`）。

### 表关系

```
product_task (本次不做)
  └── task_record (本次新建)
        └── tightening_data (已存在，task_record_id 引用本表)
              └── curve_data (已存在)
```

## 数据库迁移

**文件**: `V1.0.7__add_table_task_record.sql`

```sql
CREATE TABLE task_record (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    product_task_id  INTEGER,
    product_code        TEXT,
    is_rework           INTEGER DEFAULT 0,
    task_result      INTEGER,
    deleted             INTEGER DEFAULT 0,
    creator_id          INTEGER,
    modifier_id         INTEGER,
    create_time         TEXT,
    modify_time         TEXT
);
```

- `product_task_id`: 逻辑外键，指向未来 `product_task` 表
- `product_code`: 产品码
- `is_rework`: 是否返工（0=正常, 1=返工）
- `task_result`: 任务结果（0=NG, 1=OK），对齐 `TighteningStatus`。任务激活时立即写入 NG，所有螺栓 OK 后更新为 OK
- BaseEntity 字段: `id`, `deleted`, `creator_id`, `modifier_id`, `create_time`, `modify_time`

## 新增文件

### 1. Entity

```java
@Setter @Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("task_record")
public class TaskRecord extends BaseEntity {
    private Long productTaskId;
    private String productCode;
    private Integer isRework;
    private Integer taskResult;
}
```

### 2. DTO

```java
@Setter @Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class TaskRecordDTO extends BaseDTO {
    private Long productTaskId;
    private String productCode;
    private Integer isRework;
    private Integer taskResult;
}
```

### 3. Mapper

```java
@Mapper
public interface TaskRecordMapper extends BaseMapper<TaskRecord> {}
```

### 4. Service

```java
@Slf4j
@Service
public class TaskRecordService extends ServiceImpl<TaskRecordMapper, TaskRecord> {}
```

### 5. 枚举

**TaskResult** (`constant/TaskResult.java`):

```java
@Getter
public enum TaskResult {
    NG(0),
    OK(1);

    private final int code;

    TaskResult(int code) { this.code = code; }

    public static Optional<TaskResult> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

**ReworkStatus** (`constant/ReworkStatus.java`):

```java
@Getter
public enum ReworkStatus {
    NORMAL(0),
    REWORK(1);

    private final int code;

    ReworkStatus(int code) { this.code = code; }

    public static Optional<ReworkStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

**DeleteStatus** (`constant/DeleteStatus.java`):

```java
@Getter
public enum DeleteStatus {
    NORMAL(0),
    DELETED(1);

    private final int code;

    DeleteStatus(int code) { this.code = code; }

    public static Optional<DeleteStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

**设计决策**：实体字段使用 `Integer` 而非枚举类型，避免引入 MyBatis-Plus `typeEnumsPackage` 配置复杂度。枚举仅作为常量对照表，在 service 层使用。与现有 `TighteningStatus` 模式一致。

## 文件清单

| # | 文件 | 路径 |
|---|------|------|
| 1 | SQL 迁移 | `src/main/resources/db/migration/V1.0.7__add_table_task_record.sql` |
| 2 | Entity | `src/main/java/com/tightening/entity/TaskRecord.java` |
| 3 | DTO | `src/main/java/com/tightening/dto/TaskRecordDTO.java` |
| 4 | Mapper | `src/main/java/com/tightening/mapper/TaskRecordMapper.java` |
| 5 | Service | `src/main/java/com/tightening/service/TaskRecordService.java` |
| 6 | Enum | `src/main/java/com/tightening/constant/TaskResult.java` |
| 7 | Enum | `src/main/java/com/tightening/constant/ReworkStatus.java` |
| 8 | Enum | `src/main/java/com/tightening/constant/DeleteStatus.java` |
