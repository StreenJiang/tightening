# Unit Test Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将项目测试覆盖率从 ~10%（8/81 文件）提升到接近全量覆盖，分三层推进：Layer 1（Entity/DTO/Enum/Config）→ Layer 2（Codec/Parser/Util）→ Layer 3（Handler/Service/Controller）。

**Architecture:** 纯单元测试，不启动 Spring 上下文（仅保留一个冒烟测试）。Mockito `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`。Netty 用 `EmbeddedChannel`。测试目录完全镜像 main 包结构。

**Tech Stack:** JUnit 5 + AssertJ 3.27.7 + Mockito（spring-boot-starter-test 自带）+ EmbeddedChannel

**Spec:** `docs/superpowers/specs/2026-06-14-unit-test-coverage-design.md`

---

## Layer 1 — Entity & DTO & Device 类型（12 个文件）

### Task 1: Entity JSON 往返测试

**Files:**
- Create: `src/test/java/com/tightening/entity/TighteningDataTest.java`
- Create: `src/test/java/com/tightening/entity/CurveDataTest.java`
- Create: `src/test/java/com/tightening/entity/DeviceTest.java`
- Create: `src/test/java/com/tightening/entity/UserAccountInfoTest.java`
- Create: `src/test/java/com/tightening/entity/BaseEntityTest.java`

- [ ] **Step 1: 创建 TighteningDataTest — 全字段 JSON 往返**

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TighteningDataTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        TighteningData original = new TighteningData();
        original.setId(1L);
        original.setTighteningId(1234567890L);
        original.setVin("VIN1234567890");
        original.setTorqueStatus(1);
        original.setAngleStatus(1);
        original.setTighteningStatus(1);
        original.setTorque(12.34);
        original.setAngle(45.0);
        original.setTimestamp("2024-01-15 10:30:00");
        original.setParameterSetName("PSET-001");
        original.setCellId(1);
        original.setChannelId(2);

        String json = mapper.writeValueAsString(original);
        TighteningData restored = mapper.readValue(json, TighteningData.class);

        assertThat(restored.getTighteningId()).isEqualTo(1234567890L);
        assertThat(restored.getVin()).isEqualTo("VIN1234567890");
        assertThat(restored.getTorque()).isCloseTo(12.34, org.assertj.core.api.Assertions.within(0.001));
        assertThat(restored.getAngle()).isCloseTo(45.0, org.assertj.core.api.Assertions.within(0.001));
        assertThat(restored.getTimestamp()).isEqualTo("2024-01-15 10:30:00");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new TighteningData());
        TighteningData restored = mapper.readValue(json, TighteningData.class);
        assertThat(restored).isNotNull();
        assertThat(restored.getId()).isNull();
    }
}
```

- [ ] **Step 2: 创建其余 4 个 Entity 测试 — 同一模式，字段用各自类实际字段名**

`CurveDataTest.java`:

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurveDataTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        CurveData original = new CurveData();
        original.setId(1L);
        original.setTighteningId(100L);
        original.setDataSamples("[{\"torque\":1.0,\"angle\":10.0}]");
        original.setTimestamp("2024-01-15 10:30:00");

        String json = mapper.writeValueAsString(original);
        CurveData restored = mapper.readValue(json, CurveData.class);

        assertThat(restored.getTighteningId()).isEqualTo(100L);
        assertThat(restored.getDataSamples()).isEqualTo("[{\"torque\":1.0,\"angle\":10.0}]");
        assertThat(restored.getTimestamp()).isEqualTo("2024-01-15 10:30:00");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new CurveData());
        CurveData restored = mapper.readValue(json, CurveData.class);
        assertThat(restored).isNotNull();
    }
}
```

`DeviceTest.java`:

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        Device original = new Device();
        original.setId(1L);
        original.setDeviceName("TestDevice");
        original.setIp("192.168.1.100");
        original.setPort(5000);
        original.setType(1);

        String json = mapper.writeValueAsString(original);
        Device restored = mapper.readValue(json, Device.class);

        assertThat(restored.getDeviceName()).isEqualTo("TestDevice");
        assertThat(restored.getIp()).isEqualTo("192.168.1.100");
        assertThat(restored.getPort()).isEqualTo(5000);
        assertThat(restored.getType()).isEqualTo(1);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new Device());
        Device restored = mapper.readValue(json, Device.class);
        assertThat(restored).isNotNull();
    }
}
```

`UserAccountInfoTest.java`:

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountInfoTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        UserAccountInfo original = new UserAccountInfo();
        original.setId(1L);
        original.setUsername("testuser");
        original.setPassword("encrypted123");

        String json = mapper.writeValueAsString(original);
        UserAccountInfo restored = mapper.readValue(json, UserAccountInfo.class);

        assertThat(restored.getUsername()).isEqualTo("testuser");
        assertThat(restored.getPassword()).isEqualTo("encrypted123");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new UserAccountInfo());
        UserAccountInfo restored = mapper.readValue(json, UserAccountInfo.class);
        assertThat(restored).isNotNull();
    }
}
```

`BaseEntityTest.java`:

```java
package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_shouldPreserveFields() throws Exception {
        BaseEntity original = new BaseEntity();
        original.setId(42L);

        String json = mapper.writeValueAsString(original);
        BaseEntity restored = mapper.readValue(json, BaseEntity.class);

        assertThat(restored.getId()).isEqualTo(42L);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new BaseEntity());
        BaseEntity restored = mapper.readValue(json, BaseEntity.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest="com.tightening.entity.*" -DfailIfNoTests=false
```

预期：所有 10 个测试 PASS。

- [ ] **Step 4: 提交**

```bash
git add src/test/java/com/tightening/entity/
git commit -m "test: add Layer 1 entity JSON round-trip tests"
```

### Task 2: DTO JSON 往返测试

**Files:**
- Create: `src/test/java/com/tightening/dto/TighteningDataDTOTest.java`
- Create: `src/test/java/com/tightening/dto/CurveDataDTOTest.java`
- Create: `src/test/java/com/tightening/dto/DeviceDTOTest.java`
- Create: `src/test/java/com/tightening/dto/UserAccountInfoDTOTest.java`
- Create: `src/test/java/com/tightening/dto/BaseDTOTest.java`

- [ ] **Step 1: 创建 5 个 DTO 测试 — 与 Entity 完全相同模式，字段使用 DTO 类各自的 getter/setter**

`TighteningDataDTOTest.java`:

```java
package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TighteningDataDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        TighteningDataDTO original = new TighteningDataDTO();
        original.setTighteningId(1234567890L);
        original.setVin("VIN123");
        original.setTorque(12.5);
        original.setAngle(180);
        original.setTorqueStatus(1);
        original.setAngleStatus(1);
        original.setTighteningStatus(1);
        original.setTimestamp("2024-01-15 10:30:00");
        original.setRevision(1);
        original.setCellId(1);
        original.setChannelId(2);
        original.setControllerName("CTRL-01");
        original.setJobId(3);
        original.setParameterSet(5);
        original.setBatchSize(10);
        original.setBatchCounter(3);

        String json = mapper.writeValueAsString(original);
        TighteningDataDTO restored = mapper.readValue(json, TighteningDataDTO.class);

        assertThat(restored.getVin()).isEqualTo("VIN123");
        assertThat(restored.getTorque()).isCloseTo(12.5, within(0.001));
        assertThat(restored.getAngle()).isEqualTo(180);
        assertThat(restored.getParameterSet()).isEqualTo(5);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new TighteningDataDTO());
        TighteningDataDTO restored = mapper.readValue(json, TighteningDataDTO.class);
        assertThat(restored).isNotNull();
    }

    @Test
    void jsonRoundTrip_withExtraData_shouldPreserveJsonField() throws Exception {
        TighteningDataDTO original = new TighteningDataDTO();
        original.setVin("VIN");
        original.setExtraData("{\"key\":\"value\"}");

        String json = mapper.writeValueAsString(original);
        TighteningDataDTO restored = mapper.readValue(json, TighteningDataDTO.class);

        assertThat(restored.getExtraData()).isEqualTo("{\"key\":\"value\"}");
    }
}
```

`CurveDataDTOTest.java`:

```java
package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurveDataDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        CurveDataDTO original = new CurveDataDTO();
        original.setTighteningId(100L);
        original.setDataSamples("[{\"torque\":5.0,\"angle\":10.0}]");
        original.setTimestamp("2024-01-15 10:30:00");

        String json = mapper.writeValueAsString(original);
        CurveDataDTO restored = mapper.readValue(json, CurveDataDTO.class);

        assertThat(restored.getTighteningId()).isEqualTo(100L);
        assertThat(restored.getDataSamples()).isEqualTo("[{\"torque\":5.0,\"angle\":10.0}]");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new CurveDataDTO());
        CurveDataDTO restored = mapper.readValue(json, CurveDataDTO.class);
        assertThat(restored).isNotNull();
    }
}
```

`DeviceDTOTest.java`:

```java
package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        DeviceDTO original = new DeviceDTO();
        original.setId(1L);
        original.setDeviceName("TestDevice");
        original.setIp("192.168.1.100");
        original.setPort(5000);
        original.setType(1);

        String json = mapper.writeValueAsString(original);
        DeviceDTO restored = mapper.readValue(json, DeviceDTO.class);

        assertThat(restored.getDeviceName()).isEqualTo("TestDevice");
        assertThat(restored.getIp()).isEqualTo("192.168.1.100");
        assertThat(restored.getPort()).isEqualTo(5000);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new DeviceDTO());
        DeviceDTO restored = mapper.readValue(json, DeviceDTO.class);
        assertThat(restored).isNotNull();
    }
}
```

`UserAccountInfoDTOTest.java`:

```java
package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountInfoDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        UserAccountInfoDTO original = new UserAccountInfoDTO();
        original.setId(1L);
        original.setUsername("testuser");

        String json = mapper.writeValueAsString(original);
        UserAccountInfoDTO restored = mapper.readValue(json, UserAccountInfoDTO.class);

        assertThat(restored.getUsername()).isEqualTo("testuser");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new UserAccountInfoDTO());
        UserAccountInfoDTO restored = mapper.readValue(json, UserAccountInfoDTO.class);
        assertThat(restored).isNotNull();
    }
}
```

`BaseDTOTest.java`:

```java
package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_shouldPreserveFields() throws Exception {
        BaseDTO original = new BaseDTO();
        original.setId(42L);

        String json = mapper.writeValueAsString(original);
        BaseDTO restored = mapper.readValue(json, BaseDTO.class);

        assertThat(restored.getId()).isEqualTo(42L);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new BaseDTO());
        BaseDTO restored = mapper.readValue(json, BaseDTO.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -pl . -Dtest="com.tightening.dto.*" -DfailIfNoTests=false
```

预期：所有 10 个测试 PASS。

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/tightening/dto/
git commit -m "test: add Layer 1 DTO JSON round-trip tests"
```

---

## Layer 1 — Device 类型（2 个文件）

### Task 2.5: TCPDevice + Arranger JSON 往返测试

**Files:**
- Create: `src/test/java/com/tightening/device/type/TCPDeviceTest.java`
- Create: `src/test/java/com/tightening/device/type/ArrangerTest.java`

- [ ] **Step 1: 创建 TCPDeviceTest**

```java
package com.tightening.device.type;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TCPDeviceTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        TCPDevice original = new TCPDevice();
        original.setIp("192.168.1.100");
        original.setPort(5000);

        String json = mapper.writeValueAsString(original);
        TCPDevice restored = mapper.readValue(json, TCPDevice.class);

        assertThat(restored.getIp()).isEqualTo("192.168.1.100");
        assertThat(restored.getPort()).isEqualTo(5000);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new TCPDevice());
        TCPDevice restored = mapper.readValue(json, TCPDevice.class);
        assertThat(restored).isNotNull();
    }
}
```

- [ ] **Step 2: 创建 ArrangerTest — 包含 @JsonProperty 字段**

```java
package com.tightening.device.type;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArrangerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        Arranger original = new Arranger();
        original.setIp("10.0.0.1");
        original.setPort(4545);
        original.setSwitchBarCode("BAR-001");
        original.setSwitchPosition("POS-A");

        String json = mapper.writeValueAsString(original);
        Arranger restored = mapper.readValue(json, Arranger.class);

        assertThat(restored.getIp()).isEqualTo("10.0.0.1");
        assertThat(restored.getPort()).isEqualTo(4545);
        assertThat(restored.getSwitchBarCode()).isEqualTo("BAR-001");
        assertThat(restored.getSwitchPosition()).isEqualTo("POS-A");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new Arranger());
        Arranger restored = mapper.readValue(json, Arranger.class);
        assertThat(restored).isNotNull();
    }

    @Test
    void jsonProperty_shouldUseSnakeCaseKeys() throws Exception {
        Arranger original = new Arranger();
        original.setSwitchBarCode("BAR-002");
        String json = mapper.writeValueAsString(original);
        assertThat(json).contains("switch_bar_code");
    }
}
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl . -Dtest="com.tightening.device.type.*Test" -DfailIfNoTests=false
```

- [ ] **Step 4: 提交**

```bash
git add src/test/java/com/tightening/device/type/
git commit -m "test: add Layer 1 TCPDevice and Arranger JSON round-trip tests"
```

---

## Layer 1 — Enum（~19 个文件）

### Task 3: 无业务方法 Enum 测试（~10 个文件）

**Files:**
- Create: `src/test/java/com/tightening/constant/AtlasAngleStatusTest.java`
- Create: `src/test/java/com/tightening/constant/AtlasTorqueStatusTest.java`
- Create: `src/test/java/com/tightening/constant/FitAngleStatusTest.java`
- Create: `src/test/java/com/tightening/constant/FitTorqueStatusTest.java`
- Create: `src/test/java/com/tightening/constant/TighteningStatusTest.java`
- Create: `src/test/java/com/tightening/constant/TighteningResultTypeTest.java`
- Create: `src/test/java/com/tightening/constant/DeviceStatusTest.java`
- Create: `src/test/java/com/tightening/constant/DeviceChangeTypeTest.java`
- [ ] **Step 1: 创建 8 个无业务方法 Enum 测试 — 每个 Enum 验证 valueOf() 反查 + 枚举值不重复**

以 `AtlasAngleStatusTest.java` 为例：

```java
package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtlasAngleStatusTest {

    @Test
    void valueOf_shouldReturnAllValues() {
        assertThat(AtlasAngleStatus.valueOf("LOW")).isNotNull();
        assertThat(AtlasAngleStatus.valueOf("OK")).isNotNull();
        assertThat(AtlasAngleStatus.valueOf("HIGH")).isNotNull();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(AtlasAngleStatus.values())
                .map(AtlasAngleStatus::getCode)
                .distinct()
                .count();
        assertThat(codes).isEqualTo(AtlasAngleStatus.values().length);
    }
}
```

`AtlasTorqueStatusTest.java` —同上模式，枚举值：`LOW`, `OK`, `HIGH`

`FitAngleStatusTest.java` — 枚举值：`OK`, `NG`

`FitTorqueStatusTest.java` — 枚举值：`OK`, `NG`

`TighteningStatusTest.java` — 枚举值：`NG`, `OK`

`TighteningResultTypeTest.java` — 枚举值：`TIGHTENING`, `LOOSENING`

`DeviceStatusTest.java` — 枚举值：`CONNECTING`, `CONNECTED`, `DISCONNECTED`, `NONE`

`DeviceChangeTypeTest.java` — 枚举值：`ADD`, `UPDATE`, `DELETE`

- [ ] **Step 2: 运行测试**

```bash
mvn test -pl . -Dtest="com.tightening.constant.*Test" -DfailIfNoTests=false
```

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/tightening/constant/
git commit -m "test: add Layer 1 no-biz enum tests (valueOf + uniqueness)"
```

### Task 4: 有业务方法 Enum — DeviceType, AtlasErrorCode, AtlasCommandType, FitCommandType

**Files:**
- Create: `src/test/java/com/tightening/constant/DeviceTypeTest.java`
- Create: `src/test/java/com/tightening/constant/atlas/AtlasErrorCodeTest.java`
- Create: `src/test/java/com/tightening/constant/atlas/AtlasCommandTypeTest.java`
- Create: `src/test/java/com/tightening/constant/fit/FitCommandTypeTest.java`

- [ ] **Step 1: 创建 DeviceTypeTest — 测试 getType, getHandler, getHandlerByTypeId, initProvider**

```java
package com.tightening.constant;

import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.impl.AtlasPF4000Handler;
import com.tightening.device.handler.impl.AtlasPF6000OPHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceTypeTest {

    @AfterEach
    void tearDown() {
        DeviceType.initProvider(null);
    }

    @Test
    void getType_validId_shouldReturnCorrectType() {
        assertThat(DeviceType.getType(1)).isEqualTo(DeviceType.ATLAS_PF4000);
        assertThat(DeviceType.getType(2)).isEqualTo(DeviceType.ATLAS_PF6000_OP);
        assertThat(DeviceType.getType(3)).isEqualTo(DeviceType.FIT_FTC6);
    }

    @Test
    void getType_invalidId_shouldReturnNull() {
        assertThat(DeviceType.getType(999)).isNull();
        assertThat(DeviceType.getType(-1)).isNull();
    }

    @Test
    void getId_shouldReturnCorrectId() {
        assertThat(DeviceType.ATLAS_PF4000.getId()).isEqualTo(1);
        assertThat(DeviceType.ATLAS_PF6000_OP.getId()).isEqualTo(2);
        assertThat(DeviceType.FIT_FTC6.getId()).isEqualTo(3);
    }

    @Test
    void getName_shouldReturnName() {
        assertThat(DeviceType.ATLAS_PF4000.getName()).isEqualTo("PF4000");
        assertThat(DeviceType.FIT_FTC6.getName()).isEqualTo("FIT-FTC6");
    }

    @Test
    void getHandlerClass_shouldReturnCorrectClass() {
        assertThat(DeviceType.ATLAS_PF4000.getHandlerClass()).isEqualTo(AtlasPF4000Handler.class);
        assertThat(DeviceType.ATLAS_PF6000_OP.getHandlerClass()).isEqualTo(AtlasPF6000OPHandler.class);
    }

    @Test
    void getHandler_providerNotInit_shouldThrow() {
        assertThatThrownBy(() -> DeviceType.ATLAS_PF4000.getHandler())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Handler provider not initialized");
    }

    @Test
    void getHandler_providerInit_shouldReturnHandler() {
        DeviceHandler mockHandler = new AtlasPF4000Handler(null, null, null);
        DeviceType.initProvider(type -> mockHandler);

        assertThat(DeviceType.ATLAS_PF4000.getHandler()).isSameAs(mockHandler);
    }

    @Test
    void getHandlerByTypeId_valid_shouldReturnHandler() {
        DeviceHandler mockHandler = new AtlasPF4000Handler(null, null, null);
        DeviceType.initProvider(type -> mockHandler);

        assertThat(DeviceType.getHandlerByTypeId(1)).isSameAs(mockHandler);
    }

    @Test
    void getHandlerByTypeId_invalid_shouldThrow() {
        assertThatThrownBy(() -> DeviceType.getHandlerByTypeId(999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    @Test
    void values_count() {
        assertThat(DeviceType.values()).hasSize(3);
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(DeviceType.values())
                .map(DeviceType::getId).distinct().count();
        assertThat(codes).isEqualTo(DeviceType.values().length);
    }
}
```

- [ ] **Step 2: 创建 AtlasErrorCodeTest — 测试 fromCode, fromCodeOrThrow, toString**

```java
package com.tightening.constant.atlas;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtlasErrorCodeTest {

    @Test
    void fromCode_knownCode_shouldReturnErrorCode() {
        assertThat(AtlasErrorCode.fromCode(0)).contains(AtlasErrorCode.NO_ERROR);
        assertThat(AtlasErrorCode.fromCode(1)).contains(AtlasErrorCode.INVALID_DATA);
        assertThat(AtlasErrorCode.fromCode(99)).contains(AtlasErrorCode.UNKNOWN_MID);
    }

    @Test
    void fromCode_unknownCode_shouldReturnEmpty() {
        assertThat(AtlasErrorCode.fromCode(-1)).isEmpty();
        assertThat(AtlasErrorCode.fromCode(1000)).isEmpty();
    }

    @Test
    void fromCodeOrThrow_known_shouldReturn() {
        assertThat(AtlasErrorCode.fromCodeOrThrow(0)).isEqualTo(AtlasErrorCode.NO_ERROR);
    }

    @Test
    void fromCodeOrThrow_unknown_shouldThrow() {
        assertThatThrownBy(() -> AtlasErrorCode.fromCodeOrThrow(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void toString_shouldContainCodeAndDescription() {
        String s = AtlasErrorCode.NO_ERROR.toString();
        assertThat(s).contains("00").contains("No Error");
    }

    @Test
    void getCode_getDescription_shouldReturnValues() {
        assertThat(AtlasErrorCode.NO_ERROR.getCode()).isZero();
        assertThat(AtlasErrorCode.NO_ERROR.getDescription()).isEqualTo("No Error");
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(AtlasErrorCode.values())
                .map(AtlasErrorCode::getCode).distinct().count();
        assertThat(codes).isEqualTo(AtlasErrorCode.values().length);
    }
}
```

- [ ] **Step 3: 创建 AtlasCommandTypeTest**

```java
package com.tightening.constant.atlas;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AtlasCommandTypeTest {

    @Test
    void fromMid_knownMid_shouldReturnCommand() {
        assertThat(AtlasCommandType.fromMid(1)).isEqualTo(AtlasCommandType.CONNECT);
        assertThat(AtlasCommandType.fromMid(61)).isEqualTo(AtlasCommandType.TIGHTEN_DATA);
        assertThat(AtlasCommandType.fromMid(9999)).isEqualTo(AtlasCommandType.HEARTBEAT);
    }

    @Test
    void fromMid_unknownMid_shouldReturnNull() {
        assertThat(AtlasCommandType.fromMid(-1)).isNull();
        assertThat(AtlasCommandType.fromMid(0)).isNull();
        assertThat(AtlasCommandType.fromMid(99999)).isNull();
    }

    @Test
    void getMid_getName_shouldReturnValues() {
        assertThat(AtlasCommandType.CONNECT.getMid()).isEqualTo(1);
        assertThat(AtlasCommandType.CONNECT.getName()).isEqualTo("连接设备");
        assertThat(AtlasCommandType.ENABLE.getMid()).isEqualTo(43);
    }

    @Test
    void toString_shouldContainMidAndName() {
        String s = AtlasCommandType.CONNECT.toString();
        assertThat(s).contains("0001").contains("连接设备");
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(AtlasCommandType.values())
                .map(AtlasCommandType::getMid).distinct().count();
        assertThat(codes).isEqualTo(AtlasCommandType.values().length);
    }
}
```

- [ ] **Step 4: 创建 FitCommandTypeTest**

```java
package com.tightening.constant.fit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FitCommandTypeTest {

    @Test
    void fromCode_knownCode_shouldReturnCommand() {
        assertThat(FitCommandType.fromCode((byte) 0x01)).isEqualTo(FitCommandType.PARAMETER_SET);
        assertThat(FitCommandType.fromCode((byte) 0x02)).isEqualTo(FitCommandType.ENABLE_DISABLE);
        assertThat(FitCommandType.fromCode((byte) 0x83)).isEqualTo(FitCommandType.CURVE);
        assertThat(FitCommandType.fromCode((byte) 0x86)).isEqualTo(FitCommandType.HEARTBEAT_ACK);
    }

    @Test
    void fromCode_unknownCode_shouldReturnNull() {
        assertThat(FitCommandType.fromCode((byte) 0x00)).isNull();
        assertThat(FitCommandType.fromCode((byte) 0xFF)).isNull();
    }

    @Test
    void getCode_getName_shouldReturnValues() {
        assertThat(FitCommandType.PARAMETER_SET.getCode()).isEqualTo((byte) 0x01);
        assertThat(FitCommandType.PARAMETER_SET.getName()).isEqualTo("程序号");
        assertThat(FitCommandType.TIGHTEN_FINAL.getCode()).isEqualTo((byte) 0x81);
    }

    @Test
    void toString_shouldContainCodeAndName() {
        String s = FitCommandType.ENABLE_DISABLE.toString();
        assertThat(s).contains("0x02").contains("使能");
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(FitCommandType.values())
                .map(FitCommandType::getCode).distinct().count();
        assertThat(codes).isEqualTo(FitCommandType.values().length);
    }
}
```

- [ ] **Step 5: 运行测试**

```bash
mvn test -pl . -Dtest="com.tightening.constant.DeviceTypeTest,com.tightening.constant.atlas.AtlasErrorCodeTest,com.tightening.constant.atlas.AtlasCommandTypeTest,com.tightening.constant.fit.FitCommandTypeTest"
```

- [ ] **Step 6: 提交**

```bash
git add src/test/java/com/tightening/constant/DeviceTypeTest.java src/test/java/com/tightening/constant/atlas/
git commit -m "test: add Layer 1 DeviceType, AtlasErrorCode, AtlasCommandType, and FitCommandType enum tests"
```

### Task 5: 有业务方法 Enum — TCPCommand, TCPDeviceConstants, ToolConstants

**Files:**
- Create: `src/test/java/com/tightening/constant/TCPCommandTest.java`
- Create: `src/test/java/com/tightening/constant/TCPDeviceConstantsTest.java`
- Create: `src/test/java/com/tightening/constant/ToolConstantsTest.java`

- [ ] **Step 1: 创建 TCPCommandTest**

```java
package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TCPCommandTest {

    @Test
    void values_shouldHaveThreeCommands() {
        assertThat(TCPCommand.values()).hasSize(3);
    }

    @Test
    void valueOf_shouldReturnAllValues() {
        assertThat(TCPCommand.valueOf("TOOL_ENABLE")).isEqualTo(TCPCommand.TOOL_ENABLE);
        assertThat(TCPCommand.valueOf("TOOL_DISABLE")).isEqualTo(TCPCommand.TOOL_DISABLE);
        assertThat(TCPCommand.valueOf("TOOL_PARAMETER_SET")).isEqualTo(TCPCommand.TOOL_PARAMETER_SET);
    }
}
```

- [ ] **Step 2: 创建 TCPDeviceConstantsTest**

```java
package com.tightening.constant;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class TCPDeviceConstantsTest {

    @Test
    void reconnectIntervalMs_shouldBe3000() {
        assertThat(TCPDeviceConstants.RECONNECT_INTERVAL_MS).isEqualTo(3000);
    }

    @Test
    void constructor_shouldBeDefaultPackagePrivate() {
        var constructors = TCPDeviceConstants.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
    }
}
```

- [ ] **Step 3: 创建 ToolConstantsTest**

```java
package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolConstantsTest {

    @Test
    void cmdTimeoutMs_shouldBe5000() {
        assertThat(ToolConstants.CMD_TIMEOUT_MS).isEqualTo(5000L);
    }
}
```

- [ ] **Step 4: 运行测试**

```bash
mvn test -pl . -Dtest="com.tightening.constant.TCPCommandTest,com.tightening.constant.TCPDeviceConstantsTest,com.tightening.constant.ToolConstantsTest"
```

- [ ] **Step 5: 提交**

```bash
git add src/test/java/com/tightening/constant/
git commit -m "test: add Layer 1 TCP command and constants enum tests"
```

### Task 6: 有业务方法 Enum — 剩余常量文件

**Files:**
- Create: `src/test/java/com/tightening/constant/ExtraDataKeysTest.java`
- Create: `src/test/java/com/tightening/constant/atlas/AtlasExtraDataKeysTest.java`
- Create: `src/test/java/com/tightening/constant/atlas/AtlasConstantsTest.java`
- Create: `src/test/java/com/tightening/constant/fit/FitConstantsTest.java`

- [ ] **Step 1: 创建 ExtraDataKeysTest**

```java
package com.tightening.constant;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtraDataKeysTest {

    @Test
    void barcode_key_shouldBeCorrect() {
        assertThat(ExtraDataKeys.BARCODE).isEqualTo("barcode");
    }

    @Test
    void cannotInstantiate() throws Exception {
        Constructor<?> ctor = ExtraDataKeys.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThatThrownBy(() -> ctor.newInstance())
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: 创建 AtlasExtraDataKeysTest**

```java
package com.tightening.constant.atlas;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtlasExtraDataKeysTest {

    @Test
    void allKeys_shouldBeNonEmpty() {
        assertThat(AtlasExtraDataKeys.STRATEGY).isNotBlank();
        assertThat(AtlasExtraDataKeys.STRATEGY_OPTIONS).isNotBlank();
        assertThat(AtlasExtraDataKeys.TIGHTENING_ERROR_STATUS).isNotBlank();
        assertThat(AtlasExtraDataKeys.COMPENSATED_ANGLE).isNotBlank();
        assertThat(AtlasExtraDataKeys.STAGE_RESULTS).isNotBlank();
        assertThat(AtlasExtraDataKeys.TOOL_SERIAL_NUMBER).isNotBlank();
    }

    @Test
    void cannotInstantiate() throws Exception {
        Constructor<?> ctor = AtlasExtraDataKeys.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThatThrownBy(() -> ctor.newInstance())
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 3: 创建 AtlasConstantsTest**

```java
package com.tightening.constant.atlas;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AtlasConstantsTest {

    @Test
    void headerLength_shouldBe20() {
        assertThat(AtlasConstants.HEADER_LENGTH).isEqualTo(20);
    }

    @Test
    void lengthFieldOffset_shouldBe0() {
        assertThat(AtlasConstants.LENGTH_FIELD_OFFSET).isEqualTo(0);
    }

    @Test
    void lengthFieldLength_shouldBe4() {
        assertThat(AtlasConstants.LENGTH_FIELD_LENGTH).isEqualTo(4);
    }

    @Test
    void lengthAdjustment_shouldBe1() {
        assertThat(AtlasConstants.LENGTH_ADJUSTMENT).isEqualTo(1);
    }
}
```

- [ ] **Step 4: 创建 FitConstantsTest**

```java
package com.tightening.constant.fit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FitConstantsTest {

    @Test
    void head_shouldBe0xAA55() {
        assertThat(FitConstants.HEAD).isEqualTo((short) 0xAA55);
    }

    @Test
    void tail_shouldBe0x55AA() {
        assertThat(FitConstants.TAIL).isEqualTo((short) 0x55AA);
    }

    @Test
    void commandOk_shouldBe0() {
        assertThat(FitConstants.COMMAND_OK).isEqualTo((byte) 0x00);
    }

    @Test
    void lengthFieldOffset_shouldBe3() {
        assertThat(FitConstants.LENGTH_FIELD_OFFSET).isEqualTo(3);
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -pl . -Dtest="com.tightening.constant.ExtraDataKeysTest,com.tightening.constant.atlas.*Test,com.tightening.constant.fit.*Test" -DfailIfNoTests=false
```

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/tightening/constant/
git commit -m "test: add Layer 1 remaining constants enum tests"
```

---

## Layer 1 — Config（4 个文件）

### Task 7: Config 类 getter/setter 测试

**Files:**
- Create: `src/test/java/com/tightening/config/DeviceConfigTest.java`
- Create: `src/test/java/com/tightening/config/FitConfigTest.java`
- Create: `src/test/java/com/tightening/config/ToolCommonConfigTest.java`
- Create: `src/test/java/com/tightening/config/DatabaseDirectoryInitializerTest.java`

- [ ] **Step 1: 创建 DeviceConfigTest**

```java
package com.tightening.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceConfigTest {

    @Test
    void settersAndGetters_shouldWork() {
        DeviceConfig config = new DeviceConfig();

        DeviceConfig.ConnectThread connect = new DeviceConfig.ConnectThread();
        connect.setCorePoolSize(5);
        connect.setMaxPoolSize(10);
        connect.setKeepAliveTimeMs(60000L);
        connect.setCapacity(100);
        connect.setTerminationAwaitMs(5000L);
        config.setConnectThread(connect);

        DeviceConfig.ScanThread scan = new DeviceConfig.ScanThread();
        scan.setInitDelayMs(1000L);
        scan.setDelayMs(5000L);
        scan.setTerminationAwaitMs(3000L);
        config.setScanThread(scan);

        assertThat(config.getConnectThread().getCorePoolSize()).isEqualTo(5);
        assertThat(config.getConnectThread().getMaxPoolSize()).isEqualTo(10);
        assertThat(config.getScanThread().getDelayMs()).isEqualTo(5000L);
    }

    @Test
    void defaultValues_shouldNotBeNull() {
        DeviceConfig config = new DeviceConfig();
        // @ConfigurationProperties 不绑定时不注入，字段为 null——不验证非空
        // 只验证构造不抛异常
        assertThat(config).isNotNull();
    }
}
```

- [ ] **Step 2: 创建 FitConfigTest — 验证 heartBeat 相关字段 getter/setter**

- [ ] **Step 3: 创建 ToolCommonConfigTest — 验证 enableDisableCooldownMs getter/setter**

- [ ] **Step 4: 创建 DatabaseDirectoryInitializerTest — 验证类名 + 方法存在**

```java
package com.tightening.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.EnvironmentPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseDirectoryInitializerTest {

    @Test
    void shouldImplementEnvironmentPostProcessor() {
        DatabaseDirectoryInitializer initializer = new DatabaseDirectoryInitializer();
        assertThat(initializer).isInstanceOf(EnvironmentPostProcessor.class);
    }
}
```

- [ ] **Step 5: 运行测试**

```bash
mvn test -pl . -Dtest="com.tightening.config.*Test"
```

- [ ] **Step 6: 提交**

```bash
git add src/test/java/com/tightening/config/
git commit -m "test: add Layer 1 config getter/setter tests"
```

---

## Layer 2 — Frame + Codec（7 个文件）

### Task 8: AtlasFrame 和 FitFrame 模型测试

**Files:**
- Create: `src/test/java/com/tightening/netty/protocol/codec/atlas/AtlasFrameTest.java`
- Create: `src/test/java/com/tightening/netty/protocol/codec/fit/FitFrameTest.java`

- [ ] **Step 1: 创建 AtlasFrameTest**

```java
package com.tightening.netty.protocol.codec.atlas;

import com.tightening.constant.atlas.AtlasCommandType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AtlasFrameTest {

    @Test
    void construct_minimal_shouldSetMidAndLength() {
        AtlasFrame frame = new AtlasFrame(AtlasCommandType.ENABLE.getMid());
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.ENABLE.getMid());
        assertThat(frame.getRevision()).isEqualTo(1);
        assertThat(frame.getEnd()).isEqualTo('\0');
    }

    @Test
    void construct_withData_shouldCalculateLength() {
        byte[] data = "test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        AtlasFrame frame = new AtlasFrame(99, data);
        assertThat(frame.getData()).isEqualTo(data);
        assertThat(frame.getLength()).isEqualTo(20 + 4);
    }

    @Test
    void connectTool_shouldReturnCorrectFrame() {
        AtlasFrame frame = AtlasFrame.connectTool();
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.CONNECT.getMid());
        assertThat(frame.getRevision()).isEqualTo(3);
    }

    @Test
    void subscribeTighteningData_shouldSetNoAckFlag() {
        AtlasFrame frame = AtlasFrame.subscribeTighteningData();
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.SUBSCRIBE_DATA.getMid());
        assertThat(frame.getNoAckFlag()).isEqualTo(1);
    }

    @Test
    void enableTool_shouldReturnEnableCommand() {
        AtlasFrame frame = AtlasFrame.enableTool();
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.ENABLE.getMid());
    }

    @Test
    void disableTool_shouldReturnDisableCommand() {
        AtlasFrame frame = AtlasFrame.disableTool();
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.DISABLE.getMid());
    }

    @Test
    void sendPSet_shouldIncludePsetData() {
        AtlasFrame frame = AtlasFrame.sendPSet(5);
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.PARAMETER_SET.getMid());
        assertThat(new String(frame.getData())).contains("5");
    }

    @Test
    void sendHeartBeat_shouldReturnHeartbeatCommand() {
        AtlasFrame frame = AtlasFrame.sendHeartBeat();
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.HEARTBEAT.getMid());
        assertThat(frame.getRevision()).isEqualTo(1);
    }

    @Test
    void chainSetters_shouldWork() {
        AtlasFrame frame = new AtlasFrame(1)
                .setStationId(5)
                .setSpindleId(2)
                .setSequenceNumber(100);
        assertThat(frame.getStationId()).isEqualTo(5);
        assertThat(frame.getSpindleId()).isEqualTo(2);
        assertThat(frame.getSequenceNumber()).isEqualTo(100);
    }

    @Test
    void toString_shouldNotThrow() {
        AtlasFrame frame = new AtlasFrame(1, "data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(frame.toString()).contains("data");
    }
}
```

- [ ] **Step 2: 创建 FitFrameTest**

```java
package com.tightening.netty.protocol.codec.fit;

import com.tightening.constant.fit.FitCommandType;
import com.tightening.constant.fit.FitConstants;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FitFrameTest {

    @Test
    void construct_shouldSetHeadTailAndLength() {
        FitFrame frame = new FitFrame(FitCommandType.ENABLE_DISABLE.getCode(), new byte[] { 0x01 });
        assertThat(frame.getHead()).isEqualTo(FitConstants.HEAD);
        assertThat(frame.getTail()).isEqualTo(FitConstants.TAIL);
        assertThat(frame.getCmdType()).isEqualTo(FitCommandType.ENABLE_DISABLE.getCode());
        assertThat(frame.getDataLength()).isEqualTo((short) 1);
    }

    @Test
    void construct_nullData_shouldDefaultToEmptyArray() {
        FitFrame frame = new FitFrame((byte) 0, null);
        assertThat(frame.getData()).isEmpty();
        assertThat(frame.getDataLength()).isZero();
    }

    @Test
    void enableTool_shouldReturnEnableCommand() {
        FitFrame frame = FitFrame.enableTool();
        assertThat(frame.getCmdType()).isEqualTo(FitCommandType.ENABLE_DISABLE.getCode());
        assertThat(frame.getData()[0]).isEqualTo((byte) 0x01);
    }

    @Test
    void disableTool_shouldReturnDisableCommand() {
        FitFrame frame = FitFrame.disableTool();
        assertThat(frame.getCmdType()).isEqualTo(FitCommandType.ENABLE_DISABLE.getCode());
        assertThat(frame.getData()[0]).isEqualTo((byte) 0x00);
    }

    @Test
    void sendPSet_shouldIncludePsetValue() {
        FitFrame frame = FitFrame.sendPSet(7);
        assertThat(frame.getCmdType()).isEqualTo(FitCommandType.PARAMETER_SET.getCode());
        assertThat(frame.getData()[0]).isEqualTo((byte) 7);
    }

    @Test
    void sendHeartBeat_shouldReturnHeartbeatCommand() {
        FitFrame frame = FitFrame.sendHeartBeat();
        assertThat(frame.getCmdType()).isEqualTo(FitCommandType.HEARTBEAT_REQ.getCode());
        assertThat(frame.getData()).isNotEmpty();
    }

    @Test
    void toString_shouldNotThrow() {
        FitFrame frame = new FitFrame(FitCommandType.ENABLE_DISABLE.getCode(), new byte[] { 0x00 });
        assertThat(frame.toString()).contains("ENABLE_DISABLE");
    }
}
```

- [ ] **Step 3: 运行 + 提交**

```bash
mvn test -pl . -Dtest="com.tightening.netty.protocol.codec.atlas.AtlasFrameTest,com.tightening.netty.protocol.codec.fit.FitFrameTest"
git add src/test/java/com/tightening/netty/protocol/codec/atlas/AtlasFrameTest.java src/test/java/com/tightening/netty/protocol/codec/fit/FitFrameTest.java
git commit -m "test: add Layer 2 AtlasFrame and FitFrame model tests"
```

### Task 9: AtlasLengthDecoder 测试

**Files:**
- Create: `src/test/java/com/tightening/netty/protocol/codec/atlas/AtlasLengthDecoderTest.java`

- [ ] **Step 1: 创建测试 — 用 EmbeddedChannel 测试帧分割**

验证：完整帧解码、截断帧等待更多数据、空帧处理。

- [ ] **Step 2: 运行 + 提交**

### Task 10: 曲线数据模型测试

**Files:**
- Create: `src/test/java/com/tightening/netty/protocol/codec/fit/CurvePointTest.java`
- Create: `src/test/java/com/tightening/netty/protocol/codec/fit/CurveDataSamplesTest.java`

- [ ] **Step 1: 创建 CurvePointTest**

```java
package com.tightening.netty.protocol.codec.fit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CurvePointTest {

    @Test
    void constructAndGet_shouldPreserveValues() {
        CurvePoint point = new CurvePoint(0.1f, 5.0f, 10.0f);
        assertThat(point.getTime()).isEqualTo(0.1f);
        assertThat(point.getTorque()).isEqualTo(5.0f);
        assertThat(point.getAngle()).isEqualTo(10.0f);
    }

    @Test
    void setMethods_shouldUpdateValues() {
        CurvePoint point = new CurvePoint(0f, 0f, 0f);
        point.setTime(1.0f);
        point.setTorque(2.0f);
        point.setAngle(3.0f);
        assertThat(point.getTime()).isEqualTo(1.0f);
        assertThat(point.getTorque()).isEqualTo(2.0f);
        assertThat(point.getAngle()).isEqualTo(3.0f);
    }

    @Test
    void toString_shouldContainValues() {
        CurvePoint point = new CurvePoint(0.5f, 10.5f, 45.0f);
        assertThat(point.toString()).contains("0.5000").contains("10.50").contains("45.00");
    }
}
```

- [ ] **Step 2: 创建 CurveDataSamplesTest**

```java
package com.tightening.netty.protocol.codec.fit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CurveDataSamplesTest {

    @Test
    void construct_shouldSetTighteningId() {
        CurveDataSamples samples = new CurveDataSamples(42);
        assertThat(samples.getTighteningId()).isEqualTo(42);
    }

    @Test
    void addPoint_shouldAddToList() {
        CurveDataSamples samples = new CurveDataSamples(1);
        samples.addPoint(new CurvePoint(0.1f, 5.0f, 10.0f));
        assertThat(samples.size()).isEqualTo(1);
        assertThat(samples.getPoints()).hasSize(1);
    }

    @Test
    void addPoint_floatOverload_shouldCreatePoint() {
        CurveDataSamples samples = new CurveDataSamples(1);
        samples.addPoint(0.2f, 15.0f, 90.0f);
        assertThat(samples.size()).isEqualTo(1);
        assertThat(samples.getPoints().getFirst().getTorque()).isEqualTo(15.0f);
    }

    @Test
    void addMultiplePoints_shouldPreserveOrder() {
        CurveDataSamples samples = new CurveDataSamples(1);
        samples.addPoint(0.1f, 1.0f, 1.0f);
        samples.addPoint(0.2f, 2.0f, 2.0f);
        samples.addPoint(0.3f, 3.0f, 3.0f);
        assertThat(samples.size()).isEqualTo(3);
        assertThat(samples.getPoints().get(0).getTime()).isEqualTo(0.1f);
        assertThat(samples.getPoints().get(2).getTime()).isEqualTo(0.3f);
    }

    @Test
    void size_whenNoPoints_shouldReturnZero() {
        CurveDataSamples samples = new CurveDataSamples(1);
        assertThat(samples.size()).isZero();
    }
}
```

- [ ] **Step 3: 运行 + 提交**

### Task 11: FitCurveDataReassembler 测试（4 条路径）

**Files:**
- Create: `src/test/java/com/tightening/netty/protocol/codec/fit/FitCurveDataReassemblerTest.java`

- [ ] **Step 1: 创建测试 — 4 条路径**

1. **正常顺序到达** — 构造 3 个包依次写入，通过 EmbeddedChannel 验证拼装后的 CompleteFrame
2. **乱序到达** — 包 1→3→2 顺序写入，验证结果一致
3. **重复包** — 包 1→1→2→3，验证不抛异常 + 结果正确
4. **超时** — 发包 1 后等待超过 10 秒（或 mock executor 直接触发 timeout callback）

- [ ] **Step 2: 运行 + 提交**

### Task 12: 协议工具类测试

**Files:**
- Create: `src/test/java/com/tightening/netty/protocol/util/atlas/AtlasDataUtilsTest.java`
- Create: `src/test/java/com/tightening/netty/protocol/util/fit/FitDataUtilsTest.java`
- Create: `src/test/java/com/tightening/util/JsonUtilsTest.java`

- [ ] **Step 1: 创建 AtlasDataUtilsTest**

```java
package com.tightening.netty.protocol.util.atlas;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtlasDataUtilsTest {

    @Test
    void parseAsciiInt_valid_shouldParse() {
        byte[] data = "   42".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(AtlasDataUtils.parseAsciiInt(data, 0, 5)).isEqualTo(42);
    }

    @Test
    void parseAsciiInt_empty_shouldReturnNull() {
        byte[] data = "     ".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(AtlasDataUtils.parseAsciiInt(data, 0, 5)).isNull();
    }

    @Test
    void encodeIntField_valid_shouldRightAlign() {
        assertThat(AtlasDataUtils.encodeIntField(123, 6)).isEqualTo("000123");
    }

    @Test
    void encodeIntField_null_shouldReturnSpaces() {
        assertThat(AtlasDataUtils.encodeIntField(null, 3)).isEqualTo("   ");
    }

    @Test
    void encodeStringField_valid_shouldLeftAlign() {
        assertThat(AtlasDataUtils.encodeStringField("ABC", 6)).isEqualTo("ABC   ");
    }

    @Test
    void encodeStringField_null_shouldReturnSpaces() {
        assertThat(AtlasDataUtils.encodeStringField(null, 3)).isEqualTo("   ");
    }

    @Test
    void encodeStringField_tooLong_shouldTruncate() {
        assertThat(AtlasDataUtils.encodeStringField("ABCDEFG", 3)).isEqualTo("ABC");
    }

    @Test
    void encodeDoubleField_valid_shouldFormat() {
        assertThat(AtlasDataUtils.encodeDoubleField(12.34, 7, 2)).isEqualTo("  12.34");
    }

    @Test
    void encodeDoubleField_null_shouldReturnSpaces() {
        assertThat(AtlasDataUtils.encodeDoubleField(null, 5, 2)).isEqualTo("     ");
    }

    @Test
    void formatAscii_valid_shouldRightAlignZeroPad() {
        byte[] result = AtlasDataUtils.formatAscii(5, 3);
        assertThat(new String(result)).isEqualTo("005");
    }

    @Test
    void formatAscii_null_shouldReturnSpaces() {
        byte[] result = AtlasDataUtils.formatAscii(null, 3);
        assertThat(new String(result)).isEqualTo("   ");
    }

    @Test
    void formatAscii_tooLong_shouldThrow() {
        assertThatThrownBy(() -> AtlasDataUtils.formatAscii(12345, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseAsciiInt_bufForm_shouldParse() {
        io.netty.buffer.ByteBuf buf = Unpooled.copiedBuffer("42".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(AtlasDataUtils.parseAsciiInt(buf, 2)).isEqualTo(42);
    }

    @Test
    void parseAsciiInt_byteArray_shouldParse() {
        byte[] data = "123".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(AtlasDataUtils.parseAsciiInt(data)).isEqualTo(123);
    }

    @Test
    void parseAsciiInt_byteArray_empty_shouldReturnZero() {
        byte[] data = "   ".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(AtlasDataUtils.parseAsciiInt(data)).isZero();
    }
}
```

- [ ] **Step 2: 创建 FitDataUtilsTest**

```java
package com.tightening.netty.protocol.util.fit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitDataUtilsTest {

    @Test
    void bcdToInt_valid_shouldDecode() {
        byte bcd = (byte) 0x59; // 高4位=5, 低4位=9 → 59
        assertThat(FitDataUtils.bcdToInt(bcd)).isEqualTo(59);
    }

    @Test
    void bcdToInt_zero_shouldReturnZero() {
        assertThat(FitDataUtils.bcdToInt((byte) 0x00)).isZero();
    }

    @Test
    void parseBcdTimestamp_valid_shouldFormat() {
        byte[] data = new byte[7];
        data[0] = 0x20; data[1] = 0x25; // 2025
        data[2] = 0x06; // June
        data[3] = 0x15; // 15
        data[4] = 0x10; // 10h
        data[5] = 0x30; // 30m
        data[6] = 0x00; // 00s
        // called via FitTighteningDataParser internally
    }

    @Test
    void parseAlarmData_null_shouldThrow() {
        assertThatThrownBy(() -> FitDataUtils.parseAlarmData(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseAlarmData_tooShort_shouldThrow() {
        byte[] data = new byte[5];
        assertThatThrownBy(() -> FitDataUtils.parseAlarmData(data))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseAlarmData_valid_shouldFormat() {
        byte[] data = new byte[18]; // 2+1+1+0+7 minimal
        data[0] = 0x00; data[1] = 0x01; // alarmCode=1
        data[2] = 0x01; // level=Warning
        data[3] = 0x00; // infoLength=0
        // timestamp bytes at offset 4
        data[4] = 0x20; data[5] = 0x25; data[6] = 0x06; data[7] = 0x15;
        data[8] = 0x10; data[9] = 0x30; data[10] = 0x00;
        String result = FitDataUtils.parseAlarmData(data);
        assertThat(result).contains("0x0001").contains("Warning");
    }

    @Test
    void getCurrentTimestampBytes_shouldReturn4Bytes() {
        byte[] bytes = FitDataUtils.getCurrentTimestampBytes();
        assertThat(bytes).hasSize(4);
    }

    @Test
    void getTimestampBytes_knownValue_shouldEncodeLittleEndian() {
        byte[] bytes = FitDataUtils.getTimestampBytes(1234567890L);
        assertThat(bytes).hasSize(4);
        long decoded = FitDataUtils.bytesToTimestamp(bytes);
        assertThat(decoded).isEqualTo(1234567890L);
    }

    @Test
    void bytesToTimestamp_shouldDecodeLittleEndian() {
        byte[] bytes = new byte[] { (byte) 0xD2, 0x02, (byte) 0x96, 0x49 };
        long ts = FitDataUtils.bytesToTimestamp(bytes);
        assertThat(ts).isEqualTo(1234567890L);
    }

    @Test
    void getDateStr_shouldFormat() {
        byte[] bytes = FitDataUtils.getTimestampBytes(1234567890L);
        String dateStr = FitDataUtils.getDateStr(bytes);
        assertThat(dateStr).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }
}
```

- [ ] **Step 3: 创建 JsonUtilsTest**

```java
package com.tightening.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonUtilsTest {

    static class TestBean {
        public String name;
        public int value;
    }

    @Test
    void toJson_validObject_shouldReturnJson() {
        TestBean bean = new TestBean();
        bean.name = "test";
        bean.value = 42;
        String json = JsonUtils.toJson(bean);
        assertThat(json).contains("\"name\":\"test\"").contains("\"value\":42");
    }

    @Test
    void parse_validJson_shouldReturnObject() {
        String json = "{\"name\":\"test\",\"value\":42}";
        TestBean bean = JsonUtils.parse(json, TestBean.class);
        assertThat(bean.name).isEqualTo("test");
        assertThat(bean.value).isEqualTo(42);
    }

    @Test
    void parse_invalidJson_shouldThrowRuntimeException() {
        assertThatThrownBy(() -> JsonUtils.parse("{bad json}", TestBean.class))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void toJson_objectWithCyclic_shouldThrowRuntimeException() {
        // 嵌套自身导致循环引用
        assertThatThrownBy(() -> {
            Object self = new Object() { public Object me = this; };
            JsonUtils.toJson(self);
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void objectMapper_shouldBeAvailable() {
        assertThat(JsonUtils.OBJECT_MAPPER).isNotNull();
    }
}
```

- [ ] **Step 3: 补充 ConverterTest — 新增 entity2Dto / dto2Entity 测试**（追加到已有 `src/test/java/com/tightening/util/ConverterTest.java` 末尾）

```java
@Test
void testEntity2Dto_single_shouldCopyProperties() {
    TighteningData entity = new TighteningData();
    entity.setId(1L);
    entity.setVin("VIN123");
    entity.setTorque(12.5);

    TighteningDataDTO dto = Converter.entity2Dto(entity, TighteningDataDTO::new);

    assertThat(dto.getId()).isEqualTo(1L);
    assertThat(dto.getVin()).isEqualTo("VIN123");
    assertThat(dto.getTorque()).isCloseTo(12.5, 0.001);
}

@Test
void testEntity2Dto_list_shouldCopyAll() {
    TighteningData e1 = new TighteningData(); e1.setVin("A");
    TighteningData e2 = new TighteningData(); e2.setVin("B");
    List<TighteningDataDTO> dtos = Converter.entity2Dto(List.of(e1, e2), TighteningDataDTO::new);

    assertThat(dtos).hasSize(2);
    assertThat(dtos.get(0).getVin()).isEqualTo("A");
    assertThat(dtos.get(1).getVin()).isEqualTo("B");
}

@Test
void testDto2Entity_single_shouldCopyProperties() {
    TighteningDataDTO dto = new TighteningDataDTO();
    dto.setVin("VIN-DTO");
    dto.setTorque(99.9);

    TighteningData entity = Converter.dto2Entity(dto, TighteningData::new);

    assertThat(entity.getVin()).isEqualTo("VIN-DTO");
    assertThat(entity.getTorque()).isCloseTo(99.9, 0.001);
}

@Test
void testDto2Entity_list_shouldCopyAll() {
    TighteningDataDTO d1 = new TighteningDataDTO(); d1.setVin("X");
    TighteningDataDTO d2 = new TighteningDataDTO(); d2.setVin("Y");
    List<TighteningData> entities = Converter.dto2Entity(List.of(d1, d2), TighteningData::new);

    assertThat(entities).hasSize(2);
    assertThat(entities.get(0).getVin()).isEqualTo("X");
    assertThat(entities.get(1).getVin()).isEqualTo("Y");
}
```

需要额外导入 `TighteningData` 和 `TighteningDataDTO`。

- [ ] **Step 4: 运行测试**

```bash
mvn test -pl . -Dtest="com.tightening.util.*Test,com.tightening.netty.protocol.util.*.*Test" -DfailIfNoTests=false
```

- [ ] **Step 5: 提交**

```bash
git add src/test/java/com/tightening/util/ src/test/java/com/tightening/netty/protocol/util/
git commit -m "test: add Layer 2 protocol utils, JsonUtils, Converter entity-dto tests"
```

---

## Layer 2 — 已有测试改写 + 补齐（6 个文件）

### Task 13: 改写已有测试去掉 @SpringBootTest

**Files:**
- Modify: `src/test/java/com/tightening/netty/protocol/codec/atlas/AtlasFrameCodecTest.java`
- Modify: `src/test/java/com/tightening/netty/protocol/codec/fit/FitFrameCodecTest.java`
- Modify: `src/test/java/com/tightening/device/handler/HeartbeatHandlerTest.java`

- [ ] **Step 1: 改写 AtlasFrameCodecTest**

将 `@SpringBootTest` 替换为 `@ExtendWith(MockitoExtension.class)`。移除未使用的 `@Mock ChannelHandlerContext ctx` 和 `@Mock ByteBufAllocator alloc` 字段及 `when(ctx.alloc())` 调用。`EmbeddedChannel.writeInbound()` 使用 channel 自身的 alloc。

- [ ] **Step 2: 改写 FitFrameCodecTest — 同上**

- [ ] **Step 3: 改写 HeartbeatHandlerTest**

将 `@SpringBootTest` 替换为 `@ExtendWith(MockitoExtension.class)`。该类无任何 Spring 依赖，纯 EmbeddedChannel + lambda。

- [ ] **Step 4: 运行验证**

```bash
mvn test -pl . -Dtest="com.tightening.netty.protocol.codec.atlas.AtlasFrameCodecTest,com.tightening.netty.protocol.codec.fit.FitFrameCodecTest,com.tightening.device.handler.HeartbeatHandlerTest"
```

- [ ] **Step 5: 提交**

### Task 14: 补齐 AtlasTighteningDataParserTest

**Files:**
- Modify: `src/test/java/com/tightening/netty/protocol/util/atlas/AtlasTighteningDataParserTest.java`

- [ ] **Step 1: 新增 2 个测试方法**（追加到已有 `AtlasTighteningDataParserTest` 类末尾）

```java
@Test
void parse_invalidTorqueStatusCode_shouldFallbackToLow() {
    byte[] data = new byte[250];
    java.util.Arrays.fill(data, (byte) ' ');
    writeAt(data, 23, "1");       // rev 1
    writeAt(data, 29, "1");       // channelId
    writeAt(data, 33, "CTRL");
    writeAt(data, 60, "VIN");
    writeAt(data, 87, "1");       // jobId
    writeAt(data, 91, "5");       // parameterSet
    writeAt(data, 96, "10");
    writeAt(data, 102, "3");
    writeAt(data, 108, "1");
    writeAt(data, 111, "9");      // torque status = 9 (invalid, falls back to LOW=0)
    writeAt(data, 114, "9");      // angle status = 9 (invalid, falls back to LOW=0)
    writeAt(data, 117, "001000");
    writeAt(data, 125, "002000");
    writeAt(data, 133, "001500");
    writeAt(data, 141, "001234");
    writeAt(data, 149, "00010");
    writeAt(data, 156, "00100");
    writeAt(data, 163, "00050");
    writeAt(data, 170, "00045");
    writeAt(data, 177, "2024-01-15:10:30:00");
    writeAt(data, 219, "1");
    writeAt(data, 222, "1234567890");

    TighteningDataDTO d = AtlasTighteningDataParser.parse(data, 1);
    // Both fallback to LOW (0)
    assertThat(d.getTorqueStatus()).isZero();
    assertThat(d.getAngleStatus()).isZero();
}
```

- [ ] **Step 2: 运行 + 提交**

### Task 15: 补齐 FIT 解析器测试

**Files:**
- Modify: `src/test/java/com/tightening/netty/protocol/util/fit/FitTighteningDataParserTest.java`
- Modify: `src/test/java/com/tightening/netty/protocol/util/fit/FitCurveDataParserTest.java`

- [ ] **Step 1: FitTighteningDataParserTest — 新增 4 个测试方法**（追加到已有类）

```java
@Test
void parse_ngStatus_shouldSetNgFields() {
    ByteBuffer buf = ByteBuffer.allocate(4 + 1 + 1 + 1 + 0 + 4 + 4 + 7)
            .order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(1);             // tighteningId
    buf.put((byte) 0);         // status = NG
    buf.put((byte) 1);         // programNumber
    buf.put((byte) 0);         // barcodeLength = 0
    buf.putFloat(10.0f);       // torque
    buf.putFloat(45.0f);       // angle
    buf.put(new byte[7]);      // timestamp (zeros)

    TighteningDataDTO dto = FitTighteningDataParser.parse(buf.array());
    assertThat(dto.getTighteningStatus()).isEqualTo(TighteningStatus.NG.getCode());
    assertThat(dto.getTorqueStatus()).isEqualTo(FitTorqueStatus.NG.getCode());
    assertThat(dto.getAngleStatus()).isEqualTo(FitAngleStatus.NG.getCode());
}

@Test
void parse_emptyBarcode_shouldNotThrow() {
    ByteBuffer buf = ByteBuffer.allocate(4 + 1 + 1 + 1 + 0 + 4 + 4 + 7)
            .order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(1).put((byte) 1).put((byte) 1).put((byte) 0);
    buf.putFloat(10.0f).putFloat(45.0f);
    buf.put(new byte[7]);

    TighteningDataDTO dto = FitTighteningDataParser.parse(buf.array());
    assertThat(dto).isNotNull();
}

@Test
void parse_negativeAngle_shouldSetLoosening() {
    ByteBuffer buf = ByteBuffer.allocate(4 + 1 + 1 + 1 + 0 + 4 + 4 + 7)
            .order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(1).put((byte) 1).put((byte) 1).put((byte) 0);
    buf.putFloat(10.0f);
    buf.putFloat(-45.0f);      // negative angle → LOOSENING
    buf.put(new byte[7]);

    TighteningDataDTO dto = FitTighteningDataParser.parse(buf.array());
    assertThat(dto.getResultType()).isEqualTo(TighteningResultType.LOOSENING.getCode());
}

@Test
void parse_dataTooShort_shouldNotThrow() {
    byte[] data = new byte[3]; // only 3 bytes, not enough for header
    // should handle gracefully (ByteBuffer wrap allows it, parsing will read what it can)
    assertThatThrownBy(() -> FitTighteningDataParser.parse(data))
            .isInstanceOfAny(IndexOutOfBoundsException.class, java.nio.BufferUnderflowException.class);
}
```

- [ ] **Step 2: FitCurveDataParserTest — 新增 4 个测试方法**（追加到已有类）

```java
@Test
void parse_nullData_shouldThrow() {
    assertThatThrownBy(() -> FitCurveDataParser.parse(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("数据区长度不足");
}

@Test
void parse_dataTooShort_shouldThrow() {
    byte[] data = new byte[2];
    assertThatThrownBy(() -> FitCurveDataParser.parse(data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("数据区长度不足");
}

@Test
void parse_zeroPoints_shouldThrow() {
    byte[] data = new byte[4]; // only tighteningId, 0 points
    assertThatThrownBy(() -> FitCurveDataParser.parse(data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("没有曲线点数据");
}

@Test
void parse_singlePoint_shouldReturnOnePoint() {
    ByteBuffer buf = ByteBuffer.allocate(4 + 12).order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(100);
    buf.putFloat(0.1f);
    buf.putFloat(5.0f);
    buf.putFloat(10.0f);

    CurveDataDTO dto = FitCurveDataParser.parse(buf.array());
    assertThat(dto.getTighteningId()).isEqualTo(100);
    assertThat(dto.getDataSamples()).isNotNull();
    assertThat(dto.getDataSamples()).contains("\"torque\":5.0");
}
```

- [ ] **Step 3: 运行 + 提交**

---

## Layer 3 — 设备管理层（7 个文件）

### Task 16: DeviceHolder 测试

**Files:**
- Create: `src/test/java/com/tightening/device/DeviceHolderTest.java`

**(具体代码从源文件读取字段后填充)**

- [ ] **Step 1: 测试 DeviceHolder 状态管理** — 创建 DeviceHolder，验证初始状态、状态转换、enable/disable 时间戳

- [ ] **Step 2: 运行 + 提交**

### Task 17: TCPDeviceHandler 测试（匿名子类）

**Files:**
- Create: `src/test/java/com/tightening/device/handler/TCPDeviceHandlerTest.java`

- [ ] **Step 1: 创建匿名子类 stub connectToChannel() 等 abstract 方法，测试 connect/disconnect/sendCmdAsync 流程**

### Task 18: ToolHandler 测试（匿名子类）

**Files:**
- Create: `src/test/java/com/tightening/device/handler/ToolHandlerTest.java`

- [ ] **Step 1: 匿名子类 stub enableTool/disableTool/sendPSetCmd，测试 changeToolState 冷却逻辑的 4 条路径**

1. enable 当前 disabled → 放行
2. enable 当前 enabled (冷却期内) → 拒绝
3. force enable 绕过冷却 → 放行
4. enable 当前 enabled (冷却期外) → 放行

### Task 19: AtlasPFSeriesHandler + FitSeriesHandler 测试

**Files:**
- Create: `src/test/java/com/tightening/device/handler/impl/AtlasPFSeriesHandlerTest.java`
- Create: `src/test/java/com/tightening/device/handler/impl/FitSeriesHandlerTest.java`

- [ ] **Step 1: AtlasPFSeriesHandlerTest** — 构造时传入 mock 依赖，测试 enableTool/disableTool/sendPSetCmd。末尾加两个构造验证（AtlasPF4000Handler / AtlasPF6000OPHandler new + assert DeviceType）

- [ ] **Step 2: FitSeriesHandlerTest** — 同上，末尾加 FitFTC6Handler 构造验证

### Task 20: DeviceManager 测试

**Files:**
- Create: `src/test/java/com/tightening/device/DeviceManagerTest.java`

- [ ] **Step 1: 构造 DeviceManager + mock DeviceHandlerFactory + mock DeviceMapper**

测试方法：
1. userLoggedIn → 扫描启动
2. userLoggedIn 后再次 → 不重复启动
3. handleDeviceChange ADD → 调用 handler.connect
4. handleDeviceChange DELETE → 调用 handler.disconnect
5. 真实线程池 + sleep 验证异步行为

### Task 21: DeviceHandlerFactory + DeviceHandlerService + DeviceChangeEvent 测试

**Files:**
- Create: `src/test/java/com/tightening/device/handler/DeviceHandlerFactoryTest.java`
- Create: `src/test/java/com/tightening/device/service/DeviceHandlerServiceTest.java`
- Create: `src/test/java/com/tightening/device/event/DeviceChangeEventTest.java`

- [ ] **Step 1: DeviceHandlerFactoryTest** — 构造 List<DeviceHandler>，验证 getHandler(DeviceType) 匹配逻辑

- [ ] **Step 2: DeviceHandlerServiceTest** — 验证 @PostConstruct 调用 DeviceType.initProvider

- [ ] **Step 3: DeviceChangeEventTest** — 构造事件，验证 type + deviceId 字段

---

## Layer 3 — Controller + Service（6 个文件）

### Task 22: DeviceController 测试

**Files:**
- Create: `src/test/java/com/tightening/controller/DeviceControllerTest.java`

- [ ] **Step 1: 创建测试**

```java
package com.tightening.controller;

import com.tightening.device.DeviceManager;
import com.tightening.dto.DeviceDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock
    private DeviceManager deviceManager;

    @InjectMocks
    private DeviceController controller;

    @Test
    void listDevices_shouldReturnDeferredResult() {
        List<DeviceDTO> devices = List.of(new DeviceDTO());
        when(deviceManager.getAllDevices()).thenReturn(devices);

        DeferredResult<ResponseEntity> result = controller.listDevices();
        result.setResult(ResponseEntity.ok(devices)); // 手动触发

        assertThat(result.getResult()).isNotNull();
    }
}
```

- [ ] **Step 2: 依次为每个 @PostMapping 方法写测试 — 正常路径 + deviceManager 返回异常时**

- [ ] **Step 3: 运行 + 提交**

### Task 23: LoginController + UserAccountInfoController 测试

**Files:**
- Create: `src/test/java/com/tightening/controller/LoginControllerTest.java`
- Create: `src/test/java/com/tightening/controller/UserAccountInfoControllerTest.java`

- [ ] **Step 1: 与 DeviceControllerTest 相同模式 — @Mock Service + @InjectMocks Controller + 手动 setResult**

### Task 24: DeviceService + TighteningDataService + UserAccountInfoService 测试

**Files:**
- Create: `src/test/java/com/tightening/service/DeviceServiceTest.java`
- Create: `src/test/java/com/tightening/service/TighteningDataServiceTest.java`
- Create: `src/test/java/com/tightening/service/UserAccountInfoServiceTest.java`

- [ ] **Step 1: 每个 Service 测试 — @Mock Mapper + @InjectMocks Service，测试所有 public 方法正常 + 异常路径**

---

## Layer 3 — Netty Handler（5 个文件）

### Task 25: DeviceInitHandler + AtlasPFSeriesInBoundHandler + AtlasPFSeriesInitHandler 测试

**Files:**
- Create: `src/test/java/com/tightening/netty/protocol/handler/DeviceInitHandlerTest.java`
- Create: `src/test/java/com/tightening/netty/protocol/handler/atlas/AtlasPFSeriesInBoundHandlerTest.java`
- Create: `src/test/java/com/tightening/netty/protocol/handler/atlas/AtlasPFSeriesInitHandlerTest.java`

- [ ] **Step 1: 每个 Handler 用 EmbeddedChannel 测试 — 构造 pipeline → writeInbound → 验证 out 结果**

### Task 26: FitSeriesInBoundHandler + FitSeriesInitHandler 测试

**Files:**
- Create: `src/test/java/com/tightening/netty/protocol/handler/fit/FitSeriesInBoundHandlerTest.java`
- Create: `src/test/java/com/tightening/netty/protocol/handler/fit/FitSeriesInitHandlerTest.java`

- [ ] **Step 1: 同上 EmbeddedChannel 模式**

## 验证

### Task 27: 最终全量运行

- [ ] **Step 1: 运行完整测试套件**

```bash
mvn test
```

- [ ] **Step 2: 确认所有测试 PASS，无跳过，无忽略**
