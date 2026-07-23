# Task 配置表实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从旧系统迁移 4 张配置表到当前项目，新建 8 张表 + 对应 Java 组件（Entity/DTO/Mapper/Service/Enum/Validator/Controller），共计 44 个文件。

**Architecture:** 遵循现有分层——SQL 迁移 → Entity → DTO → Mapper → Service → Controller。所有实体继承 BaseEntity，DTO 继承 BaseDTO。枚举遵循现有 `fromCode` 模式。Controller 使用 `@RequestMapping("api/...")`，遵循 RESTful 风格（GET 查询、POST 创建、PUT 更新、DELETE 删除），同步 `ResponseEntity` 返回。

**Tech Stack:** Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.9, SQLite (Flyway), JUnit 5 + AssertJ

---

## Phase 1: 枚举

### Task 1: PrerequisiteType 枚举 + 测试

**Files:**
- Create: `src/main/java/com/tightening/constant/PrerequisiteType.java`
- Create: `src/test/java/com/tightening/constant/PrerequisiteTypeTest.java`

- [ ] **Step 1: 创建枚举类**

```java
package com.tightening.constant;

import lombok.Getter;
import java.util.Arrays;
import java.util.Optional;

@Getter
public enum PrerequisiteType {
    SAME_TRACE(1),
    PARTS_TRACE(2),
    INSPECTION_CHAIN(3);

    private final int code;

    PrerequisiteType(int code) { this.code = code; }

    public static Optional<PrerequisiteType> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PrerequisiteTypeTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(PrerequisiteType.fromCode(1)).contains(PrerequisiteType.SAME_TRACE);
        assertThat(PrerequisiteType.fromCode(2)).contains(PrerequisiteType.PARTS_TRACE);
        assertThat(PrerequisiteType.fromCode(3)).contains(PrerequisiteType.INSPECTION_CHAIN);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(PrerequisiteType.fromCode(-1)).isEmpty();
        assertThat(PrerequisiteType.fromCode(0)).isEmpty();
        assertThat(PrerequisiteType.fromCode(4)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(PrerequisiteType.values())
                .map(PrerequisiteType::getCode).distinct().count();
        assertThat(codes).isEqualTo(PrerequisiteType.values().length);
    }
}
```

- [ ] **Step 3: 运行测试确认通过**

```bash
mvn test -pl . -Dtest=PrerequisiteTypeTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/constant/PrerequisiteType.java src/test/java/com/tightening/constant/PrerequisiteTypeTest.java
git commit -m "feat: add PrerequisiteType enum"
```

### Task 2: InspectionScope 枚举 + 测试

**Files:**
- Create: `src/main/java/com/tightening/constant/InspectionScope.java`
- Create: `src/test/java/com/tightening/constant/InspectionScopeTest.java`

- [ ] **Step 1: 创建枚举类**

```java
package com.tightening.constant;

import lombok.Getter;
import java.util.Arrays;
import java.util.Optional;

@Getter
public enum InspectionScope {
    ALL(1),
    CHOSEN(2);

    private final int code;

    InspectionScope(int code) { this.code = code; }

    public static Optional<InspectionScope> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InspectionScopeTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(InspectionScope.fromCode(1)).contains(InspectionScope.ALL);
        assertThat(InspectionScope.fromCode(2)).contains(InspectionScope.CHOSEN);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(InspectionScope.fromCode(-1)).isEmpty();
        assertThat(InspectionScope.fromCode(0)).isEmpty();
        assertThat(InspectionScope.fromCode(3)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(InspectionScope.values())
                .map(InspectionScope::getCode).distinct().count();
        assertThat(codes).isEqualTo(InspectionScope.values().length);
    }
}
```

- [ ] **Step 3: 运行测试确认通过**

```bash
mvn test -pl . -Dtest=InspectionScopeTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/constant/InspectionScope.java src/test/java/com/tightening/constant/InspectionScopeTest.java
git commit -m "feat: add InspectionScope enum"
```

### Task 3: IoDeviceType 枚举 + 测试

**Files:**
- Create: `src/main/java/com/tightening/constant/IoDeviceType.java`
- Create: `src/test/java/com/tightening/constant/IoDeviceTypeTest.java`

- [ ] **Step 1: 创建枚举类**

```java
package com.tightening.constant;

import lombok.Getter;
import java.util.Arrays;
import java.util.Optional;

@Getter
public enum IoDeviceType {
    ARRANGER(1),
    SETTER_SELECTOR(2);

    private final int code;

    IoDeviceType(int code) { this.code = code; }

    public static Optional<IoDeviceType> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IoDeviceTypeTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(IoDeviceType.fromCode(1)).contains(IoDeviceType.ARRANGER);
        assertThat(IoDeviceType.fromCode(2)).contains(IoDeviceType.SETTER_SELECTOR);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(IoDeviceType.fromCode(-1)).isEmpty();
        assertThat(IoDeviceType.fromCode(0)).isEmpty();
        assertThat(IoDeviceType.fromCode(3)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(IoDeviceType.values())
                .map(IoDeviceType::getCode).distinct().count();
        assertThat(codes).isEqualTo(IoDeviceType.values().length);
    }
}
```

- [ ] **Step 3: 运行测试确认通过**

```bash
mvn test -pl . -Dtest=IoDeviceTypeTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/constant/IoDeviceType.java src/test/java/com/tightening/constant/IoDeviceTypeTest.java
git commit -m "feat: add IoDeviceType enum"
```

### Task 4: BarCodeRuleType 枚举 + 测试

**Files:**
- Create: `src/main/java/com/tightening/constant/BarCodeRuleType.java`
- Create: `src/test/java/com/tightening/constant/BarCodeRuleTypeTest.java`

- [ ] **Step 1: 创建枚举类**

```java
package com.tightening.constant;

import lombok.Getter;
import java.util.Arrays;
import java.util.Optional;

@Getter
public enum BarCodeRuleType {
    PRODUCT_TRACE(1),
    PARTS_BARCODE(2);

    private final int code;

    BarCodeRuleType(int code) { this.code = code; }

    public static Optional<BarCodeRuleType> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BarCodeRuleTypeTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(BarCodeRuleType.fromCode(1)).contains(BarCodeRuleType.PRODUCT_TRACE);
        assertThat(BarCodeRuleType.fromCode(2)).contains(BarCodeRuleType.PARTS_BARCODE);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(BarCodeRuleType.fromCode(-1)).isEmpty();
        assertThat(BarCodeRuleType.fromCode(0)).isEmpty();
        assertThat(BarCodeRuleType.fromCode(3)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(BarCodeRuleType.values())
                .map(BarCodeRuleType::getCode).distinct().count();
        assertThat(codes).isEqualTo(BarCodeRuleType.values().length);
    }
}
```

- [ ] **Step 3: 运行测试确认通过**

```bash
mvn test -pl . -Dtest=BarCodeRuleTypeTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/constant/BarCodeRuleType.java src/test/java/com/tightening/constant/BarCodeRuleTypeTest.java
git commit -m "feat: add BarCodeRuleType enum"
```

### Task 4.5: ImageType 枚举 + 测试

**Files:**
- Create: `src/main/java/com/tightening/constant/ImageType.java`
- Create: `src/test/java/com/tightening/constant/ImageTypeTest.java`

注意：ImageType 使用 `String value` + `fromValue(String)` 模式（而非 `int code` + `fromCode(int)`），因为图片类型的业务语义天然是字符串标识。

- [ ] **Step 1: 创建枚举类**

```java
package com.tightening.constant;

import lombok.Getter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum ImageType {
    ORIGINAL("original"),
    RENDERED("rendered"),
    THUMBNAIL("thumbnail");

    private final String value;
    private static final Map<String, ImageType> BY_VALUE =
        Arrays.stream(values()).collect(Collectors.toMap(ImageType::getValue, Function.identity()));

    ImageType(String value) { this.value = value; }

    public static Optional<ImageType> fromValue(String value) {
        return Optional.ofNullable(BY_VALUE.get(value));
    }
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTest {

    @Test
    void fromValue_shouldReturnCorrectValue() {
        assertThat(ImageType.fromValue("original")).contains(ImageType.ORIGINAL);
        assertThat(ImageType.fromValue("rendered")).contains(ImageType.RENDERED);
        assertThat(ImageType.fromValue("thumbnail")).contains(ImageType.THUMBNAIL);
    }

    @Test
    void fromValue_shouldReturnEmptyForInvalidValue() {
        assertThat(ImageType.fromValue("invalid")).isEmpty();
        assertThat(ImageType.fromValue("")).isEmpty();
    }

    @Test
    void values_shouldBeUnique() {
        var values = java.util.Arrays.stream(ImageType.values())
                .map(ImageType::getValue).distinct().count();
        assertThat(values).isEqualTo(ImageType.values().length);
    }
}
```

- [ ] **Step 3: 运行测试确认通过**

```bash
mvn test -pl . -Dtest=ImageTypeTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/constant/ImageType.java src/test/java/com/tightening/constant/ImageTypeTest.java
git commit -m "feat: add ImageType enum"
```

---

## Phase 2: SQL 迁移

### Task 5: V1.0.8 — product_task + 关联表

**Files:**
- Create: `src/main/resources/db/migration/V1.0.8__add_table_product_task.sql`

- [ ] **Step 1: 创建迁移文件**

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
    inspection_scope              INTEGER,
    deleted                       INTEGER DEFAULT 0,
    creator_id                    INTEGER,
    modifier_id                   INTEGER,
    create_time                   TEXT,
    modify_time                   TEXT
);

CREATE TABLE task_prerequisite (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id                  INTEGER NOT NULL,
    prerequisite_task_id     INTEGER NOT NULL,
    prerequisite_type           INTEGER NOT NULL,
    deleted                     INTEGER DEFAULT 0,
    creator_id                  INTEGER,
    modifier_id                 INTEGER,
    create_time                 TEXT,
    modify_time                 TEXT
);

CREATE INDEX idx_task_prerequisite_task_id ON task_prerequisite(task_id);
CREATE INDEX idx_task_prerequisite_prerequisite_task_id ON task_prerequisite(prerequisite_task_id);

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

CREATE INDEX idx_inspection_task_binding_inspection_task_id ON inspection_task_binding(inspection_task_id);
CREATE INDEX idx_inspection_task_binding_bound_task_id ON inspection_task_binding(bound_task_id);

CREATE TABLE bar_code_matching_rule (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    name                  TEXT,
    product_task_id    INTEGER,
    rule_type             INTEGER NOT NULL,
    part_number           TEXT,
    expected_length       INTEGER,
    key_start_position    INTEGER,
    key_end_position      INTEGER,
    key_char              TEXT,
    deleted               INTEGER DEFAULT 0,
    creator_id            INTEGER,
    modifier_id           INTEGER,
    create_time           TEXT,
    modify_time           TEXT
);

CREATE INDEX idx_bar_code_matching_rule_product_task_id ON bar_code_matching_rule(product_task_id);
```

- [ ] **Step 2: 验证迁移可执行**

```bash
mvn flyway:migrate -Dflyway.url=jdbc:sqlite:~/tightening_system/tightening_test.db -Dflyway.locations=filesystem:src/main/resources/db/migration -q
```

预期：Migration V1.0.8 执行成功。

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/db/migration/V1.0.8__add_table_product_task.sql
git commit -m "feat: add product_task, task_prerequisite, inspection_task_binding, bar_code_matching_rule tables"
```

### Task 6: V1.0.9 — product_side

**Files:**
- Create: `src/main/resources/db/migration/V1.0.9__add_table_product_side.sql`

- [ ] **Step 1: 创建迁移文件**

```sql
CREATE TABLE product_side (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    product_task_id    INTEGER NOT NULL,
    name                  TEXT,
    image_data            BLOB,
    rendered_image_data   BLOB,
    thumbnail_data        BLOB,
    deleted               INTEGER DEFAULT 0,
    creator_id            INTEGER,
    modifier_id           INTEGER,
    create_time           TEXT,
    modify_time           TEXT
);

CREATE INDEX idx_product_side_product_task_id ON product_side(product_task_id);
```

- [ ] **Step 2: 验证迁移可执行**

```bash
mvn flyway:migrate -Dflyway.url=jdbc:sqlite:~/tightening_system/tightening_test.db -Dflyway.locations=filesystem:src/main/resources/db/migration -q
```

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/db/migration/V1.0.9__add_table_product_side.sql
git commit -m "feat: add product_side table"
```

### Task 7: V1.0.10 — product_bolt + 关联表

**Files:**
- Create: `src/main/resources/db/migration/V1.0.10__add_table_product_bolt.sql`

- [ ] **Step 1: 创建迁移文件**

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

CREATE INDEX idx_product_bolt_product_side_id ON product_bolt(product_side_id);

CREATE TABLE bolt_device_binding (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    product_bolt_id       INTEGER NOT NULL,
    device_id             INTEGER NOT NULL,
    device_role           INTEGER NOT NULL,
    device_spec           REAL,
    sort_order            INTEGER DEFAULT 0,
    deleted               INTEGER DEFAULT 0,
    creator_id            INTEGER,
    modifier_id           INTEGER,
    create_time           TEXT,
    modify_time           TEXT
);

CREATE INDEX idx_bolt_device_binding_product_bolt_id ON bolt_device_binding(product_bolt_id);
CREATE INDEX idx_bolt_device_binding_device_id ON bolt_device_binding(device_id);

CREATE TABLE bolt_parts_barcode (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    product_bolt_id             INTEGER NOT NULL,
    bar_code_matching_rule_id   INTEGER NOT NULL,
    deleted                     INTEGER DEFAULT 0,
    creator_id                  INTEGER,
    modifier_id                  INTEGER,
    create_time                 TEXT,
    modify_time                 TEXT
);

CREATE INDEX idx_bolt_parts_barcode_product_bolt_id ON bolt_parts_barcode(product_bolt_id);
CREATE INDEX idx_bolt_parts_barcode_bar_code_matching_rule_id ON bolt_parts_barcode(bar_code_matching_rule_id);
```

- [ ] **Step 2: 验证迁移可执行**

```bash
mvn flyway:migrate -Dflyway.url=jdbc:sqlite:~/tightening_system/tightening_test.db -Dflyway.locations=filesystem:src/main/resources/db/migration -q
```

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/db/migration/V1.0.10__add_table_product_bolt.sql
git commit -m "feat: add product_bolt, bolt_device_binding, bolt_parts_barcode tables"
```

---

## Phase 3: Entity

所有 Entity 使用统一模式：`@Setter @Getter @ToString(callSuper = true) @EqualsAndHashCode(callSuper = true) @Accessors(chain = true) @TableName(...)`，继承 `BaseEntity`。测试模式：JSON 序列化往返 + 空对象序列化。

### Task 8: ProductTask Entity + 测试

**Files:**
- Create: `src/main/java/com/tightening/entity/ProductTask.java`
- Create: `src/test/java/com/tightening/entity/ProductTaskTest.java`

- [ ] **Step 1: 创建 Entity**

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
@TableName("product_task")
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

- [ ] **Step 2: 创建测试**

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductTaskTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        ProductTask original = new ProductTask();
        original.setId(1L);
        original.setName("Task A");
        original.setMaxNgCount(3);
        original.setPasswordRequiredAfterNg(2);
        original.setEnabled(1);
        original.setMultiDeviceIndependent(0);
        original.setSkipScrew(0);
        original.setIsInspection(0);
        original.setInspectionScope(null);

        String json = mapper.writeValueAsString(original);
        ProductTask restored = mapper.readValue(json, ProductTask.class);

        assertThat(restored.getName()).isEqualTo("Task A");
        assertThat(restored.getMaxNgCount()).isEqualTo(3);
        assertThat(restored.getPasswordRequiredAfterNg()).isEqualTo(2);
        assertThat(restored.getEnabled()).isEqualTo(1);
        assertThat(restored.getSkipScrew()).isEqualTo(0);
        assertThat(restored.getIsInspection()).isEqualTo(0);
    }

    @Test
    void jsonRoundTrip_inspectionTask_shouldPreserveScope() throws Exception {
        ProductTask original = new ProductTask();
        original.setId(2L);
        original.setName("Inspection A");
        original.setIsInspection(1);
        original.setInspectionScope(2);

        String json = mapper.writeValueAsString(original);
        ProductTask restored = mapper.readValue(json, ProductTask.class);

        assertThat(restored.getIsInspection()).isEqualTo(1);
        assertThat(restored.getInspectionScope()).isEqualTo(2);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductTask());
        ProductTask restored = mapper.readValue(json, ProductTask.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=ProductTaskTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/ProductTask.java src/test/java/com/tightening/entity/ProductTaskTest.java
git commit -m "feat: add ProductTask entity"
```

### Task 9: TaskPrerequisite Entity + 测试

**Files:**
- Create: `src/main/java/com/tightening/entity/TaskPrerequisite.java`
- Create: `src/test/java/com/tightening/entity/TaskPrerequisiteTest.java`

- [ ] **Step 1: 创建 Entity**

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
@TableName("task_prerequisite")
public class TaskPrerequisite extends BaseEntity {
    private Long taskId;
    private Long prerequisiteTaskId;
    private Integer prerequisiteType;
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TaskPrerequisiteTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        TaskPrerequisite original = new TaskPrerequisite();
        original.setId(1L);
        original.setTaskId(10L);
        original.setPrerequisiteTaskId(5L);
        original.setPrerequisiteType(1);

        String json = mapper.writeValueAsString(original);
        TaskPrerequisite restored = mapper.readValue(json, TaskPrerequisite.class);

        assertThat(restored.getTaskId()).isEqualTo(10L);
        assertThat(restored.getPrerequisiteTaskId()).isEqualTo(5L);
        assertThat(restored.getPrerequisiteType()).isEqualTo(1);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new TaskPrerequisite());
        TaskPrerequisite restored = mapper.readValue(json, TaskPrerequisite.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=TaskPrerequisiteTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/TaskPrerequisite.java src/test/java/com/tightening/entity/TaskPrerequisiteTest.java
git commit -m "feat: add TaskPrerequisite entity"
```

### Task 10: InspectionTaskBinding Entity + 测试

**Files:**
- Create: `src/main/java/com/tightening/entity/InspectionTaskBinding.java`
- Create: `src/test/java/com/tightening/entity/InspectionTaskBindingTest.java`

- [ ] **Step 1: 创建 Entity**

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
@TableName("inspection_task_binding")
public class InspectionTaskBinding extends BaseEntity {
    private Long inspectionTaskId;
    private Long boundTaskId;
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InspectionTaskBindingTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        InspectionTaskBinding original = new InspectionTaskBinding();
        original.setId(1L);
        original.setInspectionTaskId(10L);
        original.setBoundTaskId(3L);

        String json = mapper.writeValueAsString(original);
        InspectionTaskBinding restored = mapper.readValue(json, InspectionTaskBinding.class);

        assertThat(restored.getInspectionTaskId()).isEqualTo(10L);
        assertThat(restored.getBoundTaskId()).isEqualTo(3L);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new InspectionTaskBinding());
        InspectionTaskBinding restored = mapper.readValue(json, InspectionTaskBinding.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=InspectionTaskBindingTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/InspectionTaskBinding.java src/test/java/com/tightening/entity/InspectionTaskBindingTest.java
git commit -m "feat: add InspectionTaskBinding entity"
```

### Task 11: BarCodeMatchingRule Entity + 测试

**Files:**
- Create: `src/main/java/com/tightening/entity/BarCodeMatchingRule.java`
- Create: `src/test/java/com/tightening/entity/BarCodeMatchingRuleTest.java`

- [ ] **Step 1: 创建 Entity**

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
@TableName("bar_code_matching_rule")
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

- [ ] **Step 2: 创建测试**

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BarCodeMatchingRuleTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        BarCodeMatchingRule original = new BarCodeMatchingRule();
        original.setId(1L);
        original.setName("追溯码规则");
        original.setProductTaskId(5L);
        original.setRuleType(2);
        original.setExpectedLength(10);
        original.setKeyStartPosition(6);
        original.setKeyEndPosition(9);
        original.setKeyChar("ABCD");

        String json = mapper.writeValueAsString(original);
        BarCodeMatchingRule restored = mapper.readValue(json, BarCodeMatchingRule.class);

        assertThat(restored.getName()).isEqualTo("追溯码规则");
        assertThat(restored.getRuleType()).isEqualTo(2);
        assertThat(restored.getExpectedLength()).isEqualTo(10);
        assertThat(restored.getKeyStartPosition()).isEqualTo(6);
        assertThat(restored.getKeyEndPosition()).isEqualTo(9);
        assertThat(restored.getKeyChar()).isEqualTo("ABCD");
    }

    @Test
    void jsonRoundTrip_singleCharPosition_shouldPreserveNullEndPosition() throws Exception {
        BarCodeMatchingRule original = new BarCodeMatchingRule();
        original.setId(2L);
        original.setKeyStartPosition(6);
        original.setKeyEndPosition(null);
        original.setKeyChar("A");

        String json = mapper.writeValueAsString(original);
        BarCodeMatchingRule restored = mapper.readValue(json, BarCodeMatchingRule.class);

        assertThat(restored.getKeyStartPosition()).isEqualTo(6);
        assertThat(restored.getKeyEndPosition()).isNull();
        assertThat(restored.getKeyChar()).isEqualTo("A");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new BarCodeMatchingRule());
        BarCodeMatchingRule restored = mapper.readValue(json, BarCodeMatchingRule.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=BarCodeMatchingRuleTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/BarCodeMatchingRule.java src/test/java/com/tightening/entity/BarCodeMatchingRuleTest.java
git commit -m "feat: add BarCodeMatchingRule entity"
```

### Task 12: ProductSide Entity + 测试

**Files:**
- Create: `src/main/java/com/tightening/entity/ProductSide.java`
- Create: `src/test/java/com/tightening/entity/ProductSideTest.java`

- [ ] **Step 1: 创建 Entity**

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
@TableName("product_side")
public class ProductSide extends BaseEntity {
    private Long productTaskId;
    private String name;
    private byte[] imageData;
    private byte[] renderedImageData;
    private byte[] thumbnailData;
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductSideTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_nonBlobFields_shouldPreserveValues() throws Exception {
        ProductSide original = new ProductSide();
        original.setId(1L);
        original.setProductTaskId(5L);
        original.setName("Side A");

        String json = mapper.writeValueAsString(original);
        ProductSide restored = mapper.readValue(json, ProductSide.class);

        assertThat(restored.getName()).isEqualTo("Side A");
        assertThat(restored.getProductTaskId()).isEqualTo(5L);
    }

    @Test
    void byteArrays_shouldSerializeAsBase64() throws Exception {
        ProductSide original = new ProductSide();
        original.setId(1L);
        original.setImageData(new byte[]{1, 2, 3});
        original.setRenderedImageData(new byte[]{4, 5, 6});
        original.setThumbnailData(new byte[]{7, 8, 9});

        String json = mapper.writeValueAsString(original);
        ProductSide restored = mapper.readValue(json, ProductSide.class);

        assertThat(restored.getImageData()).containsExactly(1, 2, 3);
        assertThat(restored.getRenderedImageData()).containsExactly(4, 5, 6);
        assertThat(restored.getThumbnailData()).containsExactly(7, 8, 9);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductSide());
        ProductSide restored = mapper.readValue(json, ProductSide.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=ProductSideTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/ProductSide.java src/test/java/com/tightening/entity/ProductSideTest.java
git commit -m "feat: add ProductSide entity"
```

### Task 13: ProductBolt Entity + 测试

**Files:**
- Create: `src/main/java/com/tightening/entity/ProductBolt.java`
- Create: `src/test/java/com/tightening/entity/ProductBoltTest.java`

- [ ] **Step 1: 创建 Entity**

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
@TableName("product_bolt")
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

- [ ] **Step 2: 创建测试**

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductBoltTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        ProductBolt original = new ProductBolt();
        original.setId(1L);
        original.setProductSideId(3L);
        original.setBoltSerialNum(1);
        original.setBoltName("Bolt-1");
        original.setParameterSetId(10L);
        original.setTorqueMin(5.0);
        original.setTorqueMax(25.0);
        original.setAngleMin(10.0);
        original.setAngleMax(180.0);
        original.setArmLocation("L");
        original.setLocationXPercent(30.5);
        original.setLocationYPercent(45.2);
        original.setEnabled(1);

        String json = mapper.writeValueAsString(original);
        ProductBolt restored = mapper.readValue(json, ProductBolt.class);

        assertThat(restored.getBoltSerialNum()).isEqualTo(1);
        assertThat(restored.getBoltName()).isEqualTo("Bolt-1");
        assertThat(restored.getTorqueMin()).isEqualTo(5.0);
        assertThat(restored.getLocationXPercent()).isEqualTo(30.5);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductBolt());
        ProductBolt restored = mapper.readValue(json, ProductBolt.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=ProductBoltTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/ProductBolt.java src/test/java/com/tightening/entity/ProductBoltTest.java
git commit -m "feat: add ProductBolt entity"
```

### Task 14: BoltDeviceBinding Entity + 测试

**Files:**
- Create: `src/main/java/com/tightening/entity/BoltDeviceBinding.java`
- Create: `src/test/java/com/tightening/entity/BoltDeviceBindingTest.java`

- [ ] **Step 1: 创建 Entity**

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
@TableName("bolt_device_binding")
public class BoltDeviceBinding extends BaseEntity {
    private Long productBoltId;
    private Long deviceId;
    private Integer deviceRole;
    private Double deviceSpec;
    private Integer sortOrder;
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BoltDeviceBindingTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        BoltDeviceBinding original = new BoltDeviceBinding();
        original.setId(1L);
        original.setProductBoltId(10L);
        original.setDeviceId(3L);
        original.setDeviceRole(1);
        original.setDeviceSpec(10.0);
        original.setSortOrder(1);

        String json = mapper.writeValueAsString(original);
        BoltDeviceBinding restored = mapper.readValue(json, BoltDeviceBinding.class);

        assertThat(restored.getProductBoltId()).isEqualTo(10L);
        assertThat(restored.getDeviceId()).isEqualTo(3L);
        assertThat(restored.getIoDeviceType()).isEqualTo(1);
        assertThat(restored.getDeviceSpec()).isEqualTo(10.0);
        assertThat(restored.getSortOrder()).isEqualTo(1);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new BoltDeviceBinding());
        BoltDeviceBinding restored = mapper.readValue(json, BoltDeviceBinding.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=BoltDeviceBindingTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/BoltDeviceBinding.java src/test/java/com/tightening/entity/BoltDeviceBindingTest.java
git commit -m "feat: add BoltDeviceBinding entity"
```

### Task 15: BoltPartsBarcode Entity + 测试

**Files:**
- Create: `src/main/java/com/tightening/entity/BoltPartsBarcode.java`
- Create: `src/test/java/com/tightening/entity/BoltPartsBarcodeTest.java`

- [ ] **Step 1: 创建 Entity**

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
@TableName("bolt_parts_barcode")
public class BoltPartsBarcode extends BaseEntity {
    private Long productBoltId;
    private Long barCodeMatchingRuleId;
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BoltPartsBarcodeTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        BoltPartsBarcode original = new BoltPartsBarcode();
        original.setId(1L);
        original.setProductBoltId(10L);
        original.setBarCodeMatchingRuleId(5L);

        String json = mapper.writeValueAsString(original);
        BoltPartsBarcode restored = mapper.readValue(json, BoltPartsBarcode.class);

        assertThat(restored.getProductBoltId()).isEqualTo(10L);
        assertThat(restored.getBarCodeMatchingRuleId()).isEqualTo(5L);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new BoltPartsBarcode());
        BoltPartsBarcode restored = mapper.readValue(json, BoltPartsBarcode.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=BoltPartsBarcodeTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/BoltPartsBarcode.java src/test/java/com/tightening/entity/BoltPartsBarcodeTest.java
git commit -m "feat: add BoltPartsBarcode entity"
```

---

## Phase 4: DTO

ProductSide DTO 不含 BLOB 字段（图片通过独立上传接口处理）。ProductBolt、BoltDeviceBinding、BoltPartsBarcode DTO 字段与 Entity 一致（不含 BaseEntity 字段，由 BaseDTO 提供）。

### Task 16: 全部 8 个 DTO

**Files:**
- Create: `src/main/java/com/tightening/dto/ProductTaskDTO.java`
- Create: `src/main/java/com/tightening/dto/TaskPrerequisiteDTO.java`
- Create: `src/main/java/com/tightening/dto/InspectionTaskBindingDTO.java`
- Create: `src/main/java/com/tightening/dto/BarCodeMatchingRuleDTO.java`
- Create: `src/main/java/com/tightening/dto/ProductSideDTO.java`
- Create: `src/main/java/com/tightening/dto/ProductBoltDTO.java`
- Create: `src/main/java/com/tightening/dto/BoltDeviceBindingDTO.java`
- Create: `src/main/java/com/tightening/dto/BoltPartsBarcodeDTO.java`
- Create: `src/test/java/com/tightening/dto/ProductTaskDTOTest.java`
- Create: `src/test/java/com/tightening/dto/ProductSideDTOTest.java`
- Create: `src/test/java/com/tightening/dto/ProductBoltDTOTest.java`

- [ ] **Step 1: 创建 ProductTaskDTO**

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

- [ ] **Step 2: 创建 TaskPrerequisiteDTO**

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
public class TaskPrerequisiteDTO extends BaseDTO {
    private Long taskId;
    private Long prerequisiteTaskId;
    private Integer prerequisiteType;
}
```

- [ ] **Step 3: 创建 InspectionTaskBindingDTO**

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
public class InspectionTaskBindingDTO extends BaseDTO {
    private Long inspectionTaskId;
    private Long boundTaskId;
}
```

- [ ] **Step 4: 创建 BarCodeMatchingRuleDTO**

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
public class BarCodeMatchingRuleDTO extends BaseDTO {
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

- [ ] **Step 5: 创建 ProductSideDTO（不含 BLOB）**

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
public class ProductSideDTO extends BaseDTO {
    private Long productTaskId;
    private String name;
}
```

- [ ] **Step 6: 创建 ProductBoltDTO**

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
public class ProductBoltDTO extends BaseDTO {
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

- [ ] **Step 7: 创建 BoltDeviceBindingDTO**

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
public class BoltDeviceBindingDTO extends BaseDTO {
    private Long productBoltId;
    private Long deviceId;
    private Integer deviceRole;
    private Double deviceSpec;
    private Integer sortOrder;
}
```

- [ ] **Step 8: 创建 BoltPartsBarcodeDTO**

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
public class BoltPartsBarcodeDTO extends BaseDTO {
    private Long productBoltId;
    private Long barCodeMatchingRuleId;
}
```

- [ ] **Step 9: 创建 DTO 测试（仅测有代表性的 3 个 DTO，其余结构相同）**

`src/test/java/com/tightening/dto/ProductTaskDTOTest.java`:
```java
package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductTaskDTOTest {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_shouldPreserveValues() throws Exception {
        ProductTaskDTO dto = new ProductTaskDTO();
        dto.setId(1L);
        dto.setName("Test");
        dto.setMaxNgCount(3);
        dto.setEnabled(1);

        String json = mapper.writeValueAsString(dto);
        ProductTaskDTO restored = mapper.readValue(json, ProductTaskDTO.class);

        assertThat(restored.getName()).isEqualTo("Test");
        assertThat(restored.getMaxNgCount()).isEqualTo(3);
    }

    @Test
    void emptyDto_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductTaskDTO());
        assertThat(mapper.readValue(json, ProductTaskDTO.class)).isNotNull();
    }
}
```

`src/test/java/com/tightening/dto/ProductSideDTOTest.java`:
```java
package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductSideDTOTest {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_shouldPreserveValues() throws Exception {
        ProductSideDTO dto = new ProductSideDTO();
        dto.setId(1L);
        dto.setProductTaskId(5L);
        dto.setName("Side A");

        String json = mapper.writeValueAsString(dto);
        ProductSideDTO restored = mapper.readValue(json, ProductSideDTO.class);

        assertThat(restored.getName()).isEqualTo("Side A");
        assertThat(restored.getProductTaskId()).isEqualTo(5L);
    }

    @Test
    void emptyDto_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductSideDTO());
        assertThat(mapper.readValue(json, ProductSideDTO.class)).isNotNull();
    }
}
```

`src/test/java/com/tightening/dto/ProductBoltDTOTest.java`:
```java
package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductBoltDTOTest {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_shouldPreserveValues() throws Exception {
        ProductBoltDTO dto = new ProductBoltDTO();
        dto.setId(1L);
        dto.setProductSideId(3L);
        dto.setBoltSerialNum(1);
        dto.setTorqueMin(5.0);
        dto.setTorqueMax(25.0);

        String json = mapper.writeValueAsString(dto);
        ProductBoltDTO restored = mapper.readValue(json, ProductBoltDTO.class);

        assertThat(restored.getBoltSerialNum()).isEqualTo(1);
        assertThat(restored.getTorqueMin()).isEqualTo(5.0);
        assertThat(restored.getTorqueMax()).isEqualTo(25.0);
    }

    @Test
    void emptyDto_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductBoltDTO());
        assertThat(mapper.readValue(json, ProductBoltDTO.class)).isNotNull();
    }
}
```

- [ ] **Step 10: 运行测试**

```bash
mvn test -pl . -Dtest="ProductTaskDTOTest,ProductSideDTOTest,ProductBoltDTOTest" -DfailIfNoTests=false -q
```

- [ ] **Step 11: 提交**

```bash
git add src/main/java/com/tightening/dto/ProductTaskDTO.java \
        src/main/java/com/tightening/dto/TaskPrerequisiteDTO.java \
        src/main/java/com/tightening/dto/InspectionTaskBindingDTO.java \
        src/main/java/com/tightening/dto/BarCodeMatchingRuleDTO.java \
        src/main/java/com/tightening/dto/ProductSideDTO.java \
        src/main/java/com/tightening/dto/ProductBoltDTO.java \
        src/main/java/com/tightening/dto/BoltDeviceBindingDTO.java \
        src/main/java/com/tightening/dto/BoltPartsBarcodeDTO.java \
        src/test/java/com/tightening/dto/ProductTaskDTOTest.java \
        src/test/java/com/tightening/dto/ProductSideDTOTest.java \
        src/test/java/com/tightening/dto/ProductBoltDTOTest.java
git commit -m "feat: add task config DTOs"
```

---

## Phase 5: Mapper

### Task 17: 全部 8 个 Mapper

**Files:**
- Create: 8 个 Mapper 文件

- [ ] **Step 1: 创建所有 Mapper**

```bash
cat > src/main/java/com/tightening/mapper/ProductTaskMapper.java << 'JAVA'
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.ProductTask;

@Mapper
public interface ProductTaskMapper extends BaseMapper<ProductTask> {}
JAVA

cat > src/main/java/com/tightening/mapper/TaskPrerequisiteMapper.java << 'JAVA'
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.TaskPrerequisite;

@Mapper
public interface TaskPrerequisiteMapper extends BaseMapper<TaskPrerequisite> {}
JAVA

cat > src/main/java/com/tightening/mapper/InspectionTaskBindingMapper.java << 'JAVA'
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.InspectionTaskBinding;

@Mapper
public interface InspectionTaskBindingMapper extends BaseMapper<InspectionTaskBinding> {}
JAVA

cat > src/main/java/com/tightening/mapper/BarCodeMatchingRuleMapper.java << 'JAVA'
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.BarCodeMatchingRule;

@Mapper
public interface BarCodeMatchingRuleMapper extends BaseMapper<BarCodeMatchingRule> {}
JAVA

cat > src/main/java/com/tightening/mapper/ProductSideMapper.java << 'JAVA'
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.ProductSide;

@Mapper
public interface ProductSideMapper extends BaseMapper<ProductSide> {}
JAVA

cat > src/main/java/com/tightening/mapper/ProductBoltMapper.java << 'JAVA'
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.ProductBolt;

@Mapper
public interface ProductBoltMapper extends BaseMapper<ProductBolt> {}
JAVA

cat > src/main/java/com/tightening/mapper/BoltDeviceBindingMapper.java << 'JAVA'
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.BoltDeviceBinding;

@Mapper
public interface BoltDeviceBindingMapper extends BaseMapper<BoltDeviceBinding> {}
JAVA

cat > src/main/java/com/tightening/mapper/BoltPartsBarcodeMapper.java << 'JAVA'
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.BoltPartsBarcode;

@Mapper
public interface BoltPartsBarcodeMapper extends BaseMapper<BoltPartsBarcode> {}
JAVA
```

- [ ] **Step 2: 启动验证 Spring 可扫描到所有 Mapper**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev &
sleep 5
curl -s http://localhost:8080/api/login -X POST -H "Content-Type: application/json" -d '{"username":"test","password":"test"}' || true
kill %1 2>/dev/null
```

预期：应用正常启动，无 "required a bean of type Mapper" 错误。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/tightening/mapper/
git commit -m "feat: add task config mappers"
```

---

## Phase 6: Service + Validator

### Task 18: 全部 8 个 Service

**Files:**
- Create: 8 个 Service 文件

- [ ] **Step 1: 创建所有 Service**

```bash
cat > src/main/java/com/tightening/service/ProductTaskService.java << 'JAVA'
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.BarCodeRuleType;
import com.tightening.entity.ProductTask;
import com.tightening.entity.TaskPrerequisite;
import com.tightening.entity.InspectionTaskBinding;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.ProductSide;
import com.tightening.mapper.ProductTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductTaskService extends ServiceImpl<ProductTaskMapper, ProductTask> {

    private final TaskConfigValidator validator;
    private final ProductSideService sideService;
    private final TaskPrerequisiteService prerequisiteService;
    private final InspectionTaskBindingService bindingService;
    private final BarCodeMatchingRuleService barcodeRuleService;

    /** 添加前置依赖，含校验 */
    public void addPrerequisite(Long taskId, Long prerequisiteTaskId, Integer prerequisiteType) {
        validator.validateNoCircularDependency(taskId, prerequisiteTaskId);
        ProductTask target = getById(prerequisiteTaskId);
        validator.validatePrerequisiteType(target, prerequisiteType);
        TaskPrerequisite mp = new TaskPrerequisite()
                .setTaskId(taskId)
                .setPrerequisiteTaskId(prerequisiteTaskId)
                .setPrerequisiteType(prerequisiteType);
        prerequisiteService.save(mp);
    }

    /** 添加点检绑定，含校验 */
    public void addInspectionBinding(Long inspectionTaskId, Long boundTaskId) {
        ProductTask bound = getById(boundTaskId);
        validator.validateInspectionBinding(bound);
        InspectionTaskBinding binding = new InspectionTaskBinding()
                .setInspectionTaskId(inspectionTaskId)
                .setBoundTaskId(boundTaskId);
        bindingService.save(binding);
    }

    /** 添加条码规则，含校验 */
    public void addBarcodeRule(BarCodeMatchingRule rule) {
        validator.validateKeyCharLength(rule);
        if (BarCodeRuleType.PRODUCT_TRACE.getCode() == rule.getRuleType()) {
            validator.validateProductTraceUnique(rule.getProductTaskId(), rule.getId());
        }
        barcodeRuleService.saveOrUpdate(rule);
    }

    @Transactional
    public void cascadeDelete(Long taskId) {
        // Collect all side IDs and bolt IDs for batch deletion
        List<Long> sideIds = sideService.lambdaQuery()
                .select(ProductSide::getId)
                .eq(ProductSide::getProductTaskId, taskId)
                .list().stream().map(ProductSide::getId).toList();

        if (!sideIds.isEmpty()) {
            sideService.deleteBoltsBySideIds(sideIds);
            sideService.lambdaUpdate().in(ProductSide::getId, sideIds).remove();
        }

        prerequisiteService.lambdaUpdate()
                .eq(TaskPrerequisite::getTaskId, taskId).or()
                .eq(TaskPrerequisite::getPrerequisiteTaskId, taskId).remove();
        bindingService.lambdaUpdate()
                .eq(InspectionTaskBinding::getInspectionTaskId, taskId).or()
                .eq(InspectionTaskBinding::getBoundTaskId, taskId).remove();
        barcodeRuleService.lambdaUpdate()
                .eq(BarCodeMatchingRule::getProductTaskId, taskId).remove();
        removeById(taskId);
    }
}
JAVA

cat > src/main/java/com/tightening/service/TaskPrerequisiteService.java << 'JAVA'
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.TaskPrerequisite;
import com.tightening.mapper.TaskPrerequisiteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TaskPrerequisiteService extends ServiceImpl<TaskPrerequisiteMapper, TaskPrerequisite> {}
JAVA

cat > src/main/java/com/tightening/service/InspectionTaskBindingService.java << 'JAVA'
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.InspectionTaskBinding;
import com.tightening.mapper.InspectionTaskBindingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InspectionTaskBindingService extends ServiceImpl<InspectionTaskBindingMapper, InspectionTaskBinding> {}
JAVA

cat > src/main/java/com/tightening/service/BarCodeMatchingRuleService.java << 'JAVA'
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.mapper.BarCodeMatchingRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BarCodeMatchingRuleService extends ServiceImpl<BarCodeMatchingRuleMapper, BarCodeMatchingRule> {}
JAVA

cat > src/main/java/com/tightening/service/ProductSideService.java << 'JAVA'
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.ImageType;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductSide;
import com.tightening.mapper.ProductSideMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSideService extends ServiceImpl<ProductSideMapper, ProductSide> {
    private final ProductBoltService boltService;

    public List<Long> listSideIdsByTaskId(Long taskId) {
        return lambdaQuery()
                .select(ProductSide::getId)
                .eq(ProductSide::getProductTaskId, taskId)
                .eq(ProductSide::getDeleted, 0)
                .list().stream().map(ProductSide::getId).toList();
    }

    public List<ProductSide> listByTaskId(Long taskId) {
        return lambdaQuery()
                .select(ProductSide.class, info ->
                        !info.getColumn().equals("image_data")
                                && !info.getColumn().equals("rendered_image_data")
                                && !info.getColumn().equals("thumbnail_data"))
                .eq(ProductSide::getProductTaskId, taskId)
                .eq(ProductSide::getDeleted, 0)
                .list();
    }

    @Transactional
    public void cascadeDelete(Long sideId) {
        boltService.deleteBoltsBySideIds(List.of(sideId));
        removeById(sideId);
    }

    public void deleteBoltsBySideIds(List<Long> sideIds) {
        boltService.deleteBoltsBySideIds(sideIds);
    }

    public byte[] getImageData(Long sideId, ImageType type) {
        ProductSide side = getById(sideId);
        if (side == null) return null;
        return switch (type) {
            case ORIGINAL -> side.getImageData();
            case THUMBNAIL -> side.getThumbnailData();
            case RENDERED -> side.getRenderedImageData();
        };
    }

    public void updateImageData(Long sideId, byte[] data) {
        lambdaUpdate().eq(ProductSide::getId, sideId).set(ProductSide::getImageData, data).update();
    }

    public void updateRenderedImageData(Long sideId, byte[] data) {
        lambdaUpdate().eq(ProductSide::getId, sideId).set(ProductSide::getRenderedImageData, data).update();
    }

    public void updateThumbnailData(Long sideId, byte[] data) {
        lambdaUpdate().eq(ProductSide::getId, sideId).set(ProductSide::getThumbnailData, data).update();
    }
}
JAVA

cat > src/main/java/com/tightening/service/ProductBoltService.java << 'JAVA'
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductSide;
import com.tightening.mapper.ProductBoltMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class ProductBoltService extends ServiceImpl<ProductBoltMapper, ProductBolt> {
    private final BoltDeviceBindingService deviceBindingService;
    private final BoltPartsBarcodeService partsBarcodeService;
    private final ProductSideService sideService;

    public ProductBoltService(BoltDeviceBindingService deviceBindingService,
                              BoltPartsBarcodeService partsBarcodeService,
                              @Lazy ProductSideService sideService) {
        this.deviceBindingService = deviceBindingService;
        this.partsBarcodeService = partsBarcodeService;
        this.sideService = sideService;
    }

    public void saveBolt(ProductBolt entity, Long productTaskId) {
        validateBoltSerialNumUnique(entity, productTaskId);
        saveOrUpdate(entity);
    }

    private void validateBoltSerialNumUnique(ProductBolt entity, Long productTaskId) {
        Set<Long> sideIds = new java.util.HashSet<>(sideService.listSideIdsByTaskId(productTaskId));
        if (entity.getProductSideId() != null) sideIds.add(entity.getProductSideId());
        if (sideIds.isEmpty()) return;
        long count = lambdaQuery()
                .in(ProductBolt::getProductSideId, sideIds)
                .eq(ProductBolt::getBoltSerialNum, entity.getBoltSerialNum())
                .eq(ProductBolt::getDeleted, 0)
                .ne(entity.getId() != null, ProductBolt::getId, entity.getId())
                .count();
        if (count > 0) throw new IllegalArgumentException(
                "bolt_serial_num " + entity.getBoltSerialNum() + " 在当前 task 中已存在");
    }

    public List<ProductBolt> listBySideId(Long sideId) {
        return lambdaQuery()
                .eq(ProductBolt::getProductSideId, sideId)
                .eq(ProductBolt::getDeleted, 0)
                .orderByAsc(ProductBolt::getBoltSerialNum)
                .list();
    }

    public void deleteBoltsBySideIds(List<Long> sideIds) {
        if (sideIds.isEmpty()) return;
        List<Long> boltIds = lambdaQuery()
                .select(ProductBolt::getId)
                .in(ProductBolt::getProductSideId, sideIds)
                .list().stream().map(ProductBolt::getId).toList();
        if (!boltIds.isEmpty()) {
            deviceBindingService.lambdaUpdate().in(BoltDeviceBinding::getProductBoltId, boltIds).remove();
            partsBarcodeService.lambdaUpdate().in(BoltPartsBarcode::getProductBoltId, boltIds).remove();
            lambdaUpdate().in(ProductBolt::getId, boltIds).remove();
        }
    }

    @Transactional
    public void cascadeDelete(Long boltId) {
        deviceBindingService.lambdaUpdate()
                .eq(BoltDeviceBinding::getProductBoltId, boltId)
                .remove();
        partsBarcodeService.lambdaUpdate()
                .eq(BoltPartsBarcode::getProductBoltId, boltId)
                .remove();
        removeById(boltId);
    }
}
JAVA

cat > src/main/java/com/tightening/service/BoltDeviceBindingService.java << 'JAVA'
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.BoltDeviceBinding;
import com.tightening.mapper.BoltDeviceBindingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BoltDeviceBindingService extends ServiceImpl<BoltDeviceBindingMapper, BoltDeviceBinding> {}
JAVA

cat > src/main/java/com/tightening/service/BoltPartsBarcodeService.java << 'JAVA'
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.BoltPartsBarcode;
import com.tightening.mapper.BoltPartsBarcodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BoltPartsBarcodeService extends ServiceImpl<BoltPartsBarcodeMapper, BoltPartsBarcode> {}
JAVA
```

- [ ] **Step 2: 启动验证 Spring 可注入所有 Service**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev &
sleep 5
curl -s http://localhost:8080/api/login -X POST -H "Content-Type: application/json" -d '{"username":"test","password":"test"}' || true
kill %1 2>/dev/null
```

预期：无 Bean 注入错误。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/tightening/service/
git commit -m "feat: add task config services"
```

### Task 19: TaskConfigValidator + 测试

**Files:**
- Create: `src/main/java/com/tightening/service/TaskConfigValidator.java`
- Create: `src/test/java/com/tightening/service/TaskConfigValidatorTest.java`

- [ ] **Step 1: 创建校验器**

```java
package com.tightening.service;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.PrerequisiteType;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.TaskPrerequisite;
import com.tightening.entity.ProductTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class TaskConfigValidator {

    private final TaskPrerequisiteService prerequisiteService;
    private final BarCodeMatchingRuleService barcodeRuleService;

    public TaskConfigValidator(TaskPrerequisiteService prerequisiteService,
                                  BarCodeMatchingRuleService barcodeRuleService) {
        this.prerequisiteService = prerequisiteService;
        this.barcodeRuleService = barcodeRuleService;
    }

    /** 校验循环依赖——BFS 广度遍历所有可达路径（不限定单链），在保存前置关系前调用 */
    public void validateNoCircularDependency(Long taskId, Long prerequisiteTaskId) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(prerequisiteTaskId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!visited.add(current)) continue;
            if (current.equals(taskId)) {
                throw new IllegalArgumentException("检测到循环依赖: task " + taskId + " 不能依赖自身");
            }
            prerequisiteService.lambdaQuery()
                    .select(TaskPrerequisite::getPrerequisiteTaskId)
                    .eq(TaskPrerequisite::getTaskId, current)
                    .eq(TaskPrerequisite::getDeleted, 0)
                    .list().stream()
                    .map(TaskPrerequisite::getPrerequisiteTaskId)
                    .forEach(queue::add);
        }
    }

    /** 校验前置任务类型约束（target 由调用方查询传入，避免循环依赖） */
    public void validatePrerequisiteType(ProductTask target, Integer prerequisiteType) {
        if (target == null) return;
        boolean isInspection = Integer.valueOf(1).equals(target.getIsInspection());
        if (Integer.valueOf(PrerequisiteType.INSPECTION_CHAIN.getCode()).equals(prerequisiteType) && !isInspection) {
            throw new IllegalArgumentException("INSPECTION_CHAIN 的前置任务必须是点检任务 (is_inspection=1)");
        }
        if (!Integer.valueOf(PrerequisiteType.INSPECTION_CHAIN.getCode()).equals(prerequisiteType) && isInspection) {
            throw new IllegalArgumentException("SAME_TRACE/PARTS_TRACE 的前置任务必须是普通任务 (is_inspection=0)");
        }
    }

    /** 校验点检绑定目标必须是普通任务（boundTask 由调用方查询传入，避免循环依赖） */
    public void validateInspectionBinding(ProductTask boundTask) {
        if (boundTask != null && Integer.valueOf(1).equals(boundTask.getIsInspection())) {
            throw new IllegalArgumentException("点检任务不能绑定到另一个点检任务");
        }
    }

    /** 校验 key_char 长度与 position 范围匹配 */
    public void validateKeyCharLength(BarCodeMatchingRule rule) {
        if (rule.getKeyStartPosition() == null || rule.getKeyChar() == null) return;
        int expectedLen;
        if (rule.getKeyEndPosition() != null) {
            expectedLen = rule.getKeyEndPosition() - rule.getKeyStartPosition() + 1;
        } else {
            expectedLen = 1;
        }
        if (rule.getKeyChar().length() != expectedLen) {
            throw new IllegalArgumentException(
                "key_char 长度(" + rule.getKeyChar().length() + ")与位置范围(" + expectedLen + ")不匹配");
        }
    }

    /** 校验追溯码唯一——每个 task 最多一条 PRODUCT_TRACE */
    public void validateProductTraceUnique(Long productTaskId, Long ruleId) {
        long count = barcodeRuleService.lambdaQuery()
                .eq(BarCodeMatchingRule::getProductTaskId, productTaskId)
                .eq(BarCodeMatchingRule::getRuleType, BarCodeRuleType.PRODUCT_TRACE.getCode())
                .eq(BarCodeMatchingRule::getDeleted, 0)
                .ne(ruleId != null, BarCodeMatchingRule::getId, ruleId)
                .count();
        if (count > 0) {
            throw new IllegalArgumentException("该 task 已存在 PRODUCT_TRACE 规则，每个 task 最多一条");
        }
    }
}
```

注意：`TaskConfigValidator` 仅使用 `TaskPrerequisiteService` 和 `BarCodeMatchingRuleService`，避免与服务层产生循环依赖。需要 `ProductTask` 数据的校验方法（如 `validatePrerequisiteType`、`validateInspectionBinding`）由调用方查询后传入实体；`validateBoltSerialNumUnique` 逻辑已内联到 `ProductBoltService` 中。

- [ ] **Step 2: 创建测试——用 mock 覆盖全部 4 个校验方法（validateBoltSerialNumUnique 已内联到 ProductBoltService）**

```java
package com.tightening.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.tightening.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskConfigValidatorTest {

    @Mock private TaskPrerequisiteService prerequisiteService;
    @Mock private BarCodeMatchingRuleService barcodeRuleService;

    private TaskConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TaskConfigValidator(prerequisiteService, barcodeRuleService);
    }

    // ---- key_char 长度校验（纯逻辑，不依赖 mock） ----

    @Test
    void validateKeyCharLength_singleChar_shouldPass() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule();
        rule.setKeyStartPosition(6);
        rule.setKeyEndPosition(null);
        rule.setKeyChar("A");
        assertThatCode(() -> validator.validateKeyCharLength(rule)).doesNotThrowAnyException();
    }

    @Test
    void validateKeyCharLength_range_shouldPass() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule();
        rule.setKeyStartPosition(6);
        rule.setKeyEndPosition(9);
        rule.setKeyChar("ABCD");
        assertThatCode(() -> validator.validateKeyCharLength(rule)).doesNotThrowAnyException();
    }

    @Test
    void validateKeyCharLength_mismatch_shouldThrow() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule();
        rule.setKeyStartPosition(6);
        rule.setKeyEndPosition(9);
        rule.setKeyChar("AB");
        assertThatThrownBy(() -> validator.validateKeyCharLength(rule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不匹配");
    }

    @Test
    void validateKeyCharLength_noKeyChar_shouldNotThrow() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule();
        rule.setKeyStartPosition(6);
        rule.setKeyChar(null);
        assertThatCode(() -> validator.validateKeyCharLength(rule)).doesNotThrowAnyException();
    }

    // ---- 前置类型约束（target 由调用方传入，不再查询 DB） ----

    @Test
    void validatePrerequisiteType_inspectionChainToNormalTask_shouldThrow() {
        ProductTask normalTask = new ProductTask();
        normalTask.setIsInspection(0);

        assertThatThrownBy(() -> validator.validatePrerequisiteType(normalTask, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INSPECTION_CHAIN");
    }

    @Test
    void validatePrerequisiteType_inspectionChainToInspectionTask_shouldPass() {
        ProductTask inspectionTask = new ProductTask();
        inspectionTask.setIsInspection(1);

        assertThatCode(() -> validator.validatePrerequisiteType(inspectionTask, 3))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePrerequisiteType_sameTraceToInspectionTask_shouldThrow() {
        ProductTask inspectionTask = new ProductTask();
        inspectionTask.setIsInspection(1);

        assertThatThrownBy(() -> validator.validatePrerequisiteType(inspectionTask, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SAME_TRACE");
    }

    @Test
    void validatePrerequisiteType_sameTraceToNormalTask_shouldPass() {
        ProductTask normalTask = new ProductTask();
        normalTask.setIsInspection(0);

        assertThatCode(() -> validator.validatePrerequisiteType(normalTask, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePrerequisiteType_targetNotFound_shouldNotThrow() {
        assertThatCode(() -> validator.validatePrerequisiteType(null, 1))
                .doesNotThrowAnyException();
    }

    // ---- 点检绑定约束（boundTask 由调用方传入，不再查询 DB） ----

    @Test
    void validateInspectionBinding_toInspectionTask_shouldThrow() {
        ProductTask inspection = new ProductTask();
        inspection.setIsInspection(1);

        assertThatThrownBy(() -> validator.validateInspectionBinding(inspection))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("点检任务不能绑定到另一个点检任务");
    }

    @Test
    void validateInspectionBinding_toNormalTask_shouldPass() {
        ProductTask normal = new ProductTask();
        normal.setIsInspection(0);

        assertThatCode(() -> validator.validateInspectionBinding(normal))
                .doesNotThrowAnyException();
    }

    @Test
    void validateInspectionBinding_nullTarget_shouldPass() {
        assertThatCode(() -> validator.validateInspectionBinding(null))
                .doesNotThrowAnyException();
    }

    // ---- 追溯码唯一 ----

    @SuppressWarnings("unchecked")
    private LambdaQueryChainWrapper<BarCodeMatchingRule> mockBarcodeChain(long returnCount) {
        LambdaQueryChainWrapper<BarCodeMatchingRule> chain = mock(LambdaQueryChainWrapper.class);
        when(chain.eq(any(), any())).thenReturn(chain);
        when(chain.ne(anyBoolean(), any(), any())).thenReturn(chain);
        when(chain.count()).thenReturn(returnCount);
        return chain;
    }

    @Test
    void validateProductTraceUnique_alreadyExists_shouldThrow() {
        when(barcodeRuleService.lambdaQuery()).thenReturn(mockBarcodeChain(1L));

        assertThatThrownBy(() -> validator.validateProductTraceUnique(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在 PRODUCT_TRACE");
    }

    @Test
    void validateProductTraceUnique_noConflict_shouldNotThrow() {
        when(barcodeRuleService.lambdaQuery()).thenReturn(mockBarcodeChain(0L));

        assertThatCode(() -> validator.validateProductTraceUnique(1L, 1L))
                .doesNotThrowAnyException();
    }

    // ---- 循环依赖 ----

    @Test
    void validateNoCircularDependency_directSelfLoop_shouldThrow() {
        // 自引用：prerequisiteTaskId == taskId 在 lambdaQuery 调用前就会触发
        assertThatThrownBy(() -> validator.validateNoCircularDependency(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能依赖自身");
    }

    @SuppressWarnings("unchecked")
    @Test
    void validateNoCircularDependency_noChain_shouldNotThrow() {
        LambdaQueryChainWrapper<TaskPrerequisite> chain = mock(LambdaQueryChainWrapper.class);
        when(chain.eq(any(), any())).thenReturn(chain);
        when(chain.list()).thenReturn(List.of());
        when(prerequisiteService.lambdaQuery()).thenReturn(chain);

        assertThatCode(() -> validator.validateNoCircularDependency(1L, 2L))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=TaskConfigValidatorTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/service/TaskConfigValidator.java src/test/java/com/tightening/service/TaskConfigValidatorTest.java
git commit -m "feat: add TaskConfigValidator with keyChar and prerequisite validation"
```

---

## Phase 7: Controller

所有 Controller 使用 `@RequestMapping("api/...")`，遵循 RESTful 风格的 HTTP 方法（GET 查询、POST 创建、PUT 更新、DELETE 删除），返回同步 `ResponseEntity`（仅本地 CRUD，无异步 I/O）。测试使用纯 Mockito（`@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`），与标准 Mockito 测试模式一致。

### Task 20: ProductTaskController + 测试

**Files:**
- Create: `src/main/java/com/tightening/controller/ProductTaskController.java`
- Create: `src/test/java/com/tightening/controller/ProductTaskControllerTest.java`

- [ ] **Step 1: 创建 Controller**

```java
package com.tightening.controller;

import com.tightening.dto.BarCodeMatchingRuleDTO;
import com.tightening.dto.ProductTaskDTO;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.InspectionTaskBinding;
import com.tightening.entity.TaskPrerequisite;
import com.tightening.entity.ProductTask;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.InspectionTaskBindingService;
import com.tightening.service.TaskPrerequisiteService;
import com.tightening.service.ProductTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/tasks")
@RequiredArgsConstructor
public class ProductTaskController {

    private final ProductTaskService taskService;
    private final TaskPrerequisiteService prerequisiteService;
    private final InspectionTaskBindingService bindingService;
    private final BarCodeMatchingRuleService barcodeRuleService;

    record PrerequisiteRequest(Long prerequisiteTaskId, Integer prerequisiteType) {}
    record InspectionBindingRequest(Long boundTaskId) {}

    @GetMapping
    public ResponseEntity<List<ProductTask>> list(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(taskService.lambdaQuery()
                .eq(ProductTask::getDeleted, 0)
                .orderByDesc(ProductTask::getId)
                .last("LIMIT " + size + " OFFSET " + ((page - 1) * size))
                .list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductTask> get(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getById(id));
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody ProductTaskDTO dto) {
        ProductTask entity = new ProductTask();
        entity.setName(dto.getName());
        entity.setMaxNgCount(dto.getMaxNgCount());
        entity.setPasswordRequiredAfterNg(dto.getPasswordRequiredAfterNg());
        entity.setEnabled(dto.getEnabled());
        entity.setMultiDeviceIndependent(dto.getMultiDeviceIndependent());
        entity.setSkipScrew(dto.getSkipScrew());
        entity.setIsInspection(dto.getIsInspection());
        entity.setInspectionScope(dto.getInspectionScope());
        taskService.saveOrUpdate(entity);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody ProductTaskDTO dto) {
        ProductTask entity = new ProductTask();
        entity.setId(id);
        entity.setName(dto.getName());
        entity.setMaxNgCount(dto.getMaxNgCount());
        entity.setPasswordRequiredAfterNg(dto.getPasswordRequiredAfterNg());
        entity.setEnabled(dto.getEnabled());
        entity.setMultiDeviceIndependent(dto.getMultiDeviceIndependent());
        entity.setSkipScrew(dto.getSkipScrew());
        entity.setIsInspection(dto.getIsInspection());
        entity.setInspectionScope(dto.getInspectionScope());
        taskService.saveOrUpdate(entity);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        taskService.cascadeDelete(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{taskId}/prerequisites")
    public ResponseEntity<String> addPrerequisite(@PathVariable Long taskId,
                                                   @RequestBody PrerequisiteRequest request) {
        taskService.addPrerequisite(taskId, request.prerequisiteTaskId(), request.prerequisiteType());
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/{taskId}/prerequisites")
    public ResponseEntity<List<TaskPrerequisite>> listPrerequisites(@PathVariable Long taskId) {
        return ResponseEntity.ok(prerequisiteService.lambdaQuery()
                .eq(TaskPrerequisite::getTaskId, taskId)
                .eq(TaskPrerequisite::getDeleted, 0)
                .list());
    }

    @DeleteMapping("/{taskId}/prerequisites/{id}")
    public ResponseEntity<String> deletePrerequisite(@PathVariable Long taskId, @PathVariable Long id) {
        prerequisiteService.removeById(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{inspectionTaskId}/inspection-bindings")
    public ResponseEntity<String> addInspectionBinding(@PathVariable Long inspectionTaskId,
                                                        @RequestBody InspectionBindingRequest request) {
        taskService.addInspectionBinding(inspectionTaskId, request.boundTaskId());
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/{taskId}/inspection-bindings")
    public ResponseEntity<List<InspectionTaskBinding>> listInspectionBindings(@PathVariable Long taskId) {
        return ResponseEntity.ok(bindingService.lambdaQuery()
                .eq(InspectionTaskBinding::getInspectionTaskId, taskId)
                .eq(InspectionTaskBinding::getDeleted, 0)
                .list());
    }

    @DeleteMapping("/{taskId}/inspection-bindings/{id}")
    public ResponseEntity<String> deleteInspectionBinding(@PathVariable Long taskId, @PathVariable Long id) {
        bindingService.removeById(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{taskId}/barcode-rules")
    public ResponseEntity<String> addBarcodeRule(@PathVariable Long taskId,
                                                  @RequestBody BarCodeMatchingRuleDTO dto) {
        BarCodeMatchingRule entity = new BarCodeMatchingRule();
        if (dto.getId() != null) entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setProductTaskId(dto.getProductTaskId());
        entity.setRuleType(dto.getRuleType());
        entity.setPartNumber(dto.getPartNumber());
        entity.setExpectedLength(dto.getExpectedLength());
        entity.setKeyStartPosition(dto.getKeyStartPosition());
        entity.setKeyEndPosition(dto.getKeyEndPosition());
        entity.setKeyChar(dto.getKeyChar());
        taskService.addBarcodeRule(entity);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @GetMapping("/{taskId}/barcode-rules")
    public ResponseEntity<List<BarCodeMatchingRule>> listBarcodeRules(@PathVariable Long taskId) {
        return ResponseEntity.ok(barcodeRuleService.lambdaQuery()
                .eq(BarCodeMatchingRule::getProductTaskId, taskId)
                .eq(BarCodeMatchingRule::getDeleted, 0)
                .list());
    }

    @DeleteMapping("/{taskId}/barcode-rules/{id}")
    public ResponseEntity<String> deleteBarcodeRule(@PathVariable Long taskId, @PathVariable Long id) {
        barcodeRuleService.removeById(id);
        return ResponseEntity.ok("ok");
    }
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.tightening.dto.ProductTaskDTO;
import com.tightening.entity.ProductTask;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.InspectionTaskBindingService;
import com.tightening.service.TaskPrerequisiteService;
import com.tightening.service.ProductTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductTaskControllerTest {

    @Mock private ProductTaskService taskService;
    @Mock private TaskPrerequisiteService prerequisiteService;
    @Mock private InspectionTaskBindingService bindingService;
    @Mock private BarCodeMatchingRuleService barcodeRuleService;
    @InjectMocks private ProductTaskController controller;

    @SuppressWarnings("unchecked")
    @Test
    void list_shouldReturnOk() {
        LambdaQueryChainWrapper<ProductTask> mockChain = mock(LambdaQueryChainWrapper.class);
        when(taskService.lambdaQuery()).thenReturn(mockChain);
        when(mockChain.eq(any(), any())).thenReturn(mockChain);
        when(mockChain.orderByDesc(any())).thenReturn(mockChain);
        when(mockChain.last(any())).thenReturn(mockChain);
        when(mockChain.list()).thenReturn(List.of());

        ResponseEntity<?> result = controller.list(1, 100);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void create_shouldReturnOk() {
        ProductTaskDTO dto = new ProductTaskDTO();
        dto.setName("Test");
        dto.setMaxNgCount(3);

        ResponseEntity<String> result = controller.create(dto);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void delete_shouldCallCascadeDeleteAndReturnOk() {
        ResponseEntity<String> result = controller.delete(1L);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=ProductTaskControllerTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/controller/ProductTaskController.java src/test/java/com/tightening/controller/ProductTaskControllerTest.java
git commit -m "feat: add ProductTaskController"
```

### Task 21: ProductSideController + 图片接口 + 测试

**Files:**
- Create: `src/main/java/com/tightening/controller/ProductSideController.java`
- Create: `src/test/java/com/tightening/controller/ProductSideControllerTest.java`

- [ ] **Step 1: 创建 Controller**

```java
package com.tightening.controller;

import com.tightening.constant.ImageType;
import com.tightening.dto.ProductSideDTO;
import com.tightening.entity.ProductSide;
import com.tightening.service.ProductSideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("api/sides")
@RequiredArgsConstructor
public class ProductSideController {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, "image/gif", "image/webp");
    private final ProductSideService sideService;

    @GetMapping
    public ResponseEntity<List<ProductSide>> list(@RequestParam Long taskId) {
        return ResponseEntity.ok(sideService.listByTaskId(taskId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductSide> get(@PathVariable Long id) {
        return ResponseEntity.ok(sideService.getById(id));
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody ProductSideDTO dto) {
        ProductSide entity = new ProductSide();
        entity.setProductTaskId(dto.getProductTaskId());
        entity.setName(dto.getName());
        sideService.saveOrUpdate(entity);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody ProductSideDTO dto) {
        ProductSide entity = new ProductSide();
        entity.setId(id);
        entity.setProductTaskId(dto.getProductTaskId());
        entity.setName(dto.getName());
        sideService.saveOrUpdate(entity);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        sideService.cascadeDelete(id);
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/{sideId}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long sideId,
                                           @RequestParam(defaultValue = "rendered") String type) {
        ImageType imageType = ImageType.fromValue(type).orElse(ImageType.RENDERED);
        byte[] data = sideService.getImageData(sideId, imageType);
        if (data == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .body(data);
    }

    @PutMapping("/{sideId}/image")
    public ResponseEntity<String> uploadImage(@PathVariable Long sideId,
                                              @RequestParam MultipartFile file) throws IOException {
        return uploadImageData(sideId, file, ImageType.ORIGINAL);
    }

    @PutMapping("/{sideId}/image/rendered")
    public ResponseEntity<String> uploadRenderedImage(@PathVariable Long sideId,
                                                      @RequestParam MultipartFile file) throws IOException {
        return uploadImageData(sideId, file, ImageType.RENDERED);
    }

    @PutMapping("/{sideId}/image/thumbnail")
    public ResponseEntity<String> uploadThumbnail(@PathVariable Long sideId,
                                                   @RequestParam MultipartFile file) throws IOException {
        return uploadImageData(sideId, file, ImageType.THUMBNAIL);
    }

    private ResponseEntity<String> uploadImageData(Long sideId, MultipartFile file,
                                                   ImageType imageType) throws IOException {
        if (file.getSize() > MAX_IMAGE_SIZE)
            return ResponseEntity.badRequest().body("图片大小超过 5MB 限制");
        if (file.getContentType() == null || !ALLOWED_IMAGE_TYPES.contains(file.getContentType()))
            return ResponseEntity.badRequest().body("不支持的文件类型");
        if (sideService.getById(sideId) == null) return ResponseEntity.notFound().build();
        byte[] data = file.getBytes();
        switch (imageType) {
            case ORIGINAL -> sideService.updateImageData(sideId, data);
            case RENDERED -> sideService.updateRenderedImageData(sideId, data);
            case THUMBNAIL -> sideService.updateThumbnailData(sideId, data);
        }
        return ResponseEntity.ok("ok");
    }
}
```

注意：`ProductSideService.listByTaskId`（排除 BLOB 列）和 `deleteBoltsBySideIds` 在 Task 18 中已添加。

- [ ] **Step 2: 创建测试**

```java
package com.tightening.controller;

import com.tightening.dto.ProductSideDTO;
import com.tightening.entity.ProductSide;
import com.tightening.service.ProductSideService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSideControllerTest {

    @Mock private ProductSideService sideService;
    @InjectMocks private ProductSideController controller;

    @Test
    void list_shouldReturnOk() {
        when(sideService.listByTaskId(1L)).thenReturn(List.of());
        ResponseEntity<List<ProductSide>> result = controller.list(1L);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void create_shouldReturnOk() {
        ProductSideDTO dto = new ProductSideDTO();
        dto.setName("Side A");
        dto.setProductTaskId(1L);

        when(sideService.saveOrUpdate(any())).thenReturn(true);

        ResponseEntity<String> result = controller.create(dto);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void delete_shouldCallCascadeDeleteAndReturnOk() {
        ResponseEntity<String> result = controller.delete(1L);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void getImage_notFound_shouldReturn404() {
        when(sideService.getById(999L)).thenReturn(null);
        ResponseEntity<byte[]> result = controller.getImage(999L, "rendered");
        assertThat(result.getStatusCode().is4xxClientError()).isTrue();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=ProductSideControllerTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/controller/ProductSideController.java src/test/java/com/tightening/controller/ProductSideControllerTest.java
git commit -m "feat: add ProductSideController with image endpoints"
```

### Task 22: ProductBoltController + 测试

**Files:**
- Create: `src/main/java/com/tightening/controller/ProductBoltController.java`
- Create: `src/test/java/com/tightening/controller/ProductBoltControllerTest.java`

- [ ] **Step 1: 创建 Controller**

```java
package com.tightening.controller;

import com.tightening.dto.ProductBoltDTO;
import com.tightening.entity.ProductBolt;
import com.tightening.service.ProductBoltService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/bolts")
@RequiredArgsConstructor
public class ProductBoltController {

    private final ProductBoltService boltService;

    @GetMapping
    public ResponseEntity<List<ProductBolt>> list(@RequestParam Long sideId) {
        return ResponseEntity.ok(boltService.listBySideId(sideId));
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody ProductBoltDTO dto,
                                          @RequestParam Long taskId) {
        ProductBolt entity = new ProductBolt();
        entity.setProductSideId(dto.getProductSideId());
        entity.setBoltSerialNum(dto.getBoltSerialNum());
        entity.setBoltName(dto.getBoltName());
        entity.setParameterSetId(dto.getParameterSetId());
        entity.setTorqueMin(dto.getTorqueMin());
        entity.setTorqueMax(dto.getTorqueMax());
        entity.setAngleMin(dto.getAngleMin());
        entity.setAngleMax(dto.getAngleMax());
        entity.setArmLocation(dto.getArmLocation());
        entity.setLocationXPercent(dto.getLocationXPercent());
        entity.setLocationYPercent(dto.getLocationYPercent());
        entity.setEnabled(dto.getEnabled());
        boltService.saveBolt(entity, taskId);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody ProductBoltDTO dto,
                                          @RequestParam Long taskId) {
        ProductBolt entity = new ProductBolt();
        entity.setId(id);
        entity.setProductSideId(dto.getProductSideId());
        entity.setBoltSerialNum(dto.getBoltSerialNum());
        entity.setBoltName(dto.getBoltName());
        entity.setParameterSetId(dto.getParameterSetId());
        entity.setTorqueMin(dto.getTorqueMin());
        entity.setTorqueMax(dto.getTorqueMax());
        entity.setAngleMin(dto.getAngleMin());
        entity.setAngleMax(dto.getAngleMax());
        entity.setArmLocation(dto.getArmLocation());
        entity.setLocationXPercent(dto.getLocationXPercent());
        entity.setLocationYPercent(dto.getLocationYPercent());
        entity.setEnabled(dto.getEnabled());
        boltService.saveBolt(entity, taskId);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        boltService.cascadeDelete(id);
        return ResponseEntity.ok("ok");
    }
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.controller;

import com.tightening.dto.ProductBoltDTO;
import com.tightening.entity.ProductBolt;
import com.tightening.service.ProductBoltService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductBoltControllerTest {

    @Mock private ProductBoltService boltService;
    @InjectMocks private ProductBoltController controller;

    @Test
    void list_shouldReturnOk() {
        when(boltService.listBySideId(1L)).thenReturn(List.of());
        ResponseEntity<List<ProductBolt>> result = controller.list(1L);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void create_shouldReturnOk() {
        ProductBoltDTO dto = new ProductBoltDTO();
        dto.setProductSideId(1L);
        dto.setBoltSerialNum(1);
        dto.setTorqueMin(5.0);
        dto.setTorqueMax(25.0);

        ResponseEntity<String> result = controller.create(dto, 1L);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void delete_shouldCallCascadeDeleteAndReturnOk() {
        ResponseEntity<String> result = controller.delete(1L);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=ProductBoltControllerTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/controller/ProductBoltController.java src/test/java/com/tightening/controller/ProductBoltControllerTest.java
git commit -m "feat: add ProductBoltController"
```

- [ ] **Step 5: 运行全部测试确保无回归**

```bash
mvn test -q
```

预期：全部测试通过（含新增和已有测试）。

---

## 完成检查清单

- [ ] 5 个枚举 + 测试全部通过
- [ ] 3 个 SQL 迁移可正常执行
- [ ] 8 个 Entity + 测试全部通过
- [ ] 8 个 DTO + 3 个代表性测试全部通过
- [ ] 8 个 Mapper，应用启动无错误
- [ ] 8 个 Service + Validator，应用启动无错误
- [ ] Validator 纯逻辑测试全部通过
- [ ] 3 个 Controller + 测试全部通过
- [ ] 全量测试无回归
