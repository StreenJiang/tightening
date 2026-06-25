# Mission 配置表实施计划

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

### Task 5: V1.0.8 — product_mission + 关联表

**Files:**
- Create: `src/main/resources/db/migration/V1.0.8__add_table_product_mission.sql`

- [ ] **Step 1: 创建迁移文件**

```sql
CREATE TABLE product_mission (
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

CREATE TABLE mission_prerequisite (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    mission_id                  INTEGER NOT NULL,
    prerequisite_mission_id     INTEGER NOT NULL,
    prerequisite_type           INTEGER NOT NULL,
    deleted                     INTEGER DEFAULT 0,
    creator_id                  INTEGER,
    modifier_id                 INTEGER,
    create_time                 TEXT,
    modify_time                 TEXT
);

CREATE INDEX idx_mission_prerequisite_mission_id ON mission_prerequisite(mission_id);
CREATE INDEX idx_mission_prerequisite_prerequisite_mission_id ON mission_prerequisite(prerequisite_mission_id);

CREATE TABLE inspection_mission_binding (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    inspection_mission_id       INTEGER NOT NULL,
    bound_mission_id            INTEGER NOT NULL,
    deleted                     INTEGER DEFAULT 0,
    creator_id                  INTEGER,
    modifier_id                 INTEGER,
    create_time                 TEXT,
    modify_time                 TEXT
);

CREATE INDEX idx_inspection_mission_binding_inspection_mission_id ON inspection_mission_binding(inspection_mission_id);
CREATE INDEX idx_inspection_mission_binding_bound_mission_id ON inspection_mission_binding(bound_mission_id);

CREATE TABLE bar_code_matching_rule (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    name                  TEXT,
    product_mission_id    INTEGER,
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

CREATE INDEX idx_bar_code_matching_rule_product_mission_id ON bar_code_matching_rule(product_mission_id);
```

- [ ] **Step 2: 验证迁移可执行**

```bash
mvn flyway:migrate -Dflyway.url=jdbc:sqlite:~/tightening_system/tightening_test.db -Dflyway.locations=filesystem:src/main/resources/db/migration -q
```

预期：Migration V1.0.8 执行成功。

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/db/migration/V1.0.8__add_table_product_mission.sql
git commit -m "feat: add product_mission, mission_prerequisite, inspection_mission_binding, bar_code_matching_rule tables"
```

### Task 6: V1.0.9 — product_side

**Files:**
- Create: `src/main/resources/db/migration/V1.0.9__add_table_product_side.sql`

- [ ] **Step 1: 创建迁移文件**

```sql
CREATE TABLE product_side (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    product_mission_id    INTEGER NOT NULL,
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

CREATE INDEX idx_product_side_product_mission_id ON product_side(product_mission_id);
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

### Task 8: ProductMission Entity + 测试

**Files:**
- Create: `src/main/java/com/tightening/entity/ProductMission.java`
- Create: `src/test/java/com/tightening/entity/ProductMissionTest.java`

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
@TableName("product_mission")
public class ProductMission extends BaseEntity {
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

class ProductMissionTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        ProductMission original = new ProductMission();
        original.setId(1L);
        original.setName("Mission A");
        original.setMaxNgCount(3);
        original.setPasswordRequiredAfterNg(2);
        original.setEnabled(1);
        original.setMultiDeviceIndependent(0);
        original.setSkipScrew(0);
        original.setIsInspection(0);
        original.setInspectionScope(null);

        String json = mapper.writeValueAsString(original);
        ProductMission restored = mapper.readValue(json, ProductMission.class);

        assertThat(restored.getName()).isEqualTo("Mission A");
        assertThat(restored.getMaxNgCount()).isEqualTo(3);
        assertThat(restored.getPasswordRequiredAfterNg()).isEqualTo(2);
        assertThat(restored.getEnabled()).isEqualTo(1);
        assertThat(restored.getSkipScrew()).isEqualTo(0);
        assertThat(restored.getIsInspection()).isEqualTo(0);
    }

    @Test
    void jsonRoundTrip_inspectionMission_shouldPreserveScope() throws Exception {
        ProductMission original = new ProductMission();
        original.setId(2L);
        original.setName("Inspection A");
        original.setIsInspection(1);
        original.setInspectionScope(2);

        String json = mapper.writeValueAsString(original);
        ProductMission restored = mapper.readValue(json, ProductMission.class);

        assertThat(restored.getIsInspection()).isEqualTo(1);
        assertThat(restored.getInspectionScope()).isEqualTo(2);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductMission());
        ProductMission restored = mapper.readValue(json, ProductMission.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=ProductMissionTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/ProductMission.java src/test/java/com/tightening/entity/ProductMissionTest.java
git commit -m "feat: add ProductMission entity"
```

### Task 9: MissionPrerequisite Entity + 测试

**Files:**
- Create: `src/main/java/com/tightening/entity/MissionPrerequisite.java`
- Create: `src/test/java/com/tightening/entity/MissionPrerequisiteTest.java`

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
@TableName("mission_prerequisite")
public class MissionPrerequisite extends BaseEntity {
    private Long missionId;
    private Long prerequisiteMissionId;
    private Integer prerequisiteType;
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MissionPrerequisiteTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        MissionPrerequisite original = new MissionPrerequisite();
        original.setId(1L);
        original.setMissionId(10L);
        original.setPrerequisiteMissionId(5L);
        original.setPrerequisiteType(1);

        String json = mapper.writeValueAsString(original);
        MissionPrerequisite restored = mapper.readValue(json, MissionPrerequisite.class);

        assertThat(restored.getMissionId()).isEqualTo(10L);
        assertThat(restored.getPrerequisiteMissionId()).isEqualTo(5L);
        assertThat(restored.getPrerequisiteType()).isEqualTo(1);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new MissionPrerequisite());
        MissionPrerequisite restored = mapper.readValue(json, MissionPrerequisite.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=MissionPrerequisiteTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/MissionPrerequisite.java src/test/java/com/tightening/entity/MissionPrerequisiteTest.java
git commit -m "feat: add MissionPrerequisite entity"
```

### Task 10: InspectionMissionBinding Entity + 测试

**Files:**
- Create: `src/main/java/com/tightening/entity/InspectionMissionBinding.java`
- Create: `src/test/java/com/tightening/entity/InspectionMissionBindingTest.java`

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
@TableName("inspection_mission_binding")
public class InspectionMissionBinding extends BaseEntity {
    private Long inspectionMissionId;
    private Long boundMissionId;
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InspectionMissionBindingTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        InspectionMissionBinding original = new InspectionMissionBinding();
        original.setId(1L);
        original.setInspectionMissionId(10L);
        original.setBoundMissionId(3L);

        String json = mapper.writeValueAsString(original);
        InspectionMissionBinding restored = mapper.readValue(json, InspectionMissionBinding.class);

        assertThat(restored.getInspectionMissionId()).isEqualTo(10L);
        assertThat(restored.getBoundMissionId()).isEqualTo(3L);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new InspectionMissionBinding());
        InspectionMissionBinding restored = mapper.readValue(json, InspectionMissionBinding.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest=InspectionMissionBindingTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/InspectionMissionBinding.java src/test/java/com/tightening/entity/InspectionMissionBindingTest.java
git commit -m "feat: add InspectionMissionBinding entity"
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
    private Long productMissionId;
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
        original.setProductMissionId(5L);
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
    private Long productMissionId;
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
        original.setProductMissionId(5L);
        original.setName("Side A");

        String json = mapper.writeValueAsString(original);
        ProductSide restored = mapper.readValue(json, ProductSide.class);

        assertThat(restored.getName()).isEqualTo("Side A");
        assertThat(restored.getProductMissionId()).isEqualTo(5L);
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
- Create: `src/main/java/com/tightening/dto/ProductMissionDTO.java`
- Create: `src/main/java/com/tightening/dto/MissionPrerequisiteDTO.java`
- Create: `src/main/java/com/tightening/dto/InspectionMissionBindingDTO.java`
- Create: `src/main/java/com/tightening/dto/BarCodeMatchingRuleDTO.java`
- Create: `src/main/java/com/tightening/dto/ProductSideDTO.java`
- Create: `src/main/java/com/tightening/dto/ProductBoltDTO.java`
- Create: `src/main/java/com/tightening/dto/BoltDeviceBindingDTO.java`
- Create: `src/main/java/com/tightening/dto/BoltPartsBarcodeDTO.java`
- Create: `src/test/java/com/tightening/dto/ProductMissionDTOTest.java`
- Create: `src/test/java/com/tightening/dto/ProductSideDTOTest.java`
- Create: `src/test/java/com/tightening/dto/ProductBoltDTOTest.java`

- [ ] **Step 1: 创建 ProductMissionDTO**

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
public class ProductMissionDTO extends BaseDTO {
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

- [ ] **Step 2: 创建 MissionPrerequisiteDTO**

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
public class MissionPrerequisiteDTO extends BaseDTO {
    private Long missionId;
    private Long prerequisiteMissionId;
    private Integer prerequisiteType;
}
```

- [ ] **Step 3: 创建 InspectionMissionBindingDTO**

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
public class InspectionMissionBindingDTO extends BaseDTO {
    private Long inspectionMissionId;
    private Long boundMissionId;
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
    private Long productMissionId;
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
    private Long productMissionId;
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

`src/test/java/com/tightening/dto/ProductMissionDTOTest.java`:
```java
package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductMissionDTOTest {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_shouldPreserveValues() throws Exception {
        ProductMissionDTO dto = new ProductMissionDTO();
        dto.setId(1L);
        dto.setName("Test");
        dto.setMaxNgCount(3);
        dto.setEnabled(1);

        String json = mapper.writeValueAsString(dto);
        ProductMissionDTO restored = mapper.readValue(json, ProductMissionDTO.class);

        assertThat(restored.getName()).isEqualTo("Test");
        assertThat(restored.getMaxNgCount()).isEqualTo(3);
    }

    @Test
    void emptyDto_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductMissionDTO());
        assertThat(mapper.readValue(json, ProductMissionDTO.class)).isNotNull();
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
        dto.setProductMissionId(5L);
        dto.setName("Side A");

        String json = mapper.writeValueAsString(dto);
        ProductSideDTO restored = mapper.readValue(json, ProductSideDTO.class);

        assertThat(restored.getName()).isEqualTo("Side A");
        assertThat(restored.getProductMissionId()).isEqualTo(5L);
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
mvn test -pl . -Dtest="ProductMissionDTOTest,ProductSideDTOTest,ProductBoltDTOTest" -DfailIfNoTests=false -q
```

- [ ] **Step 11: 提交**

```bash
git add src/main/java/com/tightening/dto/ProductMissionDTO.java \
        src/main/java/com/tightening/dto/MissionPrerequisiteDTO.java \
        src/main/java/com/tightening/dto/InspectionMissionBindingDTO.java \
        src/main/java/com/tightening/dto/BarCodeMatchingRuleDTO.java \
        src/main/java/com/tightening/dto/ProductSideDTO.java \
        src/main/java/com/tightening/dto/ProductBoltDTO.java \
        src/main/java/com/tightening/dto/BoltDeviceBindingDTO.java \
        src/main/java/com/tightening/dto/BoltPartsBarcodeDTO.java \
        src/test/java/com/tightening/dto/ProductMissionDTOTest.java \
        src/test/java/com/tightening/dto/ProductSideDTOTest.java \
        src/test/java/com/tightening/dto/ProductBoltDTOTest.java
git commit -m "feat: add mission config DTOs"
```

---

## Phase 5: Mapper

### Task 17: 全部 8 个 Mapper

**Files:**
- Create: 8 个 Mapper 文件

- [ ] **Step 1: 创建所有 Mapper**

```bash
cat > src/main/java/com/tightening/mapper/ProductMissionMapper.java << 'JAVA'
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.ProductMission;

@Mapper
public interface ProductMissionMapper extends BaseMapper<ProductMission> {}
JAVA

cat > src/main/java/com/tightening/mapper/MissionPrerequisiteMapper.java << 'JAVA'
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.MissionPrerequisite;

@Mapper
public interface MissionPrerequisiteMapper extends BaseMapper<MissionPrerequisite> {}
JAVA

cat > src/main/java/com/tightening/mapper/InspectionMissionBindingMapper.java << 'JAVA'
package com.tightening.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.InspectionMissionBinding;

@Mapper
public interface InspectionMissionBindingMapper extends BaseMapper<InspectionMissionBinding> {}
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
git commit -m "feat: add mission config mappers"
```

---

## Phase 6: Service + Validator

### Task 18: 全部 8 个 Service

**Files:**
- Create: 8 个 Service 文件

- [ ] **Step 1: 创建所有 Service**

```bash
cat > src/main/java/com/tightening/service/ProductMissionService.java << 'JAVA'
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.BarCodeRuleType;
import com.tightening.entity.ProductMission;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.ProductSide;
import com.tightening.mapper.ProductMissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMissionService extends ServiceImpl<ProductMissionMapper, ProductMission> {

    private final MissionConfigValidator validator;
    private final ProductSideService sideService;
    private final MissionPrerequisiteService prerequisiteService;
    private final InspectionMissionBindingService bindingService;
    private final BarCodeMatchingRuleService barcodeRuleService;

    /** 添加前置依赖，含校验 */
    public void addPrerequisite(Long missionId, Long prerequisiteMissionId, Integer prerequisiteType) {
        validator.validateNoCircularDependency(missionId, prerequisiteMissionId);
        ProductMission target = getById(prerequisiteMissionId);
        validator.validatePrerequisiteType(target, prerequisiteType);
        MissionPrerequisite mp = new MissionPrerequisite()
                .setMissionId(missionId)
                .setPrerequisiteMissionId(prerequisiteMissionId)
                .setPrerequisiteType(prerequisiteType);
        prerequisiteService.save(mp);
    }

    /** 添加点检绑定，含校验 */
    public void addInspectionBinding(Long inspectionMissionId, Long boundMissionId) {
        ProductMission bound = getById(boundMissionId);
        validator.validateInspectionBinding(bound);
        InspectionMissionBinding binding = new InspectionMissionBinding()
                .setInspectionMissionId(inspectionMissionId)
                .setBoundMissionId(boundMissionId);
        bindingService.save(binding);
    }

    /** 添加条码规则，含校验 */
    public void addBarcodeRule(BarCodeMatchingRule rule) {
        validator.validateKeyCharLength(rule);
        if (BarCodeRuleType.PRODUCT_TRACE.getCode() == rule.getRuleType()) {
            validator.validateProductTraceUnique(rule.getProductMissionId(), rule.getId());
        }
        barcodeRuleService.saveOrUpdate(rule);
    }

    @Transactional
    public void cascadeDelete(Long missionId) {
        // Collect all side IDs and bolt IDs for batch deletion
        List<Long> sideIds = sideService.lambdaQuery()
                .select(ProductSide::getId)
                .eq(ProductSide::getProductMissionId, missionId)
                .list().stream().map(ProductSide::getId).toList();

        if (!sideIds.isEmpty()) {
            sideService.deleteBoltsBySideIds(sideIds);
            sideService.lambdaUpdate().in(ProductSide::getId, sideIds).remove();
        }

        prerequisiteService.lambdaUpdate()
                .eq(MissionPrerequisite::getMissionId, missionId).or()
                .eq(MissionPrerequisite::getPrerequisiteMissionId, missionId).remove();
        bindingService.lambdaUpdate()
                .eq(InspectionMissionBinding::getInspectionMissionId, missionId).or()
                .eq(InspectionMissionBinding::getBoundMissionId, missionId).remove();
        barcodeRuleService.lambdaUpdate()
                .eq(BarCodeMatchingRule::getProductMissionId, missionId).remove();
        removeById(missionId);
    }
}
JAVA

cat > src/main/java/com/tightening/service/MissionPrerequisiteService.java << 'JAVA'
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.mapper.MissionPrerequisiteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MissionPrerequisiteService extends ServiceImpl<MissionPrerequisiteMapper, MissionPrerequisite> {}
JAVA

cat > src/main/java/com/tightening/service/InspectionMissionBindingService.java << 'JAVA'
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.mapper.InspectionMissionBindingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InspectionMissionBindingService extends ServiceImpl<InspectionMissionBindingMapper, InspectionMissionBinding> {}
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

    public List<Long> listSideIdsByMissionId(Long missionId) {
        return lambdaQuery()
                .select(ProductSide::getId)
                .eq(ProductSide::getProductMissionId, missionId)
                .eq(ProductSide::getDeleted, 0)
                .list().stream().map(ProductSide::getId).toList();
    }

    public List<ProductSide> listByMissionId(Long missionId) {
        return lambdaQuery()
                .select(ProductSide.class, info ->
                        !info.getColumn().equals("image_data")
                                && !info.getColumn().equals("rendered_image_data")
                                && !info.getColumn().equals("thumbnail_data"))
                .eq(ProductSide::getProductMissionId, missionId)
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

    public void saveBolt(ProductBolt entity, Long productMissionId) {
        validateBoltSerialNumUnique(entity, productMissionId);
        saveOrUpdate(entity);
    }

    private void validateBoltSerialNumUnique(ProductBolt entity, Long productMissionId) {
        Set<Long> sideIds = new java.util.HashSet<>(sideService.listSideIdsByMissionId(productMissionId));
        if (entity.getProductSideId() != null) sideIds.add(entity.getProductSideId());
        if (sideIds.isEmpty()) return;
        long count = lambdaQuery()
                .in(ProductBolt::getProductSideId, sideIds)
                .eq(ProductBolt::getBoltSerialNum, entity.getBoltSerialNum())
                .eq(ProductBolt::getDeleted, 0)
                .ne(entity.getId() != null, ProductBolt::getId, entity.getId())
                .count();
        if (count > 0) throw new IllegalArgumentException(
                "bolt_serial_num " + entity.getBoltSerialNum() + " 在当前 mission 中已存在");
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
git commit -m "feat: add mission config services"
```

### Task 19: MissionConfigValidator + 测试

**Files:**
- Create: `src/main/java/com/tightening/service/MissionConfigValidator.java`
- Create: `src/test/java/com/tightening/service/MissionConfigValidatorTest.java`

- [ ] **Step 1: 创建校验器**

```java
package com.tightening.service;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.PrerequisiteType;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.entity.ProductMission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class MissionConfigValidator {

    private final MissionPrerequisiteService prerequisiteService;
    private final BarCodeMatchingRuleService barcodeRuleService;

    public MissionConfigValidator(MissionPrerequisiteService prerequisiteService,
                                  BarCodeMatchingRuleService barcodeRuleService) {
        this.prerequisiteService = prerequisiteService;
        this.barcodeRuleService = barcodeRuleService;
    }

    /** 校验循环依赖——BFS 广度遍历所有可达路径（不限定单链），在保存前置关系前调用 */
    public void validateNoCircularDependency(Long missionId, Long prerequisiteMissionId) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(prerequisiteMissionId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!visited.add(current)) continue;
            if (current.equals(missionId)) {
                throw new IllegalArgumentException("检测到循环依赖: mission " + missionId + " 不能依赖自身");
            }
            prerequisiteService.lambdaQuery()
                    .select(MissionPrerequisite::getPrerequisiteMissionId)
                    .eq(MissionPrerequisite::getMissionId, current)
                    .eq(MissionPrerequisite::getDeleted, 0)
                    .list().stream()
                    .map(MissionPrerequisite::getPrerequisiteMissionId)
                    .forEach(queue::add);
        }
    }

    /** 校验前置任务类型约束（target 由调用方查询传入，避免循环依赖） */
    public void validatePrerequisiteType(ProductMission target, Integer prerequisiteType) {
        if (target == null) return;
        boolean isInspection = Integer.valueOf(1).equals(target.getIsInspection());
        if (Integer.valueOf(PrerequisiteType.INSPECTION_CHAIN.getCode()).equals(prerequisiteType) && !isInspection) {
            throw new IllegalArgumentException("INSPECTION_CHAIN 的前置任务必须是点检任务 (is_inspection=1)");
        }
        if (!Integer.valueOf(PrerequisiteType.INSPECTION_CHAIN.getCode()).equals(prerequisiteType) && isInspection) {
            throw new IllegalArgumentException("SAME_TRACE/PARTS_TRACE 的前置任务必须是普通任务 (is_inspection=0)");
        }
    }

    /** 校验点检绑定目标必须是普通任务（boundMission 由调用方查询传入，避免循环依赖） */
    public void validateInspectionBinding(ProductMission boundMission) {
        if (boundMission != null && Integer.valueOf(1).equals(boundMission.getIsInspection())) {
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

    /** 校验追溯码唯一——每个 mission 最多一条 PRODUCT_TRACE */
    public void validateProductTraceUnique(Long productMissionId, Long ruleId) {
        long count = barcodeRuleService.lambdaQuery()
                .eq(BarCodeMatchingRule::getProductMissionId, productMissionId)
                .eq(BarCodeMatchingRule::getRuleType, BarCodeRuleType.PRODUCT_TRACE.getCode())
                .eq(BarCodeMatchingRule::getDeleted, 0)
                .ne(ruleId != null, BarCodeMatchingRule::getId, ruleId)
                .count();
        if (count > 0) {
            throw new IllegalArgumentException("该 mission 已存在 PRODUCT_TRACE 规则，每个 mission 最多一条");
        }
    }
}
```

注意：`MissionConfigValidator` 仅使用 `MissionPrerequisiteService` 和 `BarCodeMatchingRuleService`，避免与服务层产生循环依赖。需要 `ProductMission` 数据的校验方法（如 `validatePrerequisiteType`、`validateInspectionBinding`）由调用方查询后传入实体；`validateBoltSerialNumUnique` 逻辑已内联到 `ProductBoltService` 中。

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
class MissionConfigValidatorTest {

    @Mock private MissionPrerequisiteService prerequisiteService;
    @Mock private BarCodeMatchingRuleService barcodeRuleService;

    private MissionConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MissionConfigValidator(prerequisiteService, barcodeRuleService);
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
    void validatePrerequisiteType_inspectionChainToNormalMission_shouldThrow() {
        ProductMission normalMission = new ProductMission();
        normalMission.setIsInspection(0);

        assertThatThrownBy(() -> validator.validatePrerequisiteType(normalMission, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INSPECTION_CHAIN");
    }

    @Test
    void validatePrerequisiteType_inspectionChainToInspectionMission_shouldPass() {
        ProductMission inspectionMission = new ProductMission();
        inspectionMission.setIsInspection(1);

        assertThatCode(() -> validator.validatePrerequisiteType(inspectionMission, 3))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePrerequisiteType_sameTraceToInspectionMission_shouldThrow() {
        ProductMission inspectionMission = new ProductMission();
        inspectionMission.setIsInspection(1);

        assertThatThrownBy(() -> validator.validatePrerequisiteType(inspectionMission, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SAME_TRACE");
    }

    @Test
    void validatePrerequisiteType_sameTraceToNormalMission_shouldPass() {
        ProductMission normalMission = new ProductMission();
        normalMission.setIsInspection(0);

        assertThatCode(() -> validator.validatePrerequisiteType(normalMission, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePrerequisiteType_targetNotFound_shouldNotThrow() {
        assertThatCode(() -> validator.validatePrerequisiteType(null, 1))
                .doesNotThrowAnyException();
    }

    // ---- 点检绑定约束（boundMission 由调用方传入，不再查询 DB） ----

    @Test
    void validateInspectionBinding_toInspectionMission_shouldThrow() {
        ProductMission inspection = new ProductMission();
        inspection.setIsInspection(1);

        assertThatThrownBy(() -> validator.validateInspectionBinding(inspection))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("点检任务不能绑定到另一个点检任务");
    }

    @Test
    void validateInspectionBinding_toNormalMission_shouldPass() {
        ProductMission normal = new ProductMission();
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
        // 自引用：prerequisiteMissionId == missionId 在 lambdaQuery 调用前就会触发
        assertThatThrownBy(() -> validator.validateNoCircularDependency(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能依赖自身");
    }

    @SuppressWarnings("unchecked")
    @Test
    void validateNoCircularDependency_noChain_shouldNotThrow() {
        LambdaQueryChainWrapper<MissionPrerequisite> chain = mock(LambdaQueryChainWrapper.class);
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
mvn test -pl . -Dtest=MissionConfigValidatorTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/service/MissionConfigValidator.java src/test/java/com/tightening/service/MissionConfigValidatorTest.java
git commit -m "feat: add MissionConfigValidator with keyChar and prerequisite validation"
```

---

## Phase 7: Controller

所有 Controller 使用 `@RequestMapping("api/...")`，遵循 RESTful 风格的 HTTP 方法（GET 查询、POST 创建、PUT 更新、DELETE 删除），返回同步 `ResponseEntity`（仅本地 CRUD，无异步 I/O）。测试使用纯 Mockito（`@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`），与标准 Mockito 测试模式一致。

### Task 20: ProductMissionController + 测试

**Files:**
- Create: `src/main/java/com/tightening/controller/ProductMissionController.java`
- Create: `src/test/java/com/tightening/controller/ProductMissionControllerTest.java`

- [ ] **Step 1: 创建 Controller**

```java
package com.tightening.controller;

import com.tightening.dto.BarCodeMatchingRuleDTO;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.entity.ProductMission;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.InspectionMissionBindingService;
import com.tightening.service.MissionPrerequisiteService;
import com.tightening.service.ProductMissionService;
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
@RequestMapping("api/missions")
@RequiredArgsConstructor
public class ProductMissionController {

    private final ProductMissionService missionService;
    private final MissionPrerequisiteService prerequisiteService;
    private final InspectionMissionBindingService bindingService;
    private final BarCodeMatchingRuleService barcodeRuleService;

    record PrerequisiteRequest(Long prerequisiteMissionId, Integer prerequisiteType) {}
    record InspectionBindingRequest(Long boundMissionId) {}

    @GetMapping
    public ResponseEntity<List<ProductMission>> list(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(missionService.lambdaQuery()
                .eq(ProductMission::getDeleted, 0)
                .orderByDesc(ProductMission::getId)
                .last("LIMIT " + size + " OFFSET " + ((page - 1) * size))
                .list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductMission> get(@PathVariable Long id) {
        return ResponseEntity.ok(missionService.getById(id));
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody ProductMissionDTO dto) {
        ProductMission entity = new ProductMission();
        entity.setName(dto.getName());
        entity.setMaxNgCount(dto.getMaxNgCount());
        entity.setPasswordRequiredAfterNg(dto.getPasswordRequiredAfterNg());
        entity.setEnabled(dto.getEnabled());
        entity.setMultiDeviceIndependent(dto.getMultiDeviceIndependent());
        entity.setSkipScrew(dto.getSkipScrew());
        entity.setIsInspection(dto.getIsInspection());
        entity.setInspectionScope(dto.getInspectionScope());
        missionService.saveOrUpdate(entity);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody ProductMissionDTO dto) {
        ProductMission entity = new ProductMission();
        entity.setId(id);
        entity.setName(dto.getName());
        entity.setMaxNgCount(dto.getMaxNgCount());
        entity.setPasswordRequiredAfterNg(dto.getPasswordRequiredAfterNg());
        entity.setEnabled(dto.getEnabled());
        entity.setMultiDeviceIndependent(dto.getMultiDeviceIndependent());
        entity.setSkipScrew(dto.getSkipScrew());
        entity.setIsInspection(dto.getIsInspection());
        entity.setInspectionScope(dto.getInspectionScope());
        missionService.saveOrUpdate(entity);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        missionService.cascadeDelete(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{missionId}/prerequisites")
    public ResponseEntity<String> addPrerequisite(@PathVariable Long missionId,
                                                   @RequestBody PrerequisiteRequest request) {
        missionService.addPrerequisite(missionId, request.prerequisiteMissionId(), request.prerequisiteType());
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/{missionId}/prerequisites")
    public ResponseEntity<List<MissionPrerequisite>> listPrerequisites(@PathVariable Long missionId) {
        return ResponseEntity.ok(prerequisiteService.lambdaQuery()
                .eq(MissionPrerequisite::getMissionId, missionId)
                .eq(MissionPrerequisite::getDeleted, 0)
                .list());
    }

    @DeleteMapping("/{missionId}/prerequisites/{id}")
    public ResponseEntity<String> deletePrerequisite(@PathVariable Long missionId, @PathVariable Long id) {
        prerequisiteService.removeById(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{inspectionMissionId}/inspection-bindings")
    public ResponseEntity<String> addInspectionBinding(@PathVariable Long inspectionMissionId,
                                                        @RequestBody InspectionBindingRequest request) {
        missionService.addInspectionBinding(inspectionMissionId, request.boundMissionId());
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/{missionId}/inspection-bindings")
    public ResponseEntity<List<InspectionMissionBinding>> listInspectionBindings(@PathVariable Long missionId) {
        return ResponseEntity.ok(bindingService.lambdaQuery()
                .eq(InspectionMissionBinding::getInspectionMissionId, missionId)
                .eq(InspectionMissionBinding::getDeleted, 0)
                .list());
    }

    @DeleteMapping("/{missionId}/inspection-bindings/{id}")
    public ResponseEntity<String> deleteInspectionBinding(@PathVariable Long missionId, @PathVariable Long id) {
        bindingService.removeById(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/{missionId}/barcode-rules")
    public ResponseEntity<String> addBarcodeRule(@PathVariable Long missionId,
                                                  @RequestBody BarCodeMatchingRuleDTO dto) {
        BarCodeMatchingRule entity = new BarCodeMatchingRule();
        if (dto.getId() != null) entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setProductMissionId(dto.getProductMissionId());
        entity.setRuleType(dto.getRuleType());
        entity.setPartNumber(dto.getPartNumber());
        entity.setExpectedLength(dto.getExpectedLength());
        entity.setKeyStartPosition(dto.getKeyStartPosition());
        entity.setKeyEndPosition(dto.getKeyEndPosition());
        entity.setKeyChar(dto.getKeyChar());
        missionService.addBarcodeRule(entity);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @GetMapping("/{missionId}/barcode-rules")
    public ResponseEntity<List<BarCodeMatchingRule>> listBarcodeRules(@PathVariable Long missionId) {
        return ResponseEntity.ok(barcodeRuleService.lambdaQuery()
                .eq(BarCodeMatchingRule::getProductMissionId, missionId)
                .eq(BarCodeMatchingRule::getDeleted, 0)
                .list());
    }

    @DeleteMapping("/{missionId}/barcode-rules/{id}")
    public ResponseEntity<String> deleteBarcodeRule(@PathVariable Long missionId, @PathVariable Long id) {
        barcodeRuleService.removeById(id);
        return ResponseEntity.ok("ok");
    }
}
```

- [ ] **Step 2: 创建测试**

```java
package com.tightening.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.tightening.dto.ProductMissionDTO;
import com.tightening.entity.ProductMission;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.InspectionMissionBindingService;
import com.tightening.service.MissionPrerequisiteService;
import com.tightening.service.ProductMissionService;
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
class ProductMissionControllerTest {

    @Mock private ProductMissionService missionService;
    @Mock private MissionPrerequisiteService prerequisiteService;
    @Mock private InspectionMissionBindingService bindingService;
    @Mock private BarCodeMatchingRuleService barcodeRuleService;
    @InjectMocks private ProductMissionController controller;

    @SuppressWarnings("unchecked")
    @Test
    void list_shouldReturnOk() {
        LambdaQueryChainWrapper<ProductMission> mockChain = mock(LambdaQueryChainWrapper.class);
        when(missionService.lambdaQuery()).thenReturn(mockChain);
        when(mockChain.eq(any(), any())).thenReturn(mockChain);
        when(mockChain.orderByDesc(any())).thenReturn(mockChain);
        when(mockChain.last(any())).thenReturn(mockChain);
        when(mockChain.list()).thenReturn(List.of());

        ResponseEntity<?> result = controller.list(1, 100);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void create_shouldReturnOk() {
        ProductMissionDTO dto = new ProductMissionDTO();
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
mvn test -pl . -Dtest=ProductMissionControllerTest -DfailIfNoTests=false -q
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/controller/ProductMissionController.java src/test/java/com/tightening/controller/ProductMissionControllerTest.java
git commit -m "feat: add ProductMissionController"
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
    public ResponseEntity<List<ProductSide>> list(@RequestParam Long missionId) {
        return ResponseEntity.ok(sideService.listByMissionId(missionId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductSide> get(@PathVariable Long id) {
        return ResponseEntity.ok(sideService.getById(id));
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody ProductSideDTO dto) {
        ProductSide entity = new ProductSide();
        entity.setProductMissionId(dto.getProductMissionId());
        entity.setName(dto.getName());
        sideService.saveOrUpdate(entity);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody ProductSideDTO dto) {
        ProductSide entity = new ProductSide();
        entity.setId(id);
        entity.setProductMissionId(dto.getProductMissionId());
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

注意：`ProductSideService.listByMissionId`（排除 BLOB 列）和 `deleteBoltsBySideIds` 在 Task 18 中已添加。

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
        when(sideService.listByMissionId(1L)).thenReturn(List.of());
        ResponseEntity<List<ProductSide>> result = controller.list(1L);
        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void create_shouldReturnOk() {
        ProductSideDTO dto = new ProductSideDTO();
        dto.setName("Side A");
        dto.setProductMissionId(1L);

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
                                          @RequestParam Long missionId) {
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
        boltService.saveBolt(entity, missionId);
        return ResponseEntity.ok(String.valueOf(entity.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody ProductBoltDTO dto,
                                          @RequestParam Long missionId) {
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
        boltService.saveBolt(entity, missionId);
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
