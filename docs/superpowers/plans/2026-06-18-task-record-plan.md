# Task Record 表 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `task_record` 表及对应 Entity、DTO、Mapper、Service、枚举，遵循现有 TighteningData 模式。

**Architecture:** 完全遵循现有分层模式——Entity extends BaseEntity, DTO extends BaseDTO, Mapper extends BaseMapper, Service extends ServiceImpl。Entity 字段使用 Integer 而非枚举类型，枚举仅作常量参考。

**Tech Stack:** Java 21, MyBatis-Plus 3.5.9, SQLite/Flyway, Lombok

---

### Task 1: 创建枚举文件

**Files:**
- Create: `src/main/java/com/tightening/constant/TaskResult.java`
- Create: `src/main/java/com/tightening/constant/ReworkStatus.java`
- Create: `src/main/java/com/tightening/constant/DeleteStatus.java`

- [ ] **Step 1: 创建 TaskResult 枚举**

```java
package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum TaskResult {
    NG(0),
    OK(1);

    private final int code;

    TaskResult(int code) {
        this.code = code;
    }

    public static Optional<TaskResult> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 2: 创建 ReworkStatus 枚举**

```java
package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum ReworkStatus {
    NORMAL(0),
    REWORK(1);

    private final int code;

    ReworkStatus(int code) {
        this.code = code;
    }

    public static Optional<ReworkStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 3: 创建 DeleteStatus 枚举**

```java
package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum DeleteStatus {
    NORMAL(0),
    DELETED(1);

    private final int code;

    DeleteStatus(int code) {
        this.code = code;
    }

    public static Optional<DeleteStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
mvn compile -q
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tightening/constant/TaskResult.java \
        src/main/java/com/tightening/constant/ReworkStatus.java \
        src/main/java/com/tightening/constant/DeleteStatus.java
git commit -m "feat: add TaskResult, ReworkStatus, DeleteStatus enums"
```

### Task 2: 创建 SQL 迁移文件

**Files:**
- Create: `src/main/resources/db/migration/V1.0.7__add_table_task_record.sql`

- [ ] **Step 1: 创建迁移文件**

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

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/db/migration/V1.0.7__add_table_task_record.sql
git commit -m "feat: add task_record table migration"
```

### Task 3: 创建 Entity 和 DTO

**Files:**
- Create: `src/main/java/com/tightening/entity/TaskRecord.java`
- Create: `src/main/java/com/tightening/dto/TaskRecordDTO.java`

- [ ] **Step 1: 创建 TaskRecord Entity**

```java
package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Setter
@Getter
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

- [ ] **Step 2: 创建 TaskRecordDTO**

```java
package com.tightening.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Setter
@Getter
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

- [ ] **Step 3: 编译验证**

```bash
mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tightening/entity/TaskRecord.java \
        src/main/java/com/tightening/dto/TaskRecordDTO.java
git commit -m "feat: add TaskRecord entity and DTO"
```

### Task 4: 创建 Mapper 和 Service

**Files:**
- Create: `src/main/java/com/tightening/mapper/TaskRecordMapper.java`
- Create: `src/main/java/com/tightening/service/TaskRecordService.java`

- [ ] **Step 1: 创建 TaskRecordMapper**

```java
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.TaskRecord;

@Mapper
public interface TaskRecordMapper extends BaseMapper<TaskRecord> {}
```

- [ ] **Step 2: 创建 TaskRecordService**

```java
package com.tightening.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.TaskRecord;
import com.tightening.mapper.TaskRecordMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TaskRecordService extends ServiceImpl<TaskRecordMapper, TaskRecord> {}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tightening/mapper/TaskRecordMapper.java \
        src/main/java/com/tightening/service/TaskRecordService.java
git commit -m "feat: add TaskRecord mapper and service"
```

### Task 5: 启动验证

- [ ] **Step 1: 启动应用，确认 Flyway 迁移成功且启动无报错**

```bash
mvn spring-boot:run
```

验证日志中 Flyway 执行完毕，无 Mapper/Bean 注册错误。启动后 Ctrl+C 停止。

---

## Self-Review

1. **Spec 覆盖**: Entity、DTO、Mapper、Service、3 个枚举、SQL 迁移——8 个文件全覆盖
2. **Placeholder 检查**: 无 TBD/TODO，每步都有完整代码
3. **类型一致性**: Entity 和 DTO 字段名完全对应；枚举与 entity 字段类型（Integer）一致
