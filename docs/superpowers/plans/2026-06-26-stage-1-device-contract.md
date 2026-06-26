# Stage 1: Device Contract + JudgmentStrategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `ITool`/`IDevice` interfaces with `ToolAdapter` and `DeviceRegistry`, plus `JudgmentStrategy` with Atlas/FIT implementations.

**Architecture:** Pure interfaces in `device.contract` package, event-driven `DeviceRegistry` (zero coupling with `DeviceManager`), adapter pattern for ToolHandler→ITool, strategy pattern for OK/NG judgment.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Mockito, Spring Boot Test

## Global Constraints

- 新建 10 个文件，修改 1 个文件（`ToolHandler.java` 加 setToolAdapter 字段和 setter）
- `ITool` 命名用业务语义（sendLock/sendUnlock 对应 enableToolOp/disableToolOp）
- `CancellationToken` 不在 stage 1 引入
- `IPreconditionCheckable` 不加入
- DeviceRegistry 通过 `@TransactionalEventListener` 监听 `DeviceChangeEvent`（与 DeviceManager 一致，仅在事务成功后注册）
- JudgmentStrategy 入参 `TighteningDataDTO`
- Atlas 判定：tighteningStatus==TighteningStatus.OK(1) && torqueStatus==AtlasTorqueStatus.OK(1) && angleStatus==AtlasAngleStatus.OK(1)
- FIT 判定：tighteningStatus==TighteningStatus.OK(1)
- 实现中使用项目已有的枚举常量（`TighteningStatus.OK.getCode()` 等），禁止裸数字
- SudongJudgment 暂不实现，接口已预留

---

### Task 1: IDevice + ITool 接口

**Files:**
- Create: `src/main/java/com/tightening/device/contract/IDevice.java`
- Create: `src/main/java/com/tightening/device/contract/ITool.java`

**Interfaces:**
- Consumes: `com.tightening.constant.DeviceType`, `com.tightening.dto.TighteningDataDTO`, `com.tightening.dto.CurveDataDTO`
- Produces: `IDevice` (id, type, isConnected), `ITool extends IDevice` (sendLock, sendUnlock, sendPSet, onTighteningData, onCurveData)

- [ ] **Step 1: 创建 IDevice 接口**

```java
package com.tightening.device.contract;

import com.tightening.constant.DeviceType;

public interface IDevice {
    Long id();
    DeviceType type();
    boolean isConnected();
}
```

- [ ] **Step 2: 创建 ITool 接口**

```java
package com.tightening.device.contract;

import com.tightening.dto.CurveDataDTO;
import com.tightening.dto.TighteningDataDTO;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ITool extends IDevice {
    CompletableFuture<Boolean> sendLock();

    CompletableFuture<Boolean> sendUnlock();

    CompletableFuture<Boolean> sendPSet(int psetId);

    void onTighteningData(Consumer<TighteningDataDTO> callback);

    void onCurveData(Consumer<CurveDataDTO> callback);
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/device/contract/IDevice.java src/main/java/com/tightening/device/contract/ITool.java
git commit -m "feat: add IDevice and ITool device contract interfaces"
```

---

### Task 2: JudgmentStrategy + JudgmentResult

**Files:**
- Create: `src/main/java/com/tightening/judgment/JudgmentStrategy.java`
- Create: `src/main/java/com/tightening/judgment/JudgmentResult.java`

**Interfaces:**
- Consumes: `com.tightening.dto.TighteningDataDTO`
- Produces: `JudgmentStrategy.judge(TighteningDataDTO) -> JudgmentResult`, `JudgmentResult(boolean isOk, String reason)` with static factories `ok()` / `ng(String)`

- [ ] **Step 1: 创建 JudgmentResult record**

```java
package com.tightening.judgment;

public record JudgmentResult(boolean isOk, String reason) {
    public static JudgmentResult ok() {
        return new JudgmentResult(true, "OK");
    }

    public static JudgmentResult ng(String reason) {
        return new JudgmentResult(false, reason);
    }
}
```

- [ ] **Step 2: 创建 JudgmentStrategy 接口**

```java
package com.tightening.judgment;

import com.tightening.dto.TighteningDataDTO;

public interface JudgmentStrategy {
    JudgmentResult judge(TighteningDataDTO dto);
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/tightening/judgment/JudgmentStrategy.java src/main/java/com/tightening/judgment/JudgmentResult.java
git commit -m "feat: add JudgmentStrategy interface and JudgmentResult record"
```

---

### Task 3: AtlasJudgment

**Files:**
- Create: `src/test/java/com/tightening/judgment/AtlasJudgmentTest.java`
- Create: `src/main/java/com/tightening/judgment/AtlasJudgment.java`

**Interfaces:**
- Consumes: `JudgmentStrategy`, `JudgmentResult`, `TighteningDataDTO`（tighteningStatus, torqueStatus, angleStatus）
- Produces: `AtlasJudgment implements JudgmentStrategy`

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.judgment;

import com.tightening.constant.AtlasAngleStatus;
import com.tightening.constant.AtlasTorqueStatus;
import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AtlasJudgment 判定策略")
class AtlasJudgmentTest {

    private final AtlasJudgment judgment = new AtlasJudgment();

    @Test
    @DisplayName("三个状态全 OK → isOk=true")
    void allOk() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.OK.getCode());
        dto.setAngleStatus(AtlasAngleStatus.OK.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isTrue();
        assertThat(result.reason()).isEqualTo("OK");
    }

    @Test
    @DisplayName("tighteningStatus NG → isOk=false")
    void tighteningNg() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.NG.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.OK.getCode());
        dto.setAngleStatus(AtlasAngleStatus.OK.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }

    @Test
    @DisplayName("torqueStatus LOW → isOk=false")
    void torqueLow() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.LOW.getCode());
        dto.setAngleStatus(AtlasAngleStatus.OK.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }

    @Test
    @DisplayName("torqueStatus HIGH → isOk=false")
    void torqueHigh() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.HIGH.getCode());
        dto.setAngleStatus(AtlasAngleStatus.OK.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }

    @Test
    @DisplayName("angleStatus LOW → isOk=false")
    void angleLow() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.OK.getCode());
        dto.setAngleStatus(AtlasAngleStatus.LOW.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }

    @Test
    @DisplayName("angleStatus HIGH → isOk=false")
    void angleHigh() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.OK.getCode());
        dto.setAngleStatus(AtlasAngleStatus.HIGH.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=AtlasJudgmentTest -DfailIfNoTests=false -q
```
Expected: 编译失败 "cannot find symbol: class AtlasJudgment"

- [ ] **Step 3: 实现 AtlasJudgment**

```java
package com.tightening.judgment;

import com.tightening.constant.AtlasAngleStatus;
import com.tightening.constant.AtlasTorqueStatus;
import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;

public class AtlasJudgment implements JudgmentStrategy {

    @Override
    public JudgmentResult judge(TighteningDataDTO dto) {
        if (dto.getTighteningStatus() != TighteningStatus.OK.getCode()) {
            return JudgmentResult.ng("tighteningStatus is NG");
        }
        if (dto.getTorqueStatus() != AtlasTorqueStatus.OK.getCode()) {
            return JudgmentResult.ng("torqueStatus is LOW or HIGH");
        }
        if (dto.getAngleStatus() != AtlasAngleStatus.OK.getCode()) {
            return JudgmentResult.ng("angleStatus is LOW or HIGH");
        }
        return JudgmentResult.ok();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest=AtlasJudgmentTest -DfailIfNoTests=false
```
Expected: Tests run: 6, Failures: 0

- [ ] **Step 5: 提交**

```bash
git add src/test/java/com/tightening/judgment/AtlasJudgmentTest.java src/main/java/com/tightening/judgment/AtlasJudgment.java
git commit -m "feat: add AtlasJudgment with three-status check using Atlas enums"
```

---

### Task 4: FitJudgment

**Files:**
- Create: `src/test/java/com/tightening/judgment/FitJudgmentTest.java`
- Create: `src/main/java/com/tightening/judgment/FitJudgment.java`

**Interfaces:**
- Consumes: `JudgmentStrategy`, `JudgmentResult`, `TighteningDataDTO`（tighteningStatus only）
- Produces: `FitJudgment implements JudgmentStrategy`

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.judgment;

import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FitJudgment 判定策略")
class FitJudgmentTest {

    private final FitJudgment judgment = new FitJudgment();

    @Test
    @DisplayName("tighteningStatus OK → isOk=true")
    void tighteningOk() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isTrue();
        assertThat(result.reason()).isEqualTo("OK");
    }

    @Test
    @DisplayName("tighteningStatus NG → isOk=false")
    void tighteningNg() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.NG.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=FitJudgmentTest -DfailIfNoTests=false -q
```
Expected: 编译失败 "cannot find symbol: class FitJudgment"

- [ ] **Step 3: 实现 FitJudgment**

```java
package com.tightening.judgment;

import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;

public class FitJudgment implements JudgmentStrategy {

    @Override
    public JudgmentResult judge(TighteningDataDTO dto) {
        if (dto.getTighteningStatus() != TighteningStatus.OK.getCode()) {
            return JudgmentResult.ng("tighteningStatus is NG");
        }
        return JudgmentResult.ok();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest=FitJudgmentTest -DfailIfNoTests=false
```
Expected: Tests run: 2, Failures: 0

- [ ] **Step 5: 提交**

```bash
git add src/test/java/com/tightening/judgment/FitJudgmentTest.java src/main/java/com/tightening/judgment/FitJudgment.java
git commit -m "feat: add FitJudgment with tighteningStatus-only check"
```

---

### Task 5: JudgmentConfig

**Files:**
- Create: `src/test/java/com/tightening/config/JudgmentConfigTest.java`
- Create: `src/main/java/com/tightening/config/JudgmentConfig.java`

**Interfaces:**
- Consumes: `JudgmentStrategy`, `AtlasJudgment`, `FitJudgment`, `DeviceType`
- Produces: Spring Bean `Map<DeviceType, JudgmentStrategy>`，注册 Atlas 和 FIT 策略

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.config;

import com.tightening.constant.DeviceType;
import com.tightening.judgment.AtlasJudgment;
import com.tightening.judgment.FitJudgment;
import com.tightening.judgment.JudgmentStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JudgmentConfigTest.TestConfig.class)
@DisplayName("JudgmentConfig 策略注册")
class JudgmentConfigTest {

    @Configuration
    static class TestConfig {
        @Bean
        public Map<DeviceType, JudgmentStrategy> judgmentStrategies() {
            return JudgmentConfig.createStrategies();
        }
    }

    @Autowired
    private Map<DeviceType, JudgmentStrategy> strategies;

    @Test
    @DisplayName("ATLAS_PF4000 对应 AtlasJudgment")
    void atlasPf4000() {
        assertThat(strategies.get(DeviceType.ATLAS_PF4000))
                .isInstanceOf(AtlasJudgment.class);
    }

    @Test
    @DisplayName("ATLAS_PF6000_OP 对应 AtlasJudgment")
    void atlasPf6000() {
        assertThat(strategies.get(DeviceType.ATLAS_PF6000_OP))
                .isInstanceOf(AtlasJudgment.class);
    }

    @Test
    @DisplayName("FIT_FTC6 对应 FitJudgment")
    void fitFtc6() {
        assertThat(strategies.get(DeviceType.FIT_FTC6))
                .isInstanceOf(FitJudgment.class);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=JudgmentConfigTest -DfailIfNoTests=false -q
```
Expected: 编译失败 "cannot find symbol: variable JudgmentConfig"

- [ ] **Step 3: 实现 JudgmentConfig**

```java
package com.tightening.config;

import com.tightening.constant.DeviceType;
import com.tightening.judgment.AtlasJudgment;
import com.tightening.judgment.FitJudgment;
import com.tightening.judgment.JudgmentStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class JudgmentConfig {

    @Bean
    public Map<DeviceType, JudgmentStrategy> judgmentStrategies() {
        return createStrategies();
    }

    static Map<DeviceType, JudgmentStrategy> createStrategies() {
        return Map.of(
                DeviceType.ATLAS_PF4000, new AtlasJudgment(),
                DeviceType.ATLAS_PF6000_OP, new AtlasJudgment(),
                DeviceType.FIT_FTC6, new FitJudgment()
        );
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest=JudgmentConfigTest -DfailIfNoTests=false
```
Expected: Tests run: 3, Failures: 0

- [ ] **Step 5: 提交**

```bash
git add src/test/java/com/tightening/config/JudgmentConfigTest.java src/main/java/com/tightening/config/JudgmentConfig.java
git commit -m "feat: add JudgmentConfig with DeviceType-to-JudgmentStrategy mapping"
```

---

### Task 6: ToolAdapter

**Files:**
- Create: `src/test/java/com/tightening/device/contract/ToolAdapterTest.java`
- Create: `src/main/java/com/tightening/device/contract/ToolAdapter.java`

**Interfaces:**
- Consumes: `ITool`, `ToolHandler`, `Device`, `DeviceType`, `DeviceStatus`, `TighteningDataDTO`, `CurveDataDTO`
- Produces: `ToolAdapter implements ITool` — 委托 ToolHandler，维护 Consumer 列表

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.device.contract;

import com.tightening.constant.DeviceType;
import com.tightening.constant.DeviceStatus;
import com.tightening.device.handler.ToolHandler;
import com.tightening.dto.CurveDataDTO;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolAdapter 将 ToolHandler 适配为 ITool")
class ToolAdapterTest {

    @Mock
    private ToolHandler handler;

    private Device device;
    private ToolAdapter adapter;

    @BeforeEach
    void setUp() {
        device = new Device();
        device.setId(1L);
        device.setType(DeviceType.ATLAS_PF4000.getId());
        adapter = new ToolAdapter(handler, device);
    }

    @Test
    @DisplayName("id() 返回 device.getId()")
    void idDelegates() {
        assertThat(adapter.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("type() 返回 DeviceType")
    void typeDelegates() {
        assertThat(adapter.type()).isEqualTo(DeviceType.ATLAS_PF4000);
    }

    @Test
    @DisplayName("isConnected() 当 status=CONNECTED 返回 true")
    void isConnectedTrue() {
        when(handler.getStatus(1L)).thenReturn(DeviceStatus.CONNECTED);
        assertThat(adapter.isConnected()).isTrue();
    }

    @Test
    @DisplayName("isConnected() 当 status=DISCONNECTED 返回 false")
    void isConnectedFalse() {
        when(handler.getStatus(1L)).thenReturn(DeviceStatus.DISCONNECTED);
        assertThat(adapter.isConnected()).isFalse();
    }

    @Test
    @DisplayName("sendLock() 委托 enableToolOp")
    void sendLockDelegates() {
        when(handler.enableToolOp(1L)).thenReturn(CompletableFuture.completedFuture(true));
        assertThat(adapter.sendLock()).isCompletedWithValue(true);
    }

    @Test
    @DisplayName("sendUnlock() 委托 disableToolOp")
    void sendUnlockDelegates() {
        when(handler.disableToolOp(1L)).thenReturn(CompletableFuture.completedFuture(false));
        assertThat(adapter.sendUnlock()).isCompletedWithValue(false);
    }

    @Test
    @DisplayName("sendPSet() 委托 sendPSetOp")
    void sendPSetDelegates() {
        when(handler.sendPSetOp(1L, 5)).thenReturn(CompletableFuture.completedFuture(true));
        assertThat(adapter.sendPSet(5)).isCompletedWithValue(true);
    }

    @Test
    @DisplayName("onTighteningData() 注册 Consumer，fireTighteningData() 通知所有 Consumer")
    void tighteningDataCallback() {
        AtomicInteger count = new AtomicInteger(0);
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(1);

        adapter.onTighteningData(d -> count.incrementAndGet());
        adapter.fireTighteningData(dto);

        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("onCurveData() 注册 Consumer，fireCurveData() 通知所有 Consumer")
    void curveDataCallback() {
        AtomicInteger count = new AtomicInteger(0);
        CurveDataDTO dto = new CurveDataDTO();

        adapter.onCurveData(d -> count.incrementAndGet());
        adapter.fireCurveData(dto);

        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("多个 Consumer 全部被通知")
    void multipleConsumers() {
        AtomicInteger c1 = new AtomicInteger(0);
        AtomicInteger c2 = new AtomicInteger(0);
        TighteningDataDTO dto = new TighteningDataDTO();

        adapter.onTighteningData(d -> c1.incrementAndGet());
        adapter.onTighteningData(d -> c2.incrementAndGet());
        adapter.fireTighteningData(dto);

        assertThat(c1.get()).isEqualTo(1);
        assertThat(c2.get()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=ToolAdapterTest -DfailIfNoTests=false -q
```
Expected: 编译失败 "cannot find symbol: class ToolAdapter"

- [ ] **Step 3: 实现 ToolAdapter**

```java
package com.tightening.device.contract;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.handler.ToolHandler;
import com.tightening.dto.CurveDataDTO;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.Device;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ToolAdapter implements ITool {

    private final ToolHandler handler;
    private final Device device;
    private final long deviceId;
    private final List<Consumer<TighteningDataDTO>> tighteningDataListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<CurveDataDTO>> curveDataListeners = new CopyOnWriteArrayList<>();

    public ToolAdapter(ToolHandler handler, Device device) {
        this.handler = handler;
        this.device = device;
        this.deviceId = device.getId();
    }

    @Override
    public Long id() {
        return deviceId;
    }

    @Override
    public DeviceType type() {
        return DeviceType.getType(device.getType());
    }

    @Override
    public boolean isConnected() {
        return handler.getStatus(deviceId) == DeviceStatus.CONNECTED;
    }

    @Override
    public CompletableFuture<Boolean> sendLock() {
        return handler.enableToolOp(deviceId);
    }

    @Override
    public CompletableFuture<Boolean> sendUnlock() {
        return handler.disableToolOp(deviceId);
    }

    @Override
    public CompletableFuture<Boolean> sendPSet(int psetId) {
        return handler.sendPSetOp(deviceId, psetId);
    }

    @Override
    public void onTighteningData(Consumer<TighteningDataDTO> callback) {
        tighteningDataListeners.add(callback);
    }

    @Override
    public void onCurveData(Consumer<CurveDataDTO> callback) {
        curveDataListeners.add(callback);
    }

    public void fireTighteningData(TighteningDataDTO dto) {
        tighteningDataListeners.forEach(l -> l.accept(dto));
    }

    public void fireCurveData(CurveDataDTO dto) {
        curveDataListeners.forEach(l -> l.accept(dto));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest=ToolAdapterTest -DfailIfNoTests=false
```
Expected: Tests run: 9, Failures: 0

- [ ] **Step 5: 提交**

```bash
git add src/test/java/com/tightening/device/contract/ToolAdapterTest.java src/main/java/com/tightening/device/contract/ToolAdapter.java
git commit -m "feat: add ToolAdapter adapting ToolHandler to ITool interface"
```

---

### Task 7: DeviceRegistry

**Files:**
- Create: `src/test/java/com/tightening/device/DeviceRegistryTest.java`
- Create: `src/main/java/com/tightening/device/DeviceRegistry.java`

**Interfaces:**
- Consumes: `ITool`, `ToolAdapter`, `DeviceHandlerFactory`, `DeviceHandler`, `ToolHandler`, `DeviceChangeEvent`, `DeviceChangeType`, `Device`
- Produces: `DeviceRegistry` @Component — getTool, getAllTools, @EventListener onDeviceChange

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.device;

import com.tightening.constant.DeviceChangeType;
import com.tightening.constant.DeviceType;
import com.tightening.device.contract.ITool;
import com.tightening.device.event.DeviceChangeEvent;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.DeviceHandlerFactory;
import com.tightening.device.handler.ToolHandler;
import com.tightening.entity.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceRegistry 事件驱动 ITool 注册表")
class DeviceRegistryTest {

    @Mock
    private DeviceHandlerFactory handlerFactory;

    @Mock
    private ToolHandler toolHandler;

    private DeviceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DeviceRegistry(handlerFactory);
    }

    @Test
    @DisplayName("getTool() 未注册设备返回 null")
    void getToolReturnsNull() {
        assertThat(registry.getTool(999L)).isNull();
    }

    @Test
    @DisplayName("DeviceChangeEvent ADD 注册 ToolAdapter → getTool() 返回 ITool")
    void addEventRegistersTool() {
        Device device = new Device();
        device.setId(1L);
        device.setType(DeviceType.ATLAS_PF4000.getId());
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(toolHandler);

        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, device));

        ITool tool = registry.getTool(1L);
        assertThat(tool).isNotNull();
        assertThat(tool.id()).isEqualTo(1L);
        assertThat(tool.type()).isEqualTo(DeviceType.ATLAS_PF4000);
    }

    @Test
    @DisplayName("DeviceChangeEvent DELETE 移除 ITool")
    void deleteEventRemovesTool() {
        Device device = new Device();
        device.setId(1L);
        device.setType(DeviceType.ATLAS_PF4000.getId());
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(toolHandler);
        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, device));

        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.DELETE, 1L));

        assertThat(registry.getTool(1L)).isNull();
    }

    @Test
    @DisplayName("DeviceChangeEvent UPDATE 重建 ToolAdapter（先 remove 再 register）")
    void updateEventRecreatesTool() {
        Device device = new Device();
        device.setId(1L);
        device.setType(DeviceType.ATLAS_PF4000.getId());
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(toolHandler);
        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, device));

        // UPDATE：设备类型变更（如 ATLAS_PF4000 → ATLAS_PF6000_OP）
        Device updatedDevice = new Device();
        updatedDevice.setId(1L);
        updatedDevice.setType(DeviceType.ATLAS_PF6000_OP.getId());
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF6000_OP)).thenReturn(toolHandler);
        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.UPDATE, updatedDevice));

        ITool tool = registry.getTool(1L);
        assertThat(tool).isNotNull();
        assertThat(tool.type()).isEqualTo(DeviceType.ATLAS_PF6000_OP);
    }

    @Test
    @DisplayName("getAllTools() 返回全部已注册工具")
    void getAllTools() {
        Device d1 = new Device();
        d1.setId(1L);
        d1.setType(DeviceType.ATLAS_PF4000.getId());
        Device d2 = new Device();
        d2.setId(2L);
        d2.setType(DeviceType.FIT_FTC6.getId());
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(toolHandler);
        when(handlerFactory.getHandler(DeviceType.FIT_FTC6)).thenReturn(toolHandler);

        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, d1));
        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, d2));

        assertThat(registry.getAllTools()).hasSize(2);
    }

    @Test
    @DisplayName("非 ToolHandler 类型的 handler 不注册（保留给未来 IArm/IArranger）")
    void nonToolHandlerSkipped() {
        Device device = new Device();
        device.setId(1L);
        device.setType(DeviceType.ATLAS_PF4000.getId());
        DeviceHandler nonToolHandler = new DeviceHandler() {
            @Override public void connect(long id) {}
            @Override public void disconnect(long id) {}
            @Override public DeviceStatus getStatus(long id) { return DeviceStatus.DISCONNECTED; }
            @Override public java.util.Set<DeviceType> getSupportedTypes() { return java.util.Set.of(); }
        };
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(nonToolHandler);

        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, device));

        assertThat(registry.getTool(1L)).isNull();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=DeviceRegistryTest -DfailIfNoTests=false -q
```
Expected: 编译失败 "cannot find symbol: class DeviceRegistry"

- [ ] **Step 3: 实现 DeviceRegistry**

```java
package com.tightening.device;

import com.tightening.constant.DeviceType;
import com.tightening.device.contract.ITool;
import com.tightening.device.contract.ToolAdapter;
import com.tightening.device.event.DeviceChangeEvent;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.DeviceHandlerFactory;
import com.tightening.device.handler.ToolHandler;
import com.tightening.entity.Device;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DeviceRegistry {

    private final Map<Long, ITool> tools = new ConcurrentHashMap<>();
    private final DeviceHandlerFactory handlerFactory;

    public DeviceRegistry(DeviceHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    public ITool getTool(Long deviceId) {
        return tools.get(deviceId);
    }

    public List<ITool> getAllTools() {
        return List.copyOf(tools.values());
    }

    @TransactionalEventListener
    void onDeviceChange(DeviceChangeEvent event) {
        switch (event.getEventType()) {
            case ADD    -> registerTool(event.getDevice());
            case UPDATE -> {
                tools.remove(event.getDeviceId());
                registerTool(event.getDevice());
            }
            case DELETE -> tools.remove(event.getDeviceId());
        }
    }

    private void registerTool(Device device) {
        DeviceHandler handler = handlerFactory.getHandler(DeviceType.getType(device.getType()));
        if (handler instanceof ToolHandler toolHandler) {
            ToolAdapter toolAdapter = new ToolAdapter(toolHandler, device);
            toolHandler.setToolAdapter(toolAdapter);  // 回路：ToolHandler 持有 ToolAdapter，stage 2 数据分叉用
            tools.put(device.getId(), toolAdapter);
            log.debug("Registered ITool for device {} (type={})", device.getId(), device.getType());
        }
    }
}
```

- [ ] **Step 3.5: 修改 ToolHandler.java 添加 ToolAdapter 回路**

修改 `src/main/java/com/tightening/device/handler/ToolHandler.java`：

1. 添加 import：
```java
import com.tightening.device.contract.ToolAdapter;
```

2. 新增约 5 行：

```java
// 在 ToolHandler 类体中，其他字段声明附近新增：
private volatile ToolAdapter toolAdapter;

// 在 ToolHandler 类体中，package-private 区域新增：
public void setToolAdapter(ToolAdapter adapter) {
    this.toolAdapter = adapter;
}

// 在 handleTighteningData() 开头预留（注释掉，stage 2 取消注释）：
// if (toolAdapter != null) {
//     toolAdapter.fireTighteningData(dto);
// }

// 在 handleCurveData() 开头预留（注释掉）：
// if (toolAdapter != null) {
//     toolAdapter.fireCurveData(dto);
// }
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest=DeviceRegistryTest -DfailIfNoTests=false
```
Expected: Tests run: 6, Failures: 0

- [ ] **Step 5: 运行全部 stage 1 测试确认无回归**

```bash
mvn test -Dtest="AtlasJudgmentTest,FitJudgmentTest,JudgmentConfigTest,ToolAdapterTest,DeviceRegistryTest" -DfailIfNoTests=false
```
Expected: Tests run: 26, Failures: 0

- [ ] **Step 6: 提交**

```bash
git add src/test/java/com/tightening/device/DeviceRegistryTest.java src/main/java/com/tightening/device/DeviceRegistry.java src/main/java/com/tightening/device/handler/ToolHandler.java
git commit -m "feat: add event-driven DeviceRegistry for ITool lifecycle"
```
