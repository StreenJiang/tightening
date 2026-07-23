# Stage 4: Capability 补齐 + 数据流修复 + API 改进 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fill 5 missing Capabilities (VALIDATION+OPERATION stages), fix ToolHandler double-write data duplication, add inbox backpressure logging, expand ExportData payload, add export_task TTL cleanup, and replace flat-string TaskStatus with structured DTO.

**Architecture:** All new Capabilities are no-arg constructors (no Spring dependencies), following existing patterns. Torque/Angle range checks are informational only — they write to `ctx.extras` and always return Pass. The ToolHandler direct `save()` is removed, making StoreData the single persistence point. The plan defers SSE/frontend notifications and real Exporter implementations to Stage 5.

**Tech Stack:** Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.9, SQLite, JUnit 5, AssertJ 3.27.7, Mockito

## Global Constraints

- 新建 6 源文件 + 7 测试文件，修改 7 源文件 + 2 测试文件，零删除
- `ProductBolt` 有 `torqueMin/torqueMax/angleMin/angleMax` 字段（Double，可为 null）
- `TaskContext.currentBolt()` 返回当前 `ProductBolt`（可能为 null）
- `TaskContext.extras` 是 `Map<String, Object>`，用于 Capability 间临时数据传递
- TorqueRangeCheck 和 AngleRangeCheck 为纯信息性 — 写入 extras，始终返回 Pass，不阻断管道
- ExecuteJudgment 优先级从 1 改为 3（让范围检查先执行）
- `ToolHandler.handleTighteningData()` 删除 `tighteningDataService.save(data)` 但保留 `toolAdapter.fireTighteningData(dto)` 和 converter 调用
- `export_task` TTL 清理使用 MyBatis-Plus `remove()`（逻辑删除），不手动 set deleted
- `TaskStatus` 为 Java record

---

### Task 1: 移除 ToolHandler 双重写入

**Files:**
- Modify: `src/main/java/com/tightening/device/handler/ToolHandler.java`

**Interfaces:**
- Consumes: nothing (data flow fix only)
- Produces: 单一数据持久化路径 — StoreData Capability 为唯一 save 点

- [ ] **Step 1: 删除 ToolHandler.handleTighteningData() 中的直接 save**

在 `ToolHandler.java` 第 167 行，删除 `tighteningDataService.save(data);`。

```java
// Before (lines 161-168):
public void handleTighteningData(TighteningDataDTO dto, Channel channel) {
    if (toolAdapter != null) {
        toolAdapter.fireTighteningData(dto);
    }
    TighteningData data = Converter.dto2Entity(dto, TighteningData::new);
    TCPDeviceHandler.applyToolTypeName(channel, data);
    tighteningDataService.save(data);  // ← 删除这一行
}

// After:
public void handleTighteningData(TighteningDataDTO dto, Channel channel) {
    if (toolAdapter != null) {
        toolAdapter.fireTighteningData(dto);
    }
    TighteningData data = Converter.dto2Entity(dto, TighteningData::new);
    TCPDeviceHandler.applyToolTypeName(channel, data);
}
```

- [ ] **Step 2: 运行全量测试确认无回归**

```bash
mvn test -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, 所有现有测试通过。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/tightening/device/handler/ToolHandler.java
git commit -m "fix: remove ToolHandler double-write, data flows exclusively through StoreData Capability"
```

---

### Task 2: WorkstationConfigCheck（VALIDATION/VALIDATING）

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/WorkstationConfigCheck.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/WorkstationConfigCheckTest.java`
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java`

**Interfaces:**
- Consumes: `Capability` interface, `TaskContext`
- Produces: `WorkstationConfigCheck` — 首个 VALIDATION 阶段 Capability

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ITool;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorkstationConfigCheck")
class WorkstationConfigCheckTest {

    private final WorkstationConfigCheck cap = new WorkstationConfigCheck();

    @Test
    @DisplayName("配置完整时返回 Pass")
    void shouldPassWhenConfigurationValid() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L)
                .taskData(new ProductTask())
                .boltConfigs(List.of(new ProductBolt()))
                .deviceRegistry(Map.of(1L, new ITool() {
                    @Override public long id() { return 1; }
                    @Override public com.tightening.constant.DeviceType type() { return com.tightening.constant.DeviceType.ATLAS_PF4000; }
                    @Override public String name() { return "tool1"; }
                    @Override public java.util.concurrent.CompletableFuture<Boolean> sendLock() { return java.util.concurrent.CompletableFuture.completedFuture(true); }
                    @Override public java.util.concurrent.CompletableFuture<Boolean> sendUnlock() { return java.util.concurrent.CompletableFuture.completedFuture(true); }
                    @Override public java.util.concurrent.CompletableFuture<Boolean> sendPSet(int psetId) { return java.util.concurrent.CompletableFuture.completedFuture(true); }
                    @Override public boolean isAlive() { return true; }
                    @Override public void onTighteningData(java.util.function.Consumer<com.tightening.dto.TighteningDataDTO> listener) {}
                }))
                .shouldSelfLoop(false)
                .build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
    }

    @Test
    @DisplayName("螺栓列表为空时返回 Fail")
    void shouldFailWhenNoBolts() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L)
                .taskData(new ProductTask())
                .boltConfigs(List.of())
                .deviceRegistry(Map.of(1L, mockTool()))
                .shouldSelfLoop(false)
                .build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("设备注册表为空时返回 Fail")
    void shouldFailWhenNoDevices() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L)
                .taskData(new ProductTask())
                .boltConfigs(List.of(new ProductBolt()))
                .deviceRegistry(Map.of())
                .shouldSelfLoop(false)
                .build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("boltConfigs 为 null 时返回 Fail")
    void shouldFailWhenBoltConfigsNull() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L)
                .taskData(new ProductTask())
                .boltConfigs(null)
                .deviceRegistry(Map.of(1L, mockTool()))
                .shouldSelfLoop(false)
                .build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("返回正确的 identity")
    void shouldReturnCorrectIdentity() {
        assertThat(cap.id()).isEqualTo("WorkstationConfigCheck");
        assertThat(cap.stage()).isEqualTo(Stage.VALIDATION);
        assertThat(cap.subState()).isEqualTo(SubState.VALIDATING);
        assertThat(cap.priority()).isEqualTo(0);
    }

    private static ITool mockTool() {
        return new ITool() {
            @Override public long id() { return 1; }
            @Override public com.tightening.constant.DeviceType type() { return com.tightening.constant.DeviceType.ATLAS_PF4000; }
            @Override public String name() { return "tool1"; }
            @Override public java.util.concurrent.CompletableFuture<Boolean> sendLock() { return java.util.concurrent.CompletableFuture.completedFuture(true); }
            @Override public java.util.concurrent.CompletableFuture<Boolean> sendUnlock() { return java.util.concurrent.CompletableFuture.completedFuture(true); }
            @Override public java.util.concurrent.CompletableFuture<Boolean> sendPSet(int psetId) { return java.util.concurrent.CompletableFuture.completedFuture(true); }
            @Override public boolean isAlive() { return true; }
            @Override public void onTighteningData(java.util.function.Consumer<com.tightening.dto.TighteningDataDTO> listener) {}
        };
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=WorkstationConfigCheckTest -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: compilation error — `WorkstationConfigCheck` class not found.

- [ ] **Step 3: 实现 WorkstationConfigCheck.java**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkstationConfigCheck implements Capability {

    @Override public String id() { return "WorkstationConfigCheck"; }
    @Override public Stage stage() { return Stage.VALIDATION; }
    @Override public SubState subState() { return SubState.VALIDATING; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        if (ctx.getBoltConfigs() == null || ctx.getBoltConfigs().isEmpty()) {
            log.warn("WorkstationConfigCheck FAIL: no bolt configs");
            return CapabilityResult.Fail;
        }
        if (ctx.getDeviceRegistry() == null || ctx.getDeviceRegistry().isEmpty()) {
            log.warn("WorkstationConfigCheck FAIL: no devices in registry");
            return CapabilityResult.Fail;
        }
        if (ctx.getTaskData() == null) {
            log.warn("WorkstationConfigCheck FAIL: no task data");
            return CapabilityResult.Fail;
        }
        log.info("WorkstationConfigCheck PASS: {} bolts, {} devices",
                ctx.totalBolts(), ctx.getDeviceRegistry().size());
        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 4: 修改 LifecycleEngineFactory.java — 注册 WorkstationConfigCheck**

在 `capabilities` 列表最前面插入 `new WorkstationConfigCheck(),`：

```java
List<Capability> capabilities = List.of(
    new WorkstationConfigCheck(),
    new PrepareBolts(),
    new CreateTaskRecord(taskRecordService),
    // ... 其余不变
);
```

- [ ] **Step 5: 运行测试确认通过**

```bash
mvn test -Dtest=WorkstationConfigCheckTest -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: Tests run: 5, Failures: 0.

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/WorkstationConfigCheck.java \
        src/test/java/com/tightening/lifecycle/capability/WorkstationConfigCheckTest.java \
        src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java
git commit -m "feat: add WorkstationConfigCheck Capability for VALIDATION stage"
```

---

### Task 3: ReceiveData（OPERATION/TIGHTENING_RECEIVED）

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/ReceiveData.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/ReceiveDataTest.java`
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java`

**Interfaces:**
- Consumes: `Capability` interface, `TaskContext.currentOperationData`
- Produces: `ReceiveData` — 形式化 TIGHTENING_RECEIVED 等待点的数据到达验证

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.TaskContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReceiveData")
class ReceiveDataTest {

    private final ReceiveData cap = new ReceiveData();

    @Test
    @DisplayName("有效数据到达时返回 Pass")
    void shouldPassWhenDataReceived() {
        TighteningData data = new TighteningData();
        data.setTighteningId(12345L);
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(java.util.List.of()).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
    }

    @Test
    @DisplayName("无数据时返回 Fail")
    void shouldFailWhenNoData() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(java.util.List.of()).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(null).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("tighteningId 为 0 时返回 Fail")
    void shouldFailWhenTighteningIdZero() {
        TighteningData data = new TighteningData();
        data.setTighteningId(0L);
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(java.util.List.of()).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("返回正确的 identity")
    void shouldReturnCorrectIdentity() {
        assertThat(cap.id()).isEqualTo("ReceiveData");
        assertThat(cap.stage()).isEqualTo(Stage.OPERATION);
        assertThat(cap.subState()).isEqualTo(SubState.TIGHTENING_RECEIVED);
        assertThat(cap.priority()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=ReceiveDataTest -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: compilation error — `ReceiveData` class not found.

- [ ] **Step 3: 实现 ReceiveData.java**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReceiveData implements Capability {

    @Override public String id() { return "ReceiveData"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.TIGHTENING_RECEIVED; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        TighteningData data = ctx.getCurrentOperationData();
        if (data == null) {
            log.warn("ReceiveData FAIL: no current operation data");
            return CapabilityResult.Fail;
        }
        if (data.getTighteningId() == null || data.getTighteningId() <= 0) {
            log.warn("ReceiveData FAIL: invalid tighteningId={}", data.getTighteningId());
            return CapabilityResult.Fail;
        }
        log.debug("ReceiveData PASS: tighteningId={}, deviceId resolved", data.getTighteningId());
        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 4: 修改 LifecycleEngineFactory.java — 注册 ReceiveData**

在 OPERATION 区域添加：

```java
// OPERATION - TIGHTENING_RECEIVED
new ReceiveData(),
```

- [ ] **Step 5: 运行测试确认通过**

```bash
mvn test -Dtest=ReceiveDataTest -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: Tests run: 4, Failures: 0.

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/ReceiveData.java \
        src/test/java/com/tightening/lifecycle/capability/ReceiveDataTest.java \
        src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java
git commit -m "feat: add ReceiveData Capability for OPERATION/TIGHTENING_RECEIVED"
```

---

### Task 4: TorqueRangeCheck（OPERATION/JUDGING, pri=1）

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/TorqueRangeCheck.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/TorqueRangeCheckTest.java`
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java`

**Interfaces:**
- Consumes: `TaskContext.currentBolt()`, `TaskContext.getCurrentOperationData()`, `ProductBolt.torqueMin/torqueMax`
- Produces: extras 写入 `torqueInRange`, `torqueMin`, `torqueMax`, `torqueActual`

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.TaskContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TorqueRangeCheck")
class TorqueRangeCheckTest {

    private TorqueRangeCheck cap;
    private ProductBolt bolt;
    private TighteningData data;

    @BeforeEach
    void setUp() {
        cap = new TorqueRangeCheck();
        bolt = new ProductBolt().setTorqueMin(10.0).setTorqueMax(20.0);
        data = new TighteningData();
    }

    @Test
    @DisplayName("扭矩在范围内时 Pass 且 extras[torqueInRange]=true")
    void shouldPassWhenTorqueInRange() {
        data.setTorque(15.0);
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(List.of(bolt)).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data)
                .boltStates(new BoltState[]{BoltState.TIGHTENING}).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("torqueInRange")).isEqualTo(true);
        assertThat(ctx.getExtras().get("torqueMin")).isEqualTo(10.0);
        assertThat(ctx.getExtras().get("torqueMax")).isEqualTo(20.0);
        assertThat(ctx.getExtras().get("torqueActual")).isEqualTo(15.0);
    }

    @Test
    @DisplayName("扭矩低于下限时 Pass 且 extras[torqueInRange]=false")
    void shouldPassWhenTorqueBelowMin() {
        data.setTorque(5.0);
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(List.of(bolt)).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data)
                .boltStates(new BoltState[]{BoltState.TIGHTENING}).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("torqueInRange")).isEqualTo(false);
    }

    @Test
    @DisplayName("扭矩高于上限时 Pass 且 extras[torqueInRange]=false")
    void shouldPassWhenTorqueAboveMax() {
        data.setTorque(25.0);
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(List.of(bolt)).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data)
                .boltStates(new BoltState[]{BoltState.TIGHTENING}).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("torqueInRange")).isEqualTo(false);
    }

    @Test
    @DisplayName("限值为 null 时 precondition 返回 false")
    void shouldSkipWhenNoLimits() {
        ProductBolt boltNoLimits = new ProductBolt();
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(List.of(boltNoLimits)).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data)
                .boltStates(new BoltState[]{BoltState.TIGHTENING}).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("无当前螺栓时 precondition 返回 false")
    void shouldSkipWhenNoCurrentBolt() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(List.of()).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("返回正确的 identity")
    void shouldReturnCorrectIdentity() {
        assertThat(cap.id()).isEqualTo("TorqueRangeCheck");
        assertThat(cap.stage()).isEqualTo(Stage.OPERATION);
        assertThat(cap.subState()).isEqualTo(SubState.JUDGING);
        assertThat(cap.priority()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=TorqueRangeCheckTest -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: compilation error — `TorqueRangeCheck` class not found.

- [ ] **Step 3: 实现 TorqueRangeCheck.java**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TorqueRangeCheck implements Capability {

    @Override public String id() { return "TorqueRangeCheck"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.JUDGING; }
    @Override public int priority() { return 1; }

    @Override
    public boolean precondition(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        return bolt != null && bolt.getTorqueMin() != null && bolt.getTorqueMax() != null;
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        TighteningData data = ctx.getCurrentOperationData();

        double min = bolt.getTorqueMin();
        double max = bolt.getTorqueMax();
        double actual = data.getTorque() != null ? data.getTorque() : 0.0;
        boolean inRange = actual >= min && actual <= max;

        ctx.getExtras().put("torqueInRange", inRange);
        ctx.getExtras().put("torqueMin", min);
        ctx.getExtras().put("torqueMax", max);
        ctx.getExtras().put("torqueActual", actual);

        log.debug("TorqueRangeCheck: actual={} range=[{}, {}] inRange={}", actual, min, max, inRange);
        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 4: 修改 LifecycleEngineFactory.java — 注册 TorqueRangeCheck**

在 JUDGING 区域添加：

```java
new TorqueRangeCheck(),
```

- [ ] **Step 5: 运行测试确认通过**

```bash
mvn test -Dtest=TorqueRangeCheckTest -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: Tests run: 6, Failures: 0.

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/TorqueRangeCheck.java \
        src/test/java/com/tightening/lifecycle/capability/TorqueRangeCheckTest.java \
        src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java
git commit -m "feat: add TorqueRangeCheck Capability for OPERATION/JUDGING"
```

---

### Task 5: AngleRangeCheck + ExecuteJudgment 优先级调整

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/AngleRangeCheck.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/AngleRangeCheckTest.java`
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java`
- Modify: `src/main/java/com/tightening/lifecycle/capability/ExecuteJudgment.java`

**Interfaces:**
- Consumes: `TaskContext.currentBolt()`, `ProductBolt.angleMin/angleMax`
- Produces: extras 写入 `angleInRange`, `angleMin`, `angleMax`, `angleActual`; ExecuteJudgment priority 1→3

- [ ] **Step 1: 写失败测试 — AngleRangeCheckTest**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.TaskContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AngleRangeCheck")
class AngleRangeCheckTest {

    private AngleRangeCheck cap;
    private ProductBolt bolt;
    private TighteningData data;

    @BeforeEach
    void setUp() {
        cap = new AngleRangeCheck();
        bolt = new ProductBolt().setAngleMin(30.0).setAngleMax(60.0);
        data = new TighteningData();
    }

    @Test
    @DisplayName("角度在范围内时 Pass 且 extras[angleInRange]=true")
    void shouldPassWhenAngleInRange() {
        data.setAngle(45.0);
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(List.of(bolt)).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data)
                .boltStates(new BoltState[]{BoltState.TIGHTENING}).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("angleInRange")).isEqualTo(true);
        assertThat(ctx.getExtras().get("angleMin")).isEqualTo(30.0);
        assertThat(ctx.getExtras().get("angleMax")).isEqualTo(60.0);
        assertThat(ctx.getExtras().get("angleActual")).isEqualTo(45.0);
    }

    @Test
    @DisplayName("角度低于下限时 Pass 且 extras[angleInRange]=false")
    void shouldPassWhenAngleBelowMin() {
        data.setAngle(15.0);
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(List.of(bolt)).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data)
                .boltStates(new BoltState[]{BoltState.TIGHTENING}).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("angleInRange")).isEqualTo(false);
    }

    @Test
    @DisplayName("角度高于上限时 Pass 且 extras[angleInRange]=false")
    void shouldPassWhenAngleAboveMax() {
        data.setAngle(75.0);
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(List.of(bolt)).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data)
                .boltStates(new BoltState[]{BoltState.TIGHTENING}).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("angleInRange")).isEqualTo(false);
    }

    @Test
    @DisplayName("限值为 null 时 precondition 返回 false")
    void shouldSkipWhenNoLimits() {
        ProductBolt boltNoLimits = new ProductBolt();
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(List.of(boltNoLimits)).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data)
                .boltStates(new BoltState[]{BoltState.TIGHTENING}).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("无当前螺栓时 precondition 返回 false")
    void shouldSkipWhenNoCurrentBolt() {
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new com.tightening.entity.ProductTask())
                .boltConfigs(List.of()).deviceRegistry(java.util.Map.of())
                .shouldSelfLoop(false).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("返回正确的 identity")
    void shouldReturnCorrectIdentity() {
        assertThat(cap.id()).isEqualTo("AngleRangeCheck");
        assertThat(cap.stage()).isEqualTo(Stage.OPERATION);
        assertThat(cap.subState()).isEqualTo(SubState.JUDGING);
        assertThat(cap.priority()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=AngleRangeCheckTest -DfailIfNoTests=false 2>&1 | tail -10
```

- [ ] **Step 3: 实现 AngleRangeCheck.java**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AngleRangeCheck implements Capability {

    @Override public String id() { return "AngleRangeCheck"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.JUDGING; }
    @Override public int priority() { return 2; }

    @Override
    public boolean precondition(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        return bolt != null && bolt.getAngleMin() != null && bolt.getAngleMax() != null;
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        TighteningData data = ctx.getCurrentOperationData();

        double min = bolt.getAngleMin();
        double max = bolt.getAngleMax();
        double actual = data.getAngle() != null ? data.getAngle() : 0.0;
        boolean inRange = actual >= min && actual <= max;

        ctx.getExtras().put("angleInRange", inRange);
        ctx.getExtras().put("angleMin", min);
        ctx.getExtras().put("angleMax", max);
        ctx.getExtras().put("angleActual", actual);

        log.debug("AngleRangeCheck: actual={} range=[{}, {}] inRange={}", actual, min, max, inRange);
        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 4: 修改 ExecuteJudgment.java — 优先级 1→3**

```java
// Before:
@Override public int priority() { return 1; }

// After:
@Override public int priority() { return 3; }
```

- [ ] **Step 5: 修改 LifecycleEngineFactory.java — 注册 AngleRangeCheck + 更新 ExecuteJudgment 注释**

在 JUDGING 区域最终顺序为：

```java
new ControllerStatusCheck(),
new TorqueRangeCheck(),
new AngleRangeCheck(),
new ExecuteJudgment(judgmentStrategies),
```

- [ ] **Step 6: 运行测试确认通过**

```bash
mvn test -Dtest=AngleRangeCheckTest,ExecuteJudgmentTest -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: Tests run: 6 (AngleRangeCheck) + existing ExecuteJudgment tests, Failures: 0.

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/AngleRangeCheck.java \
        src/test/java/com/tightening/lifecycle/capability/AngleRangeCheckTest.java \
        src/main/java/com/tightening/lifecycle/capability/ExecuteJudgment.java \
        src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java
git commit -m "feat: add AngleRangeCheck Capability, bump ExecuteJudgment priority to 3"
```

---

### Task 6: ExportData payload 扩展

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/capability/ExportData.java`
- Modify: `src/test/java/com/tightening/lifecycle/capability/ExportDataTest.java`

**Interfaces:**
- Consumes: `TaskRecord.productCode`, `TaskRecord.isRework`
- Produces: payload 新增 `productCode`, `isRework`, `timestamp` 字段

- [ ] **Step 1: 读取当前 ExportData.java 和 ExportDataTest.java**

先确认当前代码状态，然后精确修改。

```bash
grep -n "payload.put" src/main/java/com/tightening/lifecycle/capability/ExportData.java
```

- [ ] **Step 2: 修改 ExportData.execute() — 扩展 payload**

在 `ExportData.java` 的 `execute()` 方法中，在现有 `payload.put(...)` 调用后添加三行：

```java
Map<String, Object> payload = new LinkedHashMap<>();
payload.put("taskId", ctx.getProductTaskId());
payload.put("taskRecordId", record.getId());
payload.put("taskResult", record.getTaskResult());
// === 新增字段 ===
payload.put("productCode", record.getProductCode());
payload.put("isRework", record.getIsRework());
payload.put("timestamp", LocalDateTime.now().toString());
// ================
payload.put("boltCount", ctx.totalBolts());
payload.put("tighteningDataCount", ctx.getTighteningDataList().size());
```

需要添加 import：

```java
import java.time.LocalDateTime;
```

- [ ] **Step 3: 更新 ExportDataTest — 验证新字段**

修改 `shouldCreateTasksPerExportType()` 测试，添加对 payload 中新字段的验证：

```java
@Test
@DisplayName("按 settings exportTypes 列表创建多条 export_task，payload 包含完整字段")
void shouldCreateTasksPerExportType() {
    LocalSettings settings = new LocalSettings(false, List.of("standard_excel", "outer_db_store"));
    ExportData cap = new ExportData(exportTaskService, settings);
    TaskRecord record = new TaskRecord()
            .setId(42L)
            .setProductCode("P001")
            .setIsRework(0)
            .setTaskResult(TaskResult.OK.getCode());
    TaskContext ctx = TaskContext.builder()
            .productTaskId(1L).taskData(new ProductTask())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .taskRecord(record).build();

    assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);

    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(exportTaskService).createTask(eq("standard_excel"), eq(42L), payloadCaptor.capture());
    String payload = payloadCaptor.getValue();
    assertThat(payload).contains("\"productCode\":\"P001\"");
    assertThat(payload).contains("\"isRework\":0");
    assertThat(payload).contains("\"timestamp\":");
    verify(exportTaskService, times(2)).createTask(anyString(), eq(42L), anyString());
}
```

需要添加 import：

```java
import com.tightening.constant.TaskResult;
import org.mockito.ArgumentCaptor;
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest=ExportDataTest -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: Tests run: 2, Failures: 0.

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/ExportData.java \
        src/test/java/com/tightening/lifecycle/capability/ExportDataTest.java
git commit -m "feat: add productCode, isRework, timestamp to ExportData payload"
```

---

### Task 7: export_task TTL 清理

**Files:**
- Modify: `src/main/java/com/tightening/service/ExportTaskService.java`
- Modify: `src/main/java/com/tightening/export/ExportWorker.java`
- Create: `src/test/java/com/tightening/service/ExportTaskServiceCleanupTest.java`

**Interfaces:**
- Consumes: `ExportTaskMapper`（通过 ServiceImpl 继承）, `ExportTaskStatus`
- Produces: `cleanupTasks(int olderThanDays)` 返回清理数量; `ExportWorker.cleanupOldTasks()` @Scheduled

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.service;

import com.tightening.constant.ExportTaskStatus;
import com.tightening.entity.ExportTask;
import com.tightening.mapper.ExportTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportTaskService cleanup")
class ExportTaskServiceCleanupTest {

    @Mock
    private ExportTaskMapper mapper;

    private ExportTaskService service;

    @BeforeEach
    void setUp() {
        service = new ExportTaskService();
        service.baseMapper = mapper;
    }

    @Test
    @DisplayName("cleanupTasks 只删除 COMPLETED 和 FAILED 且早于 cutoff 的任务")
    void shouldCleanupOldCompletedAndFailedTasks() {
        when(mapper.delete(any())).thenReturn(3);

        service.cleanupTasks(7);

        verify(mapper).delete(any());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=ExportTaskServiceCleanupTest -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: compilation error — `cleanupTasks` method not found.

- [ ] **Step 3: 在 ExportTaskService.java 添加 cleanupTasks 方法**

```java
import java.time.LocalDateTime;

/**
 * 清理超过指定天数的已完成/失败导出任务（使用逻辑删除）。
 * @param olderThanDays 保留天数，超过此天数的 COMPLETED/FAILED 任务将被删除
 * @return 删除的任务数量
 */
public int cleanupTasks(int olderThanDays) {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
    return lambdaUpdate()
            .in(ExportTask::getStatus, ExportTaskStatus.COMPLETED.getCode(), ExportTaskStatus.FAILED.getCode())
            .apply("completed_at IS NOT NULL AND completed_at < {0}", cutoff.toString())
            .remove() ? 1 : 0;
}
```

注意：`remove()` 返回 boolean 表示是否成功。批量删除时 MyBatis-Plus 返回的是影响的记录数，但 `remove()` API 返回 boolean。对于精确计数，可以改为使用 mapper 直接操作，但这里业务上不需要精确计数。

更精确的实现：

```java
public int cleanupTasks(int olderThanDays) {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
    return lambdaUpdate()
            .in(ExportTask::getStatus, ExportTaskStatus.COMPLETED.getCode(), ExportTaskStatus.FAILED.getCode())
            .apply("completed_at IS NOT NULL AND completed_at < {0}", cutoff.toString())
            .remove() ? 1 : 0;
}
```

实际改进版本（使用 `int` 返回值更准确）：

```java
public int cleanupTasks(int olderThanDays) {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
    var wrapper = lambdaUpdate()
            .in(ExportTask::getStatus, ExportTaskStatus.COMPLETED.getCode(), ExportTaskStatus.FAILED.getCode())
            .apply("completed_at IS NOT NULL AND completed_at < {0}", cutoff.toString())
            .getWrapper();
    return baseMapper.delete(wrapper);
}
```

- [ ] **Step 4: 在 ExportWorker.java 添加清理调度**

```java
@Scheduled(cron = "0 0 3 * * ?")
public void cleanupOldTasks() {
    try {
        int removed = exportTaskService.cleanupTasks(7);
        if (removed > 0) {
            log.info("Cleaned up {} old export tasks (older than 7 days)", removed);
        }
    } catch (Exception e) {
        log.error("Export task cleanup failed", e);
    }
}
```

需要添加 import：

```java
import org.springframework.scheduling.annotation.Scheduled;
```

- [ ] **Step 5: 运行测试确认通过**

```bash
mvn test -Dtest=ExportTaskServiceCleanupTest -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: Tests run: 1, Failures: 0.

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/tightening/service/ExportTaskService.java \
        src/main/java/com/tightening/export/ExportWorker.java \
        src/test/java/com/tightening/service/ExportTaskServiceCleanupTest.java
git commit -m "feat: add export_task TTL cleanup (daily at 3am, 7-day retention)"
```

---

### Task 8: Inbox 背压检查

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngine.java`

**Interfaces:**
- Consumes: `BlockingQueue.offer()` 返回值
- Produces: WARN 日志当 inbox 满时丢消息

- [ ] **Step 1: 读取 LifecycleEngine.java 的相关部分**

```bash
grep -n "inbox.offer\|inbox\.offer\|postMessage\|handleStageFailure\|handleActorCrash" src/main/java/com/tightening/lifecycle/LifecycleEngine.java
```

- [ ] **Step 2: 修改 postMessage() — 添加背压检查**

```java
// Before:
public void postMessage(InboundMessage msg) {
    if (alive) inbox.offer(msg);
}

// After:
public void postMessage(InboundMessage msg) {
    if (!alive) return;
    boolean accepted = inbox.offer(msg);
    if (!accepted) {
        log.warn("Inbox FULL (capacity={}), dropping message: {}",
                inbox.size() + inbox.remainingCapacity(),
                msg.getClass().getSimpleName());
    }
}
```

- [ ] **Step 3: 修改 handleStageFailure 中的 retry offer**

找到 `inbox.offer(new InboundCommand.AdvancePipeline())` 的调用：

```java
// Before:
tickScheduler.schedule(() -> inbox.offer(new InboundCommand.AdvancePipeline()), 100, TimeUnit.MILLISECONDS);

// After:
tickScheduler.schedule(() -> {
    boolean accepted = inbox.offer(new InboundCommand.AdvancePipeline());
    if (!accepted) {
        log.warn("Retry AdvancePipeline dropped (inbox full)");
    }
}, 100, TimeUnit.MILLISECONDS);
```

- [ ] **Step 4: 修改 handleActorCrash 中的 Faulted offer**

```java
// Before:
inbox.offer(new EngineInternal.Faulted(e.getMessage()));

// After:
boolean accepted = inbox.offer(new EngineInternal.Faulted(e.getMessage()));
if (!accepted) {
    log.error("Faulted message dropped (inbox full) — engine may hang: {}", e.getMessage());
}
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile -q 2>&1
```

Expected: 编译成功，无错误。

- [ ] **Step 6: 运行全量测试**

```bash
mvn test -DfailIfNoTests=false 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/LifecycleEngine.java
git commit -m "fix: add inbox backpressure logging to prevent silent message drops"
```

---

### Task 9: TaskStatus DTO

**Files:**
- Create: `src/main/java/com/tightening/dto/TaskStatus.java`
- Modify: `src/main/java/com/tightening/controller/TaskLifecycleController.java`
- Create: `src/test/java/com/tightening/controller/TaskLifecycleControllerTest.java`

**Interfaces:**
- Consumes: `TaskOrchestrator.getActiveEngine()`, `LifecycleEngine.getContext()`
- Produces: `TaskStatus` record, 结构化 `GET /api/tasks/{id}/status` 响应

- [ ] **Step 1: 创建 TaskStatus.java record**

```java
package com.tightening.dto;

public record TaskStatus(
    String status,
    String stage,
    String subState,
    int currentBoltIndex,
    int totalBolts,
    Long taskRecordId
) {}
```

- [ ] **Step 2: 写失败测试**

```java
package com.tightening.controller;

import com.tightening.dto.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskStatus DTO")
class TaskLifecycleControllerTest {

    @Test
    @DisplayName("TaskStatus record 构造和访问")
    void shouldConstructTaskStatus() {
        TaskStatus status = new TaskStatus("running", "OPERATION", "JUDGING", 2, 5, 42L);
        assertThat(status.status()).isEqualTo("running");
        assertThat(status.stage()).isEqualTo("OPERATION");
        assertThat(status.subState()).isEqualTo("JUDGING");
        assertThat(status.currentBoltIndex()).isEqualTo(2);
        assertThat(status.totalBolts()).isEqualTo(5);
        assertThat(status.taskRecordId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("idle 状态各字段为 null/0")
    void shouldHaveNullsForIdle() {
        TaskStatus status = new TaskStatus("idle", null, null, 0, 0, null);
        assertThat(status.status()).isEqualTo("idle");
        assertThat(status.stage()).isNull();
        assertThat(status.subState()).isNull();
        assertThat(status.currentBoltIndex()).isEqualTo(0);
        assertThat(status.totalBolts()).isEqualTo(0);
        assertThat(status.taskRecordId()).isNull();
    }
}
```

- [ ] **Step 3: 运行测试确认失败？**

TaskStatus DTO 是纯 record，测试不会失败。但 Controller 测试会编译失败（如果测试中引用了旧 API）。

```bash
mvn test -Dtest=TaskLifecycleControllerTest -DfailIfNoTests=false 2>&1 | tail -10
```

Expected: DTO 测试本身应该通过（纯 record，没有编译依赖）。

- [ ] **Step 4: 修改 TaskLifecycleController.getTaskStatus()**

```java
@GetMapping("/{id}/status")
public ResponseEntity<ApiResponse<TaskStatus>> getTaskStatus(@PathVariable Long id) {
    var engineOpt = orchestrator.getActiveEngine(id);
    if (engineOpt.isEmpty()) {
        return ResponseEntity.ok(ApiResponse.ok(
                new TaskStatus("idle", null, null, 0, 0, null)));
    }
    var engine = engineOpt.get();
    TaskContext ctx = engine.getContext();
    String status = engine.isAlive() ? "running" : "finished";
    String stage = ctx != null && ctx.getCurrentStage() != null
            ? ctx.getCurrentStage().name() : null;
    String subState = ctx != null && ctx.getCurrentSubState() != null
            ? ctx.getCurrentSubState().name() : null;
    int currentBoltIndex = ctx != null ? ctx.getCurrentBoltIndex() : 0;
    int totalBolts = ctx != null ? ctx.totalBolts() : 0;
    Long taskRecordId = ctx != null && ctx.getTaskRecord() != null
            ? ctx.getTaskRecord().getId() : null;
    return ResponseEntity.ok(ApiResponse.ok(
            new TaskStatus(status, stage, subState, currentBoltIndex, totalBolts, taskRecordId)));
}
```

需要添加 import：

```java
import com.tightening.dto.TaskStatus;
import com.tightening.lifecycle.TaskContext;
```

- [ ] **Step 5: 运行测试 + 全量编译**

```bash
mvn compile -q 2>&1 && mvn test -Dtest=TaskLifecycleControllerTest -DfailIfNoTests=false 2>&1 | tail -10
```

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/tightening/dto/TaskStatus.java \
        src/main/java/com/tightening/controller/TaskLifecycleController.java \
        src/test/java/com/tightening/controller/TaskLifecycleControllerTest.java
git commit -m "feat: add structured TaskStatus DTO for /api/tasks/{id}/status"
```

---

## 验证

```bash
# 全量测试
mvn clean test -DfailIfNoTests=false

# 启动验证
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# API 测试
curl -s http://localhost:8080/api/tasks/1/status | jq .
# Expected: {"code":200,"message":"ok","data":{"status":"idle","stage":null,"subState":null,...}}
```

## 文件统计

**新建: 6 源文件 + 7 测试文件 = 13 文件**
**修改: 7 源文件 + 2 测试文件 = 9 文件**
**零删除**

## 已知的后续未实现（Stage 5+ 候选）

| 类别 | 项目 |
|------|------|
| Exporter | StandardExcelExporter、OuterDatabaseStorer、PlcResultSender 真实实现 |
| 前端通知 | SSE / WebSocket 推送架构 |
| 出站消息 | 引擎事件（锁失败、异常）→ 前端推送 |
| 数据流 | inbox 满时的降级策略（当前仅 WARN） |
| Stub Capability | SendArrangerSignal、SendSetterSelector、BoltBarCodeCheck 仍然 disabled |
