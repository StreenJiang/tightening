# Atlas MID 0061 拧紧数据解析器 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 `AtlasTighteningDataParser` 静态解析器，覆盖 MID 0061 revision 1-7/998/999，解析结果填充 `TighteningData` 实体统一入库。

**Architecture:** 硬编码偏移量解析器（`fillRev2Structured` + `fillRev6Base` 助手方法消除重复）+ 改造 `TighteningData`（字段重命名 + 新增 Atlas 专属列 + `revision` + `extraData` JSON）+ 常量类。FIT 协议继续使用同一实体。

**Tech Stack:** Java 21, JUnit 5, AssertJ, Jackson, Lombok

---

### 数据偏移量约定

解析器入参 `byte[] data` = `AtlasFrame.data`（全帧 byte 21+ 部分）。代码中偏移量使用协议文档 1-based byte 编号，内部通过 `protocolByte - 21` 转为 data 数组索引。

**rev 1 和 rev 2+ 偏移量不同**。例如 Torque rev 1 在 byte 141-146，rev 2 在 byte 184-189。因此：`parseRev1` 独立实现，`fillRev2Structured` 助手供 rev 2-7/998 共享。

---

### Task 1: DB 迁移 — 重命名列 + 新增 Atlas 列

**Files:**
- Create: `src/main/resources/db/migration/V1.0.6__rename_and_add_atlas_columns.sql`

- [ ] **Step 1: 创建迁移 SQL**

```sql
ALTER TABLE tightening_data RENAME COLUMN tightening_result TO tightening_status;
ALTER TABLE tightening_data RENAME COLUMN torque_result TO torque_status;
ALTER TABLE tightening_data RENAME COLUMN angle_result TO angle_status;
ALTER TABLE tightening_data RENAME COLUMN rundown_angle_result TO rundown_angle_status;

ALTER TABLE tightening_data ADD COLUMN cell_id INTEGER;
ALTER TABLE tightening_data ADD COLUMN channel_id INTEGER;
ALTER TABLE tightening_data ADD COLUMN controller_name TEXT;
ALTER TABLE tightening_data ADD COLUMN vin TEXT;
ALTER TABLE tightening_data ADD COLUMN job_id INTEGER;
ALTER TABLE tightening_data ADD COLUMN batch_size INTEGER;
ALTER TABLE tightening_data ADD COLUMN batch_counter INTEGER;
ALTER TABLE tightening_data ADD COLUMN batch_status INTEGER;
ALTER TABLE tightening_data ADD COLUMN revision INTEGER;
ALTER TABLE tightening_data ADD COLUMN extra_data TEXT;
```

- [ ] **Step 2: 验证迁移语法**

Run: `sqlite3 :memory: "CREATE TABLE t(id INTEGER PRIMARY KEY, tightening_result INTEGER); ALTER TABLE t RENAME COLUMN tightening_result TO tightening_status; ALTER TABLE t ADD COLUMN revision INTEGER; SELECT sql FROM sqlite_master WHERE name='t';"`
Expected: 输出包含 `tightening_status INTEGER` 和 `revision INTEGER` 列

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/db/migration/V1.0.6__rename_and_add_atlas_columns.sql
git commit -m "feat: rename result columns to status, add Atlas columns and revision"
```

---

### Task 2: 创建常量类

**Files:**
- Create: `src/main/java/com/tightening/constant/ExtraDataKeys.java`
- Create: `src/main/java/com/tightening/constant/atlas/AtlasExtraDataKeys.java`

- [ ] **Step 1: 创建 ExtraDataKeys（跨工具公共 key）**

```java
package com.tightening.constant;

public final class ExtraDataKeys {
    private ExtraDataKeys() {}

    // FIT
    public static final String BARCODE = "barcode";
}
```

- [ ] **Step 2: 创建 AtlasExtraDataKeys**

```java
package com.tightening.constant.atlas;

public final class AtlasExtraDataKeys {
    private AtlasExtraDataKeys() {}

    // === Rev 2+ ===
    public static final String STRATEGY = "strategy";
    public static final String STRATEGY_OPTIONS = "strategyOptions";
    public static final String CURRENT_MONITORING_STATUS = "currentMonitoringStatus";
    public static final String SELF_TAP_STATUS = "selfTapStatus";
    public static final String PV_MONITOR_STATUS = "pvMonitorStatus";
    public static final String PV_COMPENSATE_STATUS = "pvCompensateStatus";
    public static final String TIGHTENING_ERROR_STATUS = "tighteningErrorStatus";
    public static final String CURRENT_MONITORING_MIN = "currentMonitoringMin";
    public static final String CURRENT_MONITORING_MAX = "currentMonitoringMax";
    public static final String CURRENT_MONITORING_VALUE = "currentMonitoringValue";
    public static final String SELF_TAP_MIN = "selfTapMin";
    public static final String SELF_TAP_MAX = "selfTapMax";
    public static final String SELF_TAP_TORQUE = "selfTapTorque";
    public static final String PV_MONITOR_MIN = "pvMonitorMin";
    public static final String PV_MONITOR_MAX = "pvMonitorMax";
    public static final String PREVAIL_TORQUE = "prevailTorque";
    public static final String JOB_SEQUENCE_NUMBER = "jobSequenceNumber";
    public static final String SYNC_TIGHTENING_ID = "syncTighteningId";
    public static final String TOOL_SERIAL_NUMBER = "toolSerialNumber";

    // === Rev 4+ ===
    public static final String IDENTIFIER_RESULT_PART_2 = "identifierResultPart2";
    public static final String IDENTIFIER_RESULT_PART_3 = "identifierResultPart3";
    public static final String IDENTIFIER_RESULT_PART_4 = "identifierResultPart4";

    // === Rev 5+ ===
    public static final String CUSTOMER_TIGHTENING_ERROR_CODE = "customerTighteningErrorCode";

    // === Rev 6+ ===
    public static final String PV_COMPENSATE_VALUE = "pvCompensateValue";
    public static final String TIGHTENING_ERROR_STATUS_2 = "tighteningErrorStatus2";

    // === Rev 7+ ===
    public static final String COMPENSATED_ANGLE = "compensatedAngle";
    public static final String FINAL_ANGLE_DECIMAL = "finalAngleDecimal";

    // === Rev 998 ===
    public static final String TOTAL_STAGES = "totalStages";
    public static final String COMPLETED_STAGES = "completedStages";
    public static final String STAGE_RESULTS = "stageResults";
}
```

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/tightening/constant/ExtraDataKeys.java \
        src/main/java/com/tightening/constant/atlas/AtlasExtraDataKeys.java
git commit -m "feat: add ExtraDataKeys and AtlasExtraDataKeys constants"
```

---

### Task 3: 枚举 — 按工具定义独立枚举

**Files:**
- Rename: `src/main/java/com/tightening/constant/TighteningResult.java` → `TighteningStatus.java`
- Rename: `src/main/java/com/tightening/constant/TorqueResult.java` → `AtlasTorqueStatus.java`
- Rename: `src/main/java/com/tightening/constant/AngleResult.java` → `AtlasAngleStatus.java`
- Create: `src/main/java/com/tightening/constant/FitTorqueStatus.java`
- Create: `src/main/java/com/tightening/constant/FitAngleStatus.java`

设计原则：`torqueStatus`/`angleStatus` 列在 Atlas（三值 Low/OK/High）和 FIT（二值 OK/NG）中语义不同，为每个工具定义独立枚举，避免 code 冲突和展示歧义。

| 枚举 | 值 | 使用方 |
|---|---|---|
| `TighteningStatus` | NG(0), OK(1) | Atlas + FIT 共享 |
| `AtlasTorqueStatus` | LOW(0), OK(1), HIGH(2) | AtlasTighteningDataParser |
| `AtlasAngleStatus` | LOW(0), OK(1), HIGH(2) | AtlasTighteningDataParser |
| `FitTorqueStatus` | OK(1), NG(2) | FitDataUtils |
| `FitAngleStatus` | OK(1), NG(2) | FitDataUtils |

- [ ] **Step 1: TighteningResult → TighteningStatus**

```java
package com.tightening.constant;

import java.util.Arrays;
import java.util.Optional;

public enum TighteningStatus {
    NG(0),
    OK(1);

    private final int code;
    TighteningStatus(int code) { this.code = code; }
    public int getCode() { return code; }
    public static Optional<TighteningStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 2: TorqueResult → AtlasTorqueStatus**

```java
package com.tightening.constant;

import java.util.Arrays;
import java.util.Optional;

public enum AtlasTorqueStatus {
    LOW(0),
    OK(1),
    HIGH(2);

    private final int code;
    AtlasTorqueStatus(int code) { this.code = code; }
    public int getCode() { return code; }
    public static Optional<AtlasTorqueStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 3: AngleResult → AtlasAngleStatus**

```java
package com.tightening.constant;

import java.util.Arrays;
import java.util.Optional;

public enum AtlasAngleStatus {
    LOW(0),
    OK(1),
    HIGH(2);

    private final int code;
    AtlasAngleStatus(int code) { this.code = code; }
    public int getCode() { return code; }
    public static Optional<AtlasAngleStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 4: 新建 FitTorqueStatus**

```java
package com.tightening.constant;

import java.util.Arrays;
import java.util.Optional;

public enum FitTorqueStatus {
    OK(1),
    NG(2);

    private final int code;
    FitTorqueStatus(int code) { this.code = code; }
    public int getCode() { return code; }
    public static Optional<FitTorqueStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 5: 新建 FitAngleStatus**（同 FitTorqueStatus 模式）

- [ ] **Step 6: 删除旧枚举**

```bash
git rm src/main/java/com/tightening/constant/TighteningResult.java \
       src/main/java/com/tightening/constant/TorqueResult.java \
       src/main/java/com/tightening/constant/AngleResult.java
```

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/tightening/constant/
git commit -m "refactor: separate status enums by tool — Atlas vs FIT"
```

- [ ] **Step 4: 删除旧枚举文件**

```bash
git rm src/main/java/com/tightening/constant/TighteningResult.java \
       src/main/java/com/tightening/constant/TorqueResult.java \
       src/main/java/com/tightening/constant/AngleResult.java
```

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/constant/TighteningStatus.java \
        src/main/java/com/tightening/constant/TorqueStatus.java \
        src/main/java/com/tightening/constant/AngleStatus.java
git commit -m "refactor: rename Result enums to Status, add LOW/HIGH values"
```

---

### Task 4: 改造 TighteningData 实体

**Files:**
- Modify: `src/main/java/com/tightening/entity/TighteningData.java`

- [ ] **Step 1: 重命名 + 新增字段**

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
@TableName("tightening_data")
public class TighteningData extends BaseEntity {
    // 业务字段
    private long missionRecordId;
    private String workstationName;
    private String toolName;
    private String toolTypeName;
    private String productSideName;
    private int boltSerialNum;
    private String armLocation;
    private int parameterSet;
    private String parameterSetName;

    // 拧紧核心字段
    private long tighteningId;
    private int tighteningStatus;
    private int resultType;
    private int torqueStatus;
    private int angleStatus;
    private int rundownAngleStatus;
    private int torqueValuesUnit;

    private double torqueMinLimit;
    private double torqueMaxLimit;
    private double torqueFinalTarget;
    private double torque;
    private double angleMinLimit;
    private double angleMaxLimit;
    private double angleFinalTarget;
    private double angle;
    private double rundownAngleMinLimit;
    private double rundownAngleMaxLimit;
    private double rundownAngle;

    private String timestamp;

    // Atlas 专属
    private int cellId;
    private int channelId;
    private String controllerName;
    private String vin;
    private int jobId;
    private int batchSize;
    private int batchCounter;
    private int batchStatus;

    // 元数据
    private int revision;
    private String extraData;
}
```

变更：4 个字段重命名 + 新增 11 个字段（cellId/channelId/controllerName/vin/jobId/batchSize/batchCounter/batchStatus/revision/extraData）+ tighteningId int→long

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl . -q`
Expected: (预计编译错误 — DTO 和 FitDataUtils 还有旧字段名，后续任务修复)

- [ ] **Step 3: 暂不提交，等 Task 4 完成后一起提交**

---

### Task 5: 同步 TighteningDataDTO 和 FitDataUtils

**Files:**
- Modify: `src/main/java/com/tightening/dto/TighteningDataDTO.java`
- Modify: `src/main/java/com/tightening/netty/protocol/util/FitDataUtils.java`

- [ ] **Step 1: 同步 DTO 字段**

`TighteningDataDTO.java` 与实体做相同改动：`*Result` → `*Status`，新增 `cellId` / `channelId` / `controllerName` / `vin` / `jobId` / `batchSize` / `batchCounter` / `batchStatus` / `revision` / `extraData`，`tighteningId` int→long

- [ ] **Step 2: 修改 FitDataUtils 导入和调用**

更新 import：
```java
import com.tightening.constant.FitAngleStatus;
import com.tightening.constant.FitTorqueStatus;
import com.tightening.constant.TighteningStatus;
```

更新 setter/getter 调用：

```java
// 旧代码：
tighteningData.setTighteningResult(TighteningResult.OK.getCode());
tighteningData.setTorqueResult(TorqueResult.OK.getCode());
tighteningData.setAngleResult(AngleResult.OK.getCode());
tighteningData.setTighteningResult(TighteningResult.NG.getCode());
tighteningData.setTorqueResult(TorqueResult.NG.getCode());
tighteningData.setAngleResult(AngleResult.NG.getCode());
log.debug("tightening_status=" + tighteningData.getTighteningResult());

// 新代码：
tighteningData.setTighteningStatus(TighteningStatus.OK.getCode());
tighteningData.setTorqueStatus(FitTorqueStatus.OK.getCode());
tighteningData.setAngleStatus(FitAngleStatus.OK.getCode());
tighteningData.setTighteningStatus(TighteningStatus.NG.getCode());
tighteningData.setTorqueStatus(FitTorqueStatus.NG.getCode());
tighteningData.setAngleStatus(FitAngleStatus.NG.getCode());
log.debug("tightening_status=" + tighteningData.getTighteningStatus());
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/entity/TighteningData.java \
        src/main/java/com/tightening/dto/TighteningDataDTO.java \
        src/main/java/com/tightening/netty/protocol/util/FitDataUtils.java
git commit -m "feat: rename Result→Status, add Atlas columns, revision, extraData"
```

---

### Task 6: 创建 AtlasTighteningDataParser（TDD — rev 1 解析）

**Files:**
- Create: `src/test/java/com/tightening/netty/protocol/util/AtlasTighteningDataParserTest.java`
- Create: `src/main/java/com/tightening/netty/protocol/util/AtlasTighteningDataParser.java`

- [ ] **Step 1: 编写 rev 1 测试**

```java
package com.tightening.netty.protocol.util;

import com.tightening.entity.TighteningData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtlasTighteningDataParserTest {

    @Test
    void parseRev1_shouldExtractAllFields() {
        byte[] data = buildRev1Data(
                "0001", "00",
                "CTRL-1                   ",
                "VIN123456789012345678901  ",
                "12", "345", "0010", "0005",
                "1", "1", "1",
                "001234", "005678", "003500", "002750",
                "00180", "00720", "00360", "00270",
                "2026-06-01:12:00:00",
                "2026-05-01:08:00:00",
                "1", "0000001234"
        );

        TighteningData result = AtlasTighteningDataParser.parse(data, 1);

        assertThat(result.getRevision()).isEqualTo(1);
        assertThat(result.getCellId()).isEqualTo(1);
        assertThat(result.getChannelId()).isEqualTo(0);
        assertThat(result.getControllerName()).isEqualTo("CTRL-1");
        assertThat(result.getVin()).isEqualTo("VIN123456789012345678901");
        assertThat(result.getJobId()).isEqualTo(12);
        assertThat(result.getParameterSet()).isEqualTo(345);
        assertThat(result.getBatchSize()).isEqualTo(10);
        assertThat(result.getBatchCounter()).isEqualTo(5);
        assertThat(result.getTighteningStatus()).isEqualTo(1);
        assertThat(result.getTorqueStatus()).isEqualTo(1);
        assertThat(result.getAngleStatus()).isEqualTo(1);
        assertThat(result.getTorqueMinLimit()).isEqualTo(12.34);
        assertThat(result.getTorqueMaxLimit()).isEqualTo(56.78);
        assertThat(result.getTorqueFinalTarget()).isEqualTo(35.00);
        assertThat(result.getTorque()).isEqualTo(27.50);
        assertThat(result.getAngleMinLimit()).isEqualTo(180);
        assertThat(result.getAngleMaxLimit()).isEqualTo(720);
        assertThat(result.getAngleFinalTarget()).isEqualTo(360);
        assertThat(result.getAngle()).isEqualTo(270);
        assertThat(result.getTimestamp()).isEqualTo("2026-06-01:12:00:00");
        assertThat(result.getBatchStatus()).isEqualTo(1);
        assertThat(result.getTighteningId()).isEqualTo(1234);
    }

    @Test
    void parse_unsupportedRevision_shouldThrow() {
        byte[] data = new byte[300];
        assertThatThrownBy(() -> AtlasTighteningDataParser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported revision");
    }

    @Test
    void parse_dataTooShort_shouldNotThrow() {
        byte[] data = new byte[10];
        TighteningData result = AtlasTighteningDataParser.parse(data, 1);
        assertThat(result).isNotNull();
    }

    private static byte[] buildRev1Data(
            String cellId, String channelId, String controllerName, String vin,
            String jobId, String parameterSetId, String batchSize, String batchCounter,
            String tighteningStatus, String torqueStatus, String angleStatus,
            String torqueMin, String torqueMax, String torqueTarget, String torque,
            String angleMin, String angleMax, String angleTarget, String angle,
            String timestamp, String paramChangeTs, String batchStatus, String tighteningId) {
        byte[] data = new byte[215];
        java.util.Arrays.fill(data, (byte) ' ');
        writeAt(data, 23, cellId);       writeAt(data, 29, channelId);
        writeAt(data, 33, controllerName); writeAt(data, 60, vin);
        writeAt(data, 87, jobId);        writeAt(data, 91, parameterSetId);
        writeAt(data, 96, batchSize);    writeAt(data, 102, batchCounter);
        writeAt(data, 108, tighteningStatus); writeAt(data, 111, torqueStatus);
        writeAt(data, 114, angleStatus);
        writeAt(data, 117, torqueMin);   writeAt(data, 125, torqueMax);
        writeAt(data, 133, torqueTarget); writeAt(data, 141, torque);
        writeAt(data, 149, angleMin);    writeAt(data, 156, angleMax);
        writeAt(data, 163, angleTarget); writeAt(data, 170, angle);
        writeAt(data, 177, timestamp);   writeAt(data, 198, paramChangeTs);
        writeAt(data, 219, batchStatus); writeAt(data, 222, tighteningId);
        return data;
    }

    private static void writeAt(byte[] data, int protocolByte, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, data, protocolByte - 21, bytes.length);
    }
}
```

- [ ] **Step 2: 运行测试，验证失败**

Run: `mvn test -pl . -Dtest=AtlasTighteningDataParserTest -DfailIfNoTests=false -q`
Expected: 编译错误（类不存在）或测试失败

- [ ] **Step 3: 创建解析器 + rev 1 实现**

```java
package com.tightening.netty.protocol.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tightening.constant.AtlasAngleStatus;
import com.tightening.constant.AtlasTorqueStatus;
import com.tightening.constant.atlas.AtlasExtraDataKeys;
import com.tightening.entity.TighteningData;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public final class AtlasTighteningDataParser {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AtlasTighteningDataParser() {}

    public static TighteningData parse(byte[] data, int revision) {
        TighteningData result = switch (revision) {
            case 1 -> parseRev1(data);
            case 2 -> parseRev2(data);
            case 3 -> parseRev3(data);
            case 4 -> parseRev4(data);
            case 5 -> parseRev5(data);
            case 6 -> parseRev6(data);
            case 7 -> parseRev7(data);
            case 998 -> parseRev998(data);
            case 999 -> parseRev999(data);
            default -> throw new IllegalArgumentException("Unsupported revision: " + revision);
        };
        result.setRevision(revision);
        return result;
    }

    // ==================== Rev 1 ====================

    private static TighteningData parseRev1(byte[] data) {
        var d = new TighteningData();
        d.setCellId(parseInt(data, 23, 4));
        d.setChannelId(parseInt(data, 29, 2));
        d.setControllerName(parseString(data, 33, 25));
        d.setVin(parseString(data, 60, 25));
        d.setJobId(parseInt(data, 87, 2));
        d.setParameterSet(parseInt(data, 91, 3));
        d.setBatchSize(parseInt(data, 96, 4));
        d.setBatchCounter(parseInt(data, 102, 4));
        d.setTighteningStatus(parseInt(data, 108, 1));
        d.setTorqueStatus(parseTorqueStatus(data, 111));
        d.setAngleStatus(parseAngleStatus(data, 114));
        d.setTorqueMinLimit(parseTorque(data, 117));
        d.setTorqueMaxLimit(parseTorque(data, 125));
        d.setTorqueFinalTarget(parseTorque(data, 133));
        d.setTorque(parseTorque(data, 141));
        d.setAngleMinLimit(parseInt(data, 149, 5));
        d.setAngleMaxLimit(parseInt(data, 156, 5));
        d.setAngleFinalTarget(parseInt(data, 163, 5));
        d.setAngle(parseInt(data, 170, 5));
        d.setTimestamp(parseString(data, 177, 19));
        d.setBatchStatus(parseInt(data, 219, 1));
        d.setTighteningId(parseLong(data, 222, 10));
        return d;
    }

    // ==================== Rev 2+ 共享助手 ====================

    /** 填充 rev 2+ 级别的结构化列，供 rev 2-7/998 复用 */
    private static void fillRev2Structured(TighteningData d, byte[] data) {
        d.setCellId(parseInt(data, 23, 4));
        d.setChannelId(parseInt(data, 29, 2));
        d.setControllerName(parseString(data, 33, 25));
        d.setVin(parseString(data, 60, 25));
        d.setJobId(parseInt(data, 87, 2));
        d.setParameterSet(parseInt(data, 91, 3));
        d.setBatchSize(parseInt(data, 109, 4));
        d.setBatchCounter(parseInt(data, 115, 4));
        d.setTighteningStatus(parseInt(data, 121, 1));
        d.setBatchStatus(parseInt(data, 124, 1));
        d.setTorqueStatus(parseTorqueStatus(data, 127));
        d.setAngleStatus(parseAngleStatus(data, 130));
        d.setRundownAngleStatus(parseInt(data, 133, 1));
        d.setTorqueMinLimit(parseTorque(data, 160));
        d.setTorqueMaxLimit(parseTorque(data, 168));
        d.setTorqueFinalTarget(parseTorque(data, 176));
        d.setTorque(parseTorque(data, 184));
        d.setAngleMinLimit(parseInt(data, 192, 5));
        d.setAngleMaxLimit(parseInt(data, 199, 5));
        d.setAngleFinalTarget(parseInt(data, 206, 5));
        d.setAngle(parseInt(data, 213, 5));
        d.setRundownAngleMinLimit(parseInt(data, 220, 5));
        d.setRundownAngleMaxLimit(parseInt(data, 227, 5));
        d.setRundownAngle(parseInt(data, 234, 5));
        d.setTimestamp(parseString(data, 346, 19));
        d.setTighteningId(parseLong(data, 304, 10));
    }

    /** 填充 rev 2 extraData（共 19 个字段） */
    private static Map<String, Object> buildRev2Extra(byte[] data) {
        Map<String, Object> extra = new HashMap<>();
        extra.put(AtlasExtraDataKeys.STRATEGY, parseInt(data, 96, 2));
        extra.put(AtlasExtraDataKeys.STRATEGY_OPTIONS, parseString(data, 102, 5));
        extra.put(AtlasExtraDataKeys.CURRENT_MONITORING_STATUS, parseInt(data, 136, 1));
        extra.put(AtlasExtraDataKeys.SELF_TAP_STATUS, parseInt(data, 139, 1));
        extra.put(AtlasExtraDataKeys.PV_MONITOR_STATUS, parseInt(data, 142, 1));
        extra.put(AtlasExtraDataKeys.PV_COMPENSATE_STATUS, parseInt(data, 145, 1));
        extra.put(AtlasExtraDataKeys.TIGHTENING_ERROR_STATUS, parseString(data, 148, 10));
        extra.put(AtlasExtraDataKeys.CURRENT_MONITORING_MIN, parseInt(data, 241, 3));
        extra.put(AtlasExtraDataKeys.CURRENT_MONITORING_MAX, parseInt(data, 246, 3));
        extra.put(AtlasExtraDataKeys.CURRENT_MONITORING_VALUE, parseInt(data, 251, 3));
        extra.put(AtlasExtraDataKeys.SELF_TAP_MIN, parseTorque(data, 256));
        extra.put(AtlasExtraDataKeys.SELF_TAP_MAX, parseTorque(data, 264));
        extra.put(AtlasExtraDataKeys.SELF_TAP_TORQUE, parseTorque(data, 272));
        extra.put(AtlasExtraDataKeys.PV_MONITOR_MIN, parseTorque(data, 280));
        extra.put(AtlasExtraDataKeys.PV_MONITOR_MAX, parseTorque(data, 288));
        extra.put(AtlasExtraDataKeys.PREVAIL_TORQUE, parseTorque(data, 296));
        extra.put(AtlasExtraDataKeys.JOB_SEQUENCE_NUMBER, parseInt(data, 316, 5));
        extra.put(AtlasExtraDataKeys.SYNC_TIGHTENING_ID, parseInt(data, 323, 5));
        extra.put(AtlasExtraDataKeys.TOOL_SERIAL_NUMBER, parseString(data, 330, 14));
        return extra;
    }

    // ==================== Rev 2-7 ====================

    private static TighteningData parseRev2(byte[] data) {
        var d = new TighteningData();
        fillRev2Structured(d, data);
        d.setExtraData(toJson(buildRev2Extra(data)));
        return d;
    }

    private static TighteningData parseRev3(byte[] data) {
        var d = new TighteningData();
        fillRev2Structured(d, data);
        Map<String, Object> extra = buildRev2Extra(data);
        d.setParameterSetName(parseString(data, 388, 25));
        d.setTorqueValuesUnit(parseInt(data, 415, 1));
        d.setResultType(parseInt(data, 418, 2));
        d.setExtraData(toJson(extra));
        return d;
    }

    private static TighteningData parseRev4(byte[] data) {
        var d = parseRev3(data);  // 复用 rev 3（fillRev2Structured + rev3 extra）
        Map<String, Object> extra = fromJson(d.getExtraData());
        extra.put(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_2, parseString(data, 422, 25));
        extra.put(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_3, parseString(data, 449, 25));
        extra.put(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_4, parseString(data, 476, 25));
        d.setExtraData(toJson(extra));
        return d;
    }

    private static TighteningData parseRev5(byte[] data) {
        var d = parseRev4(data);  // 复用 rev 4
        Map<String, Object> extra = fromJson(d.getExtraData());
        extra.put(AtlasExtraDataKeys.CUSTOMER_TIGHTENING_ERROR_CODE, parseString(data, 503, 4));
        d.setExtraData(toJson(extra));
        return d;
    }

    private static TighteningData parseRev6(byte[] data) {
        var d = parseRev5(data);  // 复用 rev 5
        Map<String, Object> extra = fromJson(d.getExtraData());
        extra.put(AtlasExtraDataKeys.PV_COMPENSATE_VALUE, parseTorque(data, 509));
        extra.put(AtlasExtraDataKeys.TIGHTENING_ERROR_STATUS_2, parseString(data, 517, 10));
        d.setExtraData(toJson(extra));
        return d;
    }

    private static TighteningData parseRev7(byte[] data) {
        var d = parseRev6(data);  // 复用 rev 6
        Map<String, Object> extra = fromJson(d.getExtraData());
        extra.put(AtlasExtraDataKeys.COMPENSATED_ANGLE, parseInt(data, 529, 7));
        extra.put(AtlasExtraDataKeys.FINAL_ANGLE_DECIMAL, parseInt(data, 538, 7));
        d.setExtraData(toJson(extra));
        return d;
    }

    // ==================== Rev 998 / 999 ====================

    private static TighteningData parseRev998(byte[] data) {
        var d = parseRev6(data);  // rev 6 base (byte 21-526), rev 998 overlays 527+
        Map<String, Object> extra = fromJson(d.getExtraData());
        int totalStages = parseInt(data, 529, 2);
        int completedStages = parseInt(data, 533, 2);
        extra.put(AtlasExtraDataKeys.TOTAL_STAGES, totalStages);
        extra.put(AtlasExtraDataKeys.COMPLETED_STAGES, completedStages);

        java.util.List<java.util.Map<String, Object>> stages = new java.util.ArrayList<>();
        int offset = 537;
        for (int i = 0; i < completedStages; i++) {
            java.util.Map<String, Object> stage = new HashMap<>();
            stage.put("torque", parseTorque(data, offset));
            stage.put("angle", parseInt(data, offset + 6, 5));
            stages.add(stage);
            offset += 11;
        }
        extra.put(AtlasExtraDataKeys.STAGE_RESULTS, stages);
        d.setExtraData(toJson(extra));
        return d;
    }

    private static TighteningData parseRev999(byte[] data) {
        var d = new TighteningData();
        d.setVin(parseString(data, 21, 25));
        d.setJobId(parseInt(data, 46, 2));
        d.setParameterSet(parseInt(data, 48, 3));
        d.setBatchSize(parseInt(data, 51, 4));
        d.setBatchCounter(parseInt(data, 55, 4));
        d.setBatchStatus(parseInt(data, 59, 1));
        d.setTighteningStatus(parseInt(data, 60, 1));
        d.setTorqueStatus(parseTorqueStatus(data, 61));
        d.setAngleStatus(parseAngleStatus(data, 62));
        d.setTorque(parseTorque(data, 63));
        d.setAngle(parseInt(data, 69, 5));
        d.setTimestamp(parseString(data, 74, 19));
        d.setTighteningId(parseLong(data, 112, 10));
        return d;
    }

    // ==================== 工具方法 ====================

    private static int parseInt(byte[] data, int protocolByte, int length) {
        int offset = protocolByte - 21;
        if (offset + length > data.length) {
            log.warn("Data too short at protocol byte {}: need {}, have {}",
                    protocolByte, length, data.length - offset);
            return 0;
        }
        String raw = new String(data, offset, length, StandardCharsets.US_ASCII).trim();
        if (raw.isEmpty()) return 0;
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException e) {
            log.warn("Failed to parse int at protocol byte {}: '{}'", protocolByte, raw);
            return 0;
        }
    }

    private static long parseLong(byte[] data, int protocolByte, int length) {
        int offset = protocolByte - 21;
        if (offset + length > data.length) {
            log.warn("Data too short at protocol byte {}: need {}, have {}",
                    protocolByte, length, data.length - offset);
            return 0;
        }
        String raw = new String(data, offset, length, StandardCharsets.US_ASCII).trim();
        if (raw.isEmpty()) return 0;
        try { return Long.parseLong(raw); }
        catch (NumberFormatException e) {
            log.warn("Failed to parse long at protocol byte {}: '{}'", protocolByte, raw);
            return 0;
        }
    }

    private static String parseString(byte[] data, int protocolByte, int length) {
        int offset = protocolByte - 21;
        if (offset + length > data.length) {
            log.warn("Data too short at protocol byte {}: need {}, have {}",
                    protocolByte, length, data.length - offset);
            return "";
        }
        return new String(data, offset, length, StandardCharsets.US_ASCII).trim();
    }

    private static double parseTorque(byte[] data, int protocolByte) {
        return parseInt(data, protocolByte, 6) / 100.0;
    }

    private static int parseTorqueStatus(byte[] data, int protocolByte) {
        return AtlasTorqueStatus.fromCode(parseInt(data, protocolByte, 1))
                .orElse(AtlasTorqueStatus.LOW).getCode();
    }

    private static int parseAngleStatus(byte[] data, int protocolByte) {
        return AtlasAngleStatus.fromCode(parseInt(data, protocolByte, 1))
                .orElse(AtlasAngleStatus.LOW).getCode();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try { return OBJECT_MAPPER.readValue(json, Map.class); }
        catch (JsonProcessingException e) { return new HashMap<>(); }
    }

    private static String toJson(Map<String, Object> map) {
        if (map.isEmpty()) return null;
        try { return OBJECT_MAPPER.writeValueAsString(map); }
        catch (JsonProcessingException e) { return null; }
    }
}
```

- [ ] **Step 4: 运行 rev 1 测试**

Run: `mvn test -pl . -Dtest=AtlasTighteningDataParserTest#parseRev1_shouldExtractAllFields -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/netty/protocol/util/AtlasTighteningDataParser.java \
        src/test/java/com/tightening/netty/protocol/util/AtlasTighteningDataParserTest.java
git commit -m "feat: add AtlasTighteningDataParser with rev 1-7/998/999 support"
```

---

### Task 7: 接入 Handler（Atlas + FIT）

**Files:**
- Modify: `src/main/java/com/tightening/device/DeviceHolder.java`
- Modify: `src/main/java/com/tightening/netty/protocol/handler/atlas/AtlasPFSeriesInBoundHandler.java`
- Modify: `src/main/java/com/tightening/netty/protocol/handler/fit/FitSeriesInBoundHandler.java`

- [ ] **Step 0: DeviceHolder 添加 resolveToolTypeName()**

```java
public String resolveToolTypeName() {
    DeviceType deviceType = DeviceType.getType(device.getType());
    return deviceType != null ? deviceType.getName() : null;
}
```

- [ ] **Step 1: Atlas Handler — 构造器改 ToolHandler + 调用解析器**

`AtlasPFSeriesInBoundHandler.java`：

```java
@Slf4j
public class AtlasPFSeriesInBoundHandler extends SimpleChannelInboundHandler<AtlasFrame> {
    private final ToolHandler deviceHandler;

    public AtlasPFSeriesInBoundHandler(ToolHandler deviceHandler) {
        this.deviceHandler = deviceHandler;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, AtlasFrame msg) throws Exception {
        // ... 现有 switch 逻辑不变 ...

        case TIGHTEN_DATA:
            TighteningData tighteningData = AtlasTighteningDataParser.parse(
                    msg.getData(), msg.getRevision());
            DeviceHolder holder = ctx.channel().attr(DEVICE_HOLDER).get();
            if (holder != null) {
                tighteningData.setToolTypeName(holder.resolveToolTypeName());
            }
            log.debug("Parsed tightening data: tighteningId={}, torque={}, revision={}",
                    tighteningData.getTighteningId(), tighteningData.getTorque(),
                    tighteningData.getRevision());
            deviceHandler.getTighteningDataService().save(tighteningData);
            break;

        // ...
    }
}
```

- [ ] **Step 2: FIT Handler — 构造器改 ToolHandler + 设 toolTypeName**

`FitSeriesInBoundHandler.java`：

```java
@Slf4j
public class FitSeriesInBoundHandler extends SimpleChannelInboundHandler<FitFrame> {
    private final ToolHandler deviceHandler;

    public FitSeriesInBoundHandler(ToolHandler deviceHandler) {
        this.deviceHandler = deviceHandler;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FitFrame msg) throws Exception {
        // ... 现有 switch 逻辑不变 ...

        case TIGHTEN_FINAL:
            TighteningDataDTO tighteningDataDTO = FitDataUtils.parseTighteningData(data);
            TighteningData tighteningData = Converter.dto2Entity(tighteningDataDTO, TighteningData::new);
            DeviceHolder holder = ctx.channel().attr(DEVICE_HOLDER).get();
            if (holder != null) {
                tighteningData.setToolTypeName(holder.resolveToolTypeName());
            }
            log.debug("Read from tool: tighteningDataDTO={}", tighteningDataDTO);
            deviceHandler.getTighteningDataService().save(tighteningData);
            break;

        // ... 注意：移除原来的 ((ToolHandler) deviceHandler) 强转
    }
}
```

- [ ] **Step 3: 调用方无需修改**

`AtlasPFSeriesHandler.setupChannelInitializer()` 和 `FitSeriesHandler.setupChannelInitializer()` 中 `self` 已是 `ToolHandler` 子类，无需改动。

- [ ] **Step 4: 编译 + 测试**

Run: `mvn compile -pl . -q && mvn test -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/netty/protocol/handler/atlas/AtlasPFSeriesInBoundHandler.java \
        src/main/java/com/tightening/netty/protocol/handler/fit/FitSeriesInBoundHandler.java
git commit -m "feat: wire parser into handlers with ToolHandler type safety and toolTypeName"
```

---

### Task 8: 集成验证 — 使用现有真实数据

**Files:**
- Modify: `src/test/java/com/tightening/netty/protocol/util/AtlasTighteningDataParserTest.java`

- [ ] **Step 1: 添加真实 rev 3 数据测试**

```java
@Test
void parse_realRev3Data_shouldSucceed() {
    String fullFrame = "0419006100310000    010000020003ND-5                     04780270-001680000000S00X3 05000006008070108000000900001000001101221301401511611711811912000001310722100092022001080230010002400020825000032600100270000028000002900100300500031000003200033000340003500000036005000370000003800000039999999400000004100000439584200000430000044A7550367      452026-06-02:13:43:19462025-11-30:15:37:2047P01 10.0??0.8N.m         4814901\0";
    byte[] fullBytes = fullFrame.getBytes(StandardCharsets.US_ASCII);
    byte[] data = java.util.Arrays.copyOfRange(fullBytes, 20, fullBytes.length - 1);

    TighteningData result = AtlasTighteningDataParser.parse(data, 3);

    assertThat(result.getControllerName()).isEqualTo("ND-5");
    assertThat(result.getVin()).isEqualTo("02780270-001680000000S00X3");
    assertThat(result.getTighteningId()).isEqualTo(14901);
    assertThat(result.getTighteningStatus()).isEqualTo(1);
    assertThat(result.getTorqueStatus()).isEqualTo(1);
    assertThat(result.getParameterSetName()).isEqualTo("P01 10.0??0.8N.m");
    assertThat(result.getRevision()).isEqualTo(3);
}
```

- [ ] **Step 2: 运行全量测试**

Run: `mvn test -pl . -q`
Expected: BUILD SUCCESS, 所有测试通过

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/tightening/netty/protocol/util/AtlasTighteningDataParserTest.java
git commit -m "test: add real rev 3 data integration test"
```

---

## 完成确认清单

- [ ] `mvn test -pl . -q` 全量通过
- [ ] Atlas rev 1-7/998/999 均有测试覆盖
- [ ] FIT 协议字段名同步完成，`FitDataUtils` 测试通过
- [ ] `revision` 列在解析后正确设置
- [ ] Spec 与 Plan 一致
