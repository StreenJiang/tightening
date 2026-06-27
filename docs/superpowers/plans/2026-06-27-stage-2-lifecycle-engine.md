# Stage 2: LifecycleEngine 内核 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Actor engine core — `LifecycleEngine` with BlockingQueue inbox, MessageHandler registry, pipeline advancement, and crash recovery — plus key Capabilities proving end-to-end data flow from ToolAdapter data fork through judgment to storage.

**Architecture:** `LifecycleEngine` is NOT a Spring bean — instantiated per-mission via `LifecycleEngineFactory` (@Component). Single-threaded actor loop with `inbox.take()` blocking wait, `Map<Class, MessageHandler>` dispatch. Pipeline advances recursively through non-waiting sub-states, pauses at waiting points. Checkpoint persisted to `mission_record.context_snapshot` at key transitions.

**Tech Stack:** Java 21, JUnit 5, AssertJ 3.27.7, Mockito (transitive), Spring Boot Test, MyBatis-Plus

## Global Constraints

- 新建 ~34 源文件 + ~15 测试文件，修改 1 个现有文件（`MissionRecordService.java`）
- `LifecycleEngine` 非 Spring Bean，由 `LifecycleEngineFactory`（@Component）创建
- 枚举遵循 `MissionResult` 模式：`int code` + `Optional<X> fromCode(int)`
- `ContextCheckpoint` 必须在 `saveCheckpoint()` 中写 DB（`mission_record.context_snapshot`）
- `MessageHandler` 签名：`void handle(InboundMessage msg, MissionContext ctx, LifecycleEngine engine)` 三元组
- `LockMessage` 使用 `record(String source, String reason)`，非 `Set<String>`
- `boltConfigs` 按 `boltSerialNum` 排序（由调用方保证）
- OPERATION 管道：`SWITCH_BOLT`(非等待) → `TIGHTENING_RECEIVED`(等待点，等拧紧数据) → `JUDGING` → `STORING` → `ADVANCING`(非等待) → 循环回 `SWITCH_BOLT`
- ADVANCING 通过显式 `registerTransition(ADVANCING → SWITCH_BOLT)` 实现螺栓循环
- AdvanceBolt 检测全部完成时直接设置 `ctx` 为 FINALIZATION 阶段，`advancePipeline()` 检测 Capability 引起的阶段重定向
- FINALIZATION 全部子状态为非等待点，管道快速通过后到达终点 → `onCompleted` 回调 → `shutdown()`
- Stub Capability 的 `precondition()` 返回 `false`，引擎自动 Skip
- 管道终点检测：`getNext()` 返回不变时终止推进
- `volatile interruptRequested` 保留（因为 `interrupt()` 从外部线程调用，非 Actor 线程），`interruptReason` 同样加 `volatile`

---

### Task 1: Stage、SubState、BoltState 枚举

**Files:**
- Create: `src/main/java/com/tightening/constant/Stage.java`
- Create: `src/main/java/com/tightening/constant/SubState.java`
- Create: `src/main/java/com/tightening/constant/BoltState.java`
- Create: `src/test/java/com/tightening/constant/StageTest.java`
- Create: `src/test/java/com/tightening/constant/SubStateTest.java`
- Create: `src/test/java/com/tightening/constant/BoltStateTest.java`

**Interfaces:**
- Consumes: nothing (standalone enum)
- Produces: `Stage` (VALIDATION, ACTIVATION, OPERATION, FINALIZATION), `SubState` (14 values incl. SWITCH_BOLT, FAULTED), `BoltState` (PENDING, TIGHTENING, JUDGED_OK, JUDGED_NG) — each with `int getCode()` + `static Optional<X> fromCode(int)`

- [ ] **Step 1: 写失败测试 — StageTest**

```java
package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stage 枚举")
class StageTest {

    @Test
    @DisplayName("fromCode 返回正确的 Stage")
    void fromCodeShouldReturnCorrectStage() {
        assertThat(Stage.fromCode(0)).isEqualTo(Optional.of(Stage.VALIDATION));
        assertThat(Stage.fromCode(1)).isEqualTo(Optional.of(Stage.ACTIVATION));
        assertThat(Stage.fromCode(2)).isEqualTo(Optional.of(Stage.OPERATION));
        assertThat(Stage.fromCode(3)).isEqualTo(Optional.of(Stage.FINALIZATION));
    }

    @Test
    @DisplayName("无效 code 返回 empty")
    void fromCodeShouldReturnEmptyForInvalidCode() {
        assertThat(Stage.fromCode(99)).isEmpty();
        assertThat(Stage.fromCode(-1)).isEmpty();
    }

    @Test
    @DisplayName("所有 code 唯一")
    void codesShouldBeUnique() {
        var codes = new HashSet<Integer>();
        for (Stage s : Stage.values()) {
            assertThat(codes.add(s.getCode()))
                .withFailMessage("Duplicate code: %d", s.getCode())
                .isTrue();
        }
    }
}
```

- [ ] **Step 2: 写失败测试 — SubStateTest**

```java
package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubState 枚举")
class SubStateTest {

    @Test
    @DisplayName("fromCode 返回正确的 SubState")
    void fromCodeShouldReturnCorrectSubState() {
        assertThat(SubState.fromCode(4)).isEqualTo(Optional.of(SubState.SWITCH_BOLT));
        assertThat(SubState.fromCode(5)).isEqualTo(Optional.of(SubState.TIGHTENING_RECEIVED));
        assertThat(SubState.fromCode(6)).isEqualTo(Optional.of(SubState.JUDGING));
        assertThat(SubState.fromCode(99)).isEqualTo(Optional.of(SubState.FAULTED));
    }

    @Test
    @DisplayName("无效 code 返回 empty")
    void fromCodeShouldReturnEmptyForInvalidCode() {
        assertThat(SubState.fromCode(100)).isEmpty();
        assertThat(SubState.fromCode(-1)).isEmpty();
    }

    @Test
    @DisplayName("所有 code 唯一")
    void codesShouldBeUnique() {
        var codes = new HashSet<Integer>();
        for (SubState s : SubState.values()) {
            assertThat(codes.add(s.getCode()))
                .withFailMessage("Duplicate code: %d", s.getCode())
                .isTrue();
        }
    }
}
```

- [ ] **Step 3: 写失败测试 — BoltStateTest**

```java
package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BoltState 枚举")
class BoltStateTest {

    @Test
    @DisplayName("fromCode 返回正确的 BoltState")
    void fromCodeShouldReturnCorrectBoltState() {
        assertThat(BoltState.fromCode(0)).isEqualTo(Optional.of(BoltState.PENDING));
        assertThat(BoltState.fromCode(1)).isEqualTo(Optional.of(BoltState.TIGHTENING));
        assertThat(BoltState.fromCode(2)).isEqualTo(Optional.of(BoltState.JUDGED_OK));
        assertThat(BoltState.fromCode(3)).isEqualTo(Optional.of(BoltState.JUDGED_NG));
    }

    @Test
    @DisplayName("无效 code 返回 empty")
    void fromCodeShouldReturnEmptyForInvalidCode() {
        assertThat(BoltState.fromCode(99)).isEmpty();
        assertThat(BoltState.fromCode(-1)).isEmpty();
    }

    @Test
    @DisplayName("所有 code 唯一")
    void codesShouldBeUnique() {
        var codes = new HashSet<Integer>();
        for (BoltState s : BoltState.values()) {
            assertThat(codes.add(s.getCode()))
                .withFailMessage("Duplicate code: %d", s.getCode())
                .isTrue();
        }
    }
}
```

- [ ] **Step 4: 运行测试确认失败**

```bash
mvn test -Dtest="StageTest,SubStateTest,BoltStateTest" -DfailIfNoTests=false -q
```
Expected: 编译失败 "cannot find symbol: class Stage"

- [ ] **Step 5: 实现 Stage.java**

```java
package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum Stage {
    VALIDATION(0),
    ACTIVATION(1),
    OPERATION(2),
    FINALIZATION(3);

    private final int code;

    Stage(int code) {
        this.code = code;
    }

    public static Optional<Stage> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 6: 实现 SubState.java**

```java
package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum SubState {
    IDLE(0),
    VALIDATING(1),
    PREPARING(2),
    ACTIVATING(3),
    SWITCH_BOLT(4),
    TIGHTENING_RECEIVED(5),
    JUDGING(6),
    STORING(7),
    ADVANCING(8),
    CLEANING_TASKS(9),
    LOCKING_TOOLS(10),
    RESETTING_STATE(11),
    EXPORTING(12),
    FAULTED(99);

    private final int code;

    SubState(int code) {
        this.code = code;
    }

    public static Optional<SubState> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 7: 实现 BoltState.java**

```java
package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum BoltState {
    PENDING(0),
    TIGHTENING(1),
    JUDGED_OK(2),
    JUDGED_NG(3);

    private final int code;

    BoltState(int code) {
        this.code = code;
    }

    public static Optional<BoltState> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 8: 运行测试确认通过**

```bash
mvn test -Dtest="StageTest,SubStateTest,BoltStateTest" -DfailIfNoTests=false
```
Expected: Tests run: 9, Failures: 0

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/tightening/constant/Stage.java src/main/java/com/tightening/constant/SubState.java src/main/java/com/tightening/constant/BoltState.java src/test/java/com/tightening/constant/StageTest.java src/test/java/com/tightening/constant/SubStateTest.java src/test/java/com/tightening/constant/BoltStateTest.java
git commit -m "feat: add Stage, SubState, and BoltState enums for Actor lifecycle"
```

---

### Task 2: LockMessage + InboundMessage 消息层次

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/LockMessage.java`
- Create: `src/main/java/com/tightening/lifecycle/message/InboundMessage.java`
- Create: `src/main/java/com/tightening/lifecycle/message/InboundCommand.java`
- Create: `src/main/java/com/tightening/lifecycle/message/DeviceEvent.java`
- Create: `src/main/java/com/tightening/lifecycle/message/EngineInternal.java`
- Create: `src/test/java/com/tightening/lifecycle/message/InboundMessageTest.java`

**Interfaces:**
- Consumes: `Stage`, `SubState` (from Task 1), `ProductMission`, `ProductSide`, `ProductBolt`, `BoltDeviceBinding` (entity), `TighteningData`, `CurveData` (entity)
- Produces: `LockMessage(String source, String reason)` record, `InboundMessage` sealed interface, `InboundCommand` (ActivateMission, AdvancePipeline, SelfLoop, InterruptMission), `DeviceEvent` (TighteningDataReceived, CurveDataReceived, DeviceDisconnected), `EngineInternal` (MonitorTick, Faulted)

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle.message;

import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.LockMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InboundMessage 消息层次")
class InboundMessageTest {

    @Test
    @DisplayName("ActivateMission record 创建并实现 InboundMessage")
    void shouldCreateActivateMission() {
        var msg = new InboundCommand.ActivateMission(
            new ProductMission().setId(1L).setName("test"),
            List.of(), List.of(), List.of()
        );
        assertThat(msg).isInstanceOf(InboundMessage.class);
        assertThat(msg.missionData().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("TighteningDataReceived record 创建")
    void shouldCreateTighteningDataReceived() {
        var data = new TighteningData().setTighteningId(100L);
        var msg = new DeviceEvent.TighteningDataReceived(data, 42L);
        assertThat(msg.deviceId()).isEqualTo(42L);
        assertThat(msg.data().getTighteningId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("MonitorTick 是单例")
    void monitorTickShouldBeSingleton() {
        var tick = new EngineInternal.MonitorTick();
        assertThat(tick).isInstanceOf(EngineInternal.class);
    }

    @Test
    @DisplayName("Faulted record 模式匹配")
    void faultedShouldSupportPatternMatching() {
        InboundMessage msg = new EngineInternal.Faulted("test error");
        if (msg instanceof EngineInternal.Faulted(String reason)) {
            assertThat(reason).isEqualTo("test error");
        } else {
            org.junit.jupiter.api.Assertions.fail("Expected Faulted");
        }
    }

    @Test
    @DisplayName("LockMessage isManual 判断")
    void lockMessageManualCheck() {
        var manualLock = new LockMessage("MANUAL_LOCK", "operator locked");
        var autoLock = new LockMessage("DEVICE_MONITOR", "device offline");

        assertThat(manualLock.isManual()).isTrue();
        assertThat(autoLock.isManual()).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest=InboundMessageTest -DfailIfNoTests=false -q
```
Expected: 编译失败 "cannot find symbol: class LockMessage" 或 "class InboundMessage"

- [ ] **Step 3: 实现 LockMessage.java**

```java
package com.tightening.lifecycle;

/**
 * 锁消息。source 决定优先级：MANUAL_LOCK / MANUAL_UNLOCK 最高，覆盖其他所有来源。
 */
public record LockMessage(String source, String reason) {
    public boolean isManual() {
        return "MANUAL_LOCK".equals(source) || "MANUAL_UNLOCK".equals(source);
    }
}
```

- [ ] **Step 4: 实现 InboundMessage.java**

```java
package com.tightening.lifecycle.message;

/** 所有 Actor inbox 消息的顶层标记接口 */
public sealed interface InboundMessage
    permits InboundCommand, DeviceEvent, EngineInternal {
}
```

- [ ] **Step 5: 实现 InboundCommand.java**

```java
package com.tightening.lifecycle.message;

import com.tightening.entity.BoltDeviceBinding;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.entity.ProductSide;

import java.util.List;

public sealed interface InboundCommand extends InboundMessage {

    /** 激活 Mission — 生命周期开始 */
    record ActivateMission(
        ProductMission missionData,
        List<ProductSide> sides,
        List<ProductBolt> bolts,
        List<BoltDeviceBinding> bindings
    ) implements InboundCommand {}

    /** 推进管道到下一个子状态 */
    record AdvancePipeline() implements InboundCommand {}

    /** 自循环 — 新的一轮开始 */
    record SelfLoop() implements InboundCommand {}

    /** 中断当前 Mission */
    record InterruptMission(String reason) implements InboundCommand {}
}
```

- [ ] **Step 6: 实现 DeviceEvent.java**

```java
package com.tightening.lifecycle.message;

import com.tightening.entity.CurveData;
import com.tightening.entity.TighteningData;

public sealed interface DeviceEvent extends InboundMessage {

    /** 拧紧数据到达（从 ToolAdapter 监听器转发） */
    record TighteningDataReceived(
        TighteningData data,
        long deviceId
    ) implements DeviceEvent {}

    /** 曲线数据到达 */
    record CurveDataReceived(
        CurveData data,
        long deviceId
    ) implements DeviceEvent {}

    /** 设备断线 */
    record DeviceDisconnected(long deviceId) implements DeviceEvent {}
}
```

- [ ] **Step 7: 实现 EngineInternal.java**

```java
package com.tightening.lifecycle.message;

public sealed interface EngineInternal extends InboundMessage {

    /** 定时监控 tick（由 ScheduledExecutorService 定时投递） */
    record MonitorTick() implements EngineInternal {}

    /** 引擎崩溃 / 故障 */
    record Faulted(String reason) implements EngineInternal {}
}
```

- [ ] **Step 8: 运行测试确认通过**

```bash
mvn test -Dtest=InboundMessageTest -DfailIfNoTests=false
```
Expected: Tests run: 5, Failures: 0

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/LockMessage.java src/main/java/com/tightening/lifecycle/message/InboundMessage.java src/main/java/com/tightening/lifecycle/message/InboundCommand.java src/main/java/com/tightening/lifecycle/message/DeviceEvent.java src/main/java/com/tightening/lifecycle/message/EngineInternal.java src/test/java/com/tightening/lifecycle/message/InboundMessageTest.java
git commit -m "feat: add sealed InboundMessage hierarchy and LockMessage record"
```

---

### Task 3: ContextCheckpoint + MissionContext

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/ContextCheckpoint.java`
- Create: `src/main/java/com/tightening/lifecycle/MissionContext.java`
- Create: `src/test/java/com/tightening/lifecycle/MissionContextTest.java`

**Interfaces:**
- Consumes: `Stage`, `SubState`, `BoltState` (Task 1), `LockMessage` (Task 2), `ProductMission`, `ProductBolt`, `MissionRecord`, `TighteningData`, `CurveData` (entity), `ITool` (Stage 1), `JudgmentResult` (Stage 1)
- Produces: `ContextCheckpoint` (@Value @Builder record for DB snapshot), `MissionContext` (builder pattern, 3-layer fields, convenience methods: `currentBolt()`, `hasMoreBolts()`, `totalBolts()`, `allBoltsCompleted()`)

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MissionContext 三层字段 + Builder")
class MissionContextTest {

    @Test
    @DisplayName("Builder 默认值正确")
    void shouldUseDefaultsForOptionalFields() {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L)
            .missionData(new ProductMission().setId(1L))
            .boltConfigs(List.of())
            .deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .build();

        assertThat(ctx.getCurrentStage()).isEqualTo(Stage.VALIDATION);
        assertThat(ctx.getCurrentSubState()).isEqualTo(SubState.IDLE);
        assertThat(ctx.getTighteningDataList()).isEmpty();
        assertThat(ctx.getExtras()).isEmpty();
        assertThat(ctx.getLockMessages()).isEmpty();
        assertThat(ctx.getPendingCurveData()).isEmpty();
    }

    @Test
    @DisplayName("currentBolt() 返回当前索引的螺栓")
    void currentBoltShouldReturnCorrectBolt() {
        ProductBolt b1 = new ProductBolt().setBoltSerialNum(1).setBoltName("Bolt1");
        ProductBolt b2 = new ProductBolt().setBoltSerialNum(2).setBoltName("Bolt2");
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission().setId(1L))
            .boltConfigs(List.of(b1, b2)).deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .currentBoltIndex(1).build();

        assertThat(ctx.currentBolt().getBoltName()).isEqualTo("Bolt2");
    }

    @Test
    @DisplayName("currentBolt() 索引越界返回 null")
    void currentBoltShouldReturnNullWhenOutOfRange() {
        MissionContext ctx = minimalContext();
        ctx.setCurrentBoltIndex(99);
        assertThat(ctx.currentBolt()).isNull();
    }

    @Test
    @DisplayName("hasMoreBolts() 正确判断")
    void hasMoreBoltsShouldWork() {
        ProductBolt b1 = new ProductBolt().setBoltSerialNum(1);
        ProductBolt b2 = new ProductBolt().setBoltSerialNum(2);
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission().setId(1L))
            .boltConfigs(List.of(b1, b2)).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();

        ctx.setCurrentBoltIndex(0);
        assertThat(ctx.hasMoreBolts()).isTrue();
        ctx.setCurrentBoltIndex(1);
        assertThat(ctx.hasMoreBolts()).isFalse();
    }

    @Test
    @DisplayName("allBoltsCompleted() 全部 JDGED_OK/JUDGED_NG 返回 true")
    void allBoltsCompletedShouldReturnTrueWhenAllDone() {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission().setId(1L))
            .boltConfigs(List.of(new ProductBolt(), new ProductBolt()))
            .deviceRegistry(Map.of()).shouldSelfLoop(false)
            .boltStates(new BoltState[]{BoltState.JUDGED_OK, BoltState.JUDGED_NG})
            .build();

        assertThat(ctx.allBoltsCompleted()).isTrue();
    }

    @Test
    @DisplayName("allBoltsCompleted() 有 PENDING 返回 false")
    void allBoltsCompletedShouldReturnFalseWhenPendingExists() {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission().setId(1L))
            .boltConfigs(List.of(new ProductBolt(), new ProductBolt()))
            .deviceRegistry(Map.of()).shouldSelfLoop(false)
            .boltStates(new BoltState[]{BoltState.JUDGED_OK, BoltState.PENDING})
            .build();

        assertThat(ctx.allBoltsCompleted()).isFalse();
    }

    @Test
    @DisplayName("可变字段 setter 正常工作")
    void mutableFieldsShouldBeSettable() {
        MissionContext ctx = minimalContext();
        ctx.setCurrentStage(Stage.OPERATION);
        ctx.setCurrentSubState(SubState.JUDGING);
        ctx.setCurrentBoltIndex(2);
        ctx.setInterruptRequested(true);

        assertThat(ctx.getCurrentStage()).isEqualTo(Stage.OPERATION);
        assertThat(ctx.getCurrentSubState()).isEqualTo(SubState.JUDGING);
        assertThat(ctx.getCurrentBoltIndex()).isEqualTo(2);
        assertThat(ctx.isInterruptRequested()).isTrue();
    }

    private static MissionContext minimalContext() {
        return MissionContext.builder()
            .productMissionId(1L)
            .missionData(new ProductMission().setId(1L))
            .boltConfigs(List.of())
            .deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .build();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest="MissionContextTest" -DfailIfNoTests=false -q
```
Expected: 编译失败 "cannot find symbol: class MissionContext"

- [ ] **Step 3: 实现 ContextCheckpoint.java**

```java
package com.tightening.lifecycle;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ContextCheckpoint {
    long missionId;
    long missionRecordId;
    Stage stage;
    SubState subState;
    int currentBoltIndex;
    int currentSideIndex;
    int completedBolts;
    boolean dataStored;
    String snapshotReason;
    long timestamp;
}
```

- [ ] **Step 4: 实现 MissionContext.java**

```java
package com.tightening.lifecycle;

import com.tightening.constant.BoltState;
import com.tightening.constant.DeviceType;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ITool;
import com.tightening.entity.*;
import com.tightening.judgment.JudgmentResult;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Builder
public class MissionContext {

    // ═══ 第一层：核心字段 ═══

    /** 不可变 — Workstation 就绪时注入 */
    private final Long productMissionId;
    private final ProductMission missionData;
    private final List<ProductBolt> boltConfigs;       // 按 boltSerialNum 排序（调用方保证）
    private final Map<Long, ITool> deviceRegistry;
    private final boolean shouldSelfLoop;

    /** 可变 — 引擎核心代码维护 */
    @Builder.Default @Setter private Stage currentStage = Stage.VALIDATION;
    @Builder.Default @Setter private SubState currentSubState = SubState.IDLE;
    @Builder.Default @Setter private BoltState[] boltStates = new BoltState[0];
    @Builder.Default @Setter private int currentBoltIndex = 0;
    @Builder.Default @Setter private int currentSideIndex = 0;
    @Builder.Default @Setter private MissionRecord missionRecord = null;
    @Builder.Default private final List<TighteningData> tighteningDataList = new ArrayList<>();
    @Builder.Default @Setter private volatile boolean interruptRequested = false;
    @Builder.Default @Setter private volatile String interruptReason = null;

    // ═══ 第二层：管道间传递数据 ═══

    @Builder.Default @Setter private TighteningData currentOperationData = null;
    @Builder.Default @Setter private DeviceType currentDeviceType = null;  // 由 handleTighteningData 从 deviceRegistry 解析
    @Builder.Default @Setter private TighteningData previousOperationData = null;
    @Builder.Default private final List<CurveData> pendingCurveData = new ArrayList<>();
    @Builder.Default @Setter private JudgmentResult judgeResult = null;
    @Builder.Default @Setter private int tighteningStatus = 0;
    @Builder.Default private final Set<LockMessage> lockMessages = new LinkedHashSet<>();

    // ═══ 第三层：Capability 间临时数据 ═══

    @Builder.Default private final Map<String, Object> extras = new HashMap<>();

    // ═══ 崩溃恢复 ═══

    @Builder.Default @Setter private ContextCheckpoint checkpoint = null;

    // ═══ 便捷方法 ═══

    public ProductBolt currentBolt() {
        if (boltConfigs == null || currentBoltIndex < 0 || currentBoltIndex >= boltConfigs.size()) {
            return null;
        }
        return boltConfigs.get(currentBoltIndex);
    }

    public boolean hasMoreBolts() {
        return boltConfigs != null && currentBoltIndex < boltConfigs.size() - 1;
    }

    public int totalBolts() {
        return boltConfigs != null ? boltConfigs.size() : 0;
    }

    public boolean allBoltsCompleted() {
        return boltStates != null && Arrays.stream(boltStates)
            .allMatch(s -> s == BoltState.JUDGED_OK || s == BoltState.JUDGED_NG);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

```bash
mvn test -Dtest="MissionContextTest" -DfailIfNoTests=false
```
Expected: Tests run: 7, Failures: 0

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/ContextCheckpoint.java src/main/java/com/tightening/lifecycle/MissionContext.java src/test/java/com/tightening/lifecycle/MissionContextTest.java
git commit -m "feat: add MissionContext with 3-layer fields and ContextCheckpoint"
```

---

### Task 4: Capability 接口 + 枚举

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/Capability.java`
- Create: `src/main/java/com/tightening/lifecycle/capability/CapabilityResult.java`
- Create: `src/main/java/com/tightening/lifecycle/capability/ErrorAction.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/CapabilityResultTest.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/ErrorActionTest.java`

**Interfaces:**
- Consumes: `Stage`, `SubState` (Task 1), `MissionContext` (Task 3)
- Produces: `Capability` (id, stage, subState, priority, precondition, execute, onError), `CapabilityResult` (Pass, Fail, Skip, Interrupt), `ErrorAction` (FAIL_CAPABILITY, FAIL_STAGE, RETRY_LATER, INTERRUPT)

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle.capability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CapabilityResult 枚举")
class CapabilityResultTest {

    @Test
    @DisplayName("包含四个值")
    void shouldHaveFourValues() {
        assertThat(CapabilityResult.values()).hasSize(4);
    }

    @Test
    @DisplayName("包含 Pass, Fail, Skip, Interrupt")
    void shouldContainAllExpectedValues() {
        assertThat(CapabilityResult.valueOf("Pass")).isNotNull();
        assertThat(CapabilityResult.valueOf("Fail")).isNotNull();
        assertThat(CapabilityResult.valueOf("Skip")).isNotNull();
        assertThat(CapabilityResult.valueOf("Interrupt")).isNotNull();
    }
}

@DisplayName("ErrorAction 枚举")
class ErrorActionTest {

    @Test
    @DisplayName("包含四个值")
    void shouldHaveFourValues() {
        assertThat(ErrorAction.values()).hasSize(4);
    }

    @Test
    @DisplayName("default onError 返回 FAIL_STAGE")
    void defaultOnErrorShouldReturnFailStage() {
        Capability cap = mock(Capability.class);
        when(cap.onError(any(), any())).thenCallRealMethod();
        assertThat(cap.onError(null, new RuntimeException()))
            .isEqualTo(ErrorAction.FAIL_STAGE);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest="CapabilityResultTest,ErrorActionTest" -DfailIfNoTests=false -q
```
Expected: 编译失败 "cannot find symbol"

- [ ] **Step 3: 实现 CapabilityResult.java**

```java
package com.tightening.lifecycle.capability;

public enum CapabilityResult {
    Pass,
    Fail,
    Skip,
    Interrupt
}
```

- [ ] **Step 4: 实现 ErrorAction.java**

```java
package com.tightening.lifecycle.capability;

public enum ErrorAction {
    FAIL_CAPABILITY,
    FAIL_STAGE,
    RETRY_LATER,
    INTERRUPT
}
```

- [ ] **Step 5: 实现 Capability.java**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;

public interface Capability {
    String id();
    Stage stage();
    SubState subState();
    int priority();

    default boolean precondition(MissionContext ctx) {
        return true;
    }

    CapabilityResult execute(MissionContext ctx);

    default ErrorAction onError(MissionContext ctx, Exception e) {
        return ErrorAction.FAIL_STAGE;
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

```bash
mvn test -Dtest="CapabilityResultTest,ErrorActionTest" -DfailIfNoTests=false
```
Expected: Tests run: 3, Failures: 0

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/Capability.java src/main/java/com/tightening/lifecycle/capability/CapabilityResult.java src/main/java/com/tightening/lifecycle/capability/ErrorAction.java src/test/java/com/tightening/lifecycle/capability/CapabilityResultTest.java src/test/java/com/tightening/lifecycle/capability/ErrorActionTest.java
git commit -m "feat: add Capability interface with CapabilityResult and ErrorAction enums"
```

---

### Task 5: PipelineDefinition

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/PipelineDefinition.java`
- Create: `src/test/java/com/tightening/lifecycle/PipelineDefinitionTest.java`

**Interfaces:**
- Consumes: `Stage`, `SubState` (Task 1), `Capability` (Task 4)
- Produces: `PipelineDefinition` — `registerCapability(Capability)`, `registerCapabilities(Collection)`, `registerStage(Stage, List<StageEntry>)`, `registerTransition(...)`, `getCapabilities(Stage, SubState)`, `getNext(Stage, SubState)`, `isWaitingPoint(Stage, SubState)`, `createDefault()`

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.capability.Capability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PipelineDefinition 管道路由表")
class PipelineDefinitionTest {

    private PipelineDefinition pd;

    @BeforeEach
    void setUp() {
        pd = PipelineDefinition.createDefault();
    }

    @Test
    @DisplayName("未注册 (Stage,SubState) 返回空 Capability 列表")
    void shouldReturnEmptyCapabilitiesForUnregisteredState() {
        assertThat(pd.getCapabilities(Stage.VALIDATION, SubState.VALIDATING)).isEmpty();
    }

    @Test
    @DisplayName("VALIDATION 推进到 ACTIVATION")
    void shouldTransitionFromValidationToActivation() {
        PipelineDefinition.Transition t = pd.getNext(Stage.VALIDATION, SubState.VALIDATING);
        assertThat(t.nextStage()).isEqualTo(Stage.ACTIVATION);
        assertThat(t.nextSubState()).isEqualTo(SubState.PREPARING);
    }

    @Test
    @DisplayName("ACTIVATION/ACTIVATING 推进到 OPERATION/SWITCH_BOLT")
    void shouldTransitionFromActivationToOperation() {
        PipelineDefinition.Transition t = pd.getNext(Stage.ACTIVATION, SubState.ACTIVATING);
        assertThat(t.nextStage()).isEqualTo(Stage.OPERATION);
        assertThat(t.nextSubState()).isEqualTo(SubState.SWITCH_BOLT);
    }

    @Test
    @DisplayName("OPERATION 内 TIGHTENING_RECEIVED → JUDGING")
    void shouldTransitionWithinOperation() {
        PipelineDefinition.Transition t = pd.getNext(Stage.OPERATION, SubState.TIGHTENING_RECEIVED);
        assertThat(t.nextSubState()).isEqualTo(SubState.JUDGING);
    }

    @Test
    @DisplayName("ADVANCING 循环回 SWITCH_BOLT")
    void advancingShouldLoopToSwitchBolt() {
        PipelineDefinition.Transition t = pd.getNext(Stage.OPERATION, SubState.ADVANCING);
        assertThat(t.nextStage()).isEqualTo(Stage.OPERATION);
        assertThat(t.nextSubState()).isEqualTo(SubState.SWITCH_BOLT);
    }

    @Test
    @DisplayName("SWITCH_BOLT 不是等待点")
    void switchBoltShouldNotBeWaitingPoint() {
        assertThat(pd.isWaitingPoint(Stage.OPERATION, SubState.SWITCH_BOLT)).isFalse();
    }

    @Test
    @DisplayName("TIGHTENING_RECEIVED 是等待点")
    void tighteningReceivedShouldBeWaitingPoint() {
        assertThat(pd.isWaitingPoint(Stage.OPERATION, SubState.TIGHTENING_RECEIVED)).isTrue();
    }

    @Test
    @DisplayName("ADVANCING 不是等待点")
    void advancingShouldNotBeWaitingPoint() {
        assertThat(pd.isWaitingPoint(Stage.OPERATION, SubState.ADVANCING)).isFalse();
    }

    @Test
    @DisplayName("JUDGING 不是等待点")
    void judgingShouldNotBeWaitingPoint() {
        assertThat(pd.isWaitingPoint(Stage.OPERATION, SubState.JUDGING)).isFalse();
    }

    @Test
    @DisplayName("到达终点后 getNext 返回自身")
    void shouldReturnSameWhenAtTerminal() {
        PipelineDefinition.Transition t = pd.getNext(Stage.FINALIZATION, SubState.EXPORTING);
        assertThat(t.nextStage()).isEqualTo(Stage.FINALIZATION);
        assertThat(t.nextSubState()).isEqualTo(SubState.EXPORTING);
    }

    @Test
    @DisplayName("registerCapability 注册后可查询")
    void shouldRegisterCapability() {
        Capability mockCap = mock(Capability.class);
        when(mockCap.stage()).thenReturn(Stage.OPERATION);
        when(mockCap.subState()).thenReturn(SubState.JUDGING);
        when(mockCap.id()).thenReturn("test");
        when(mockCap.priority()).thenReturn(0);

        PipelineDefinition custom = new PipelineDefinition();
        custom.registerCapability(mockCap);
        custom.sortByPriority();

        assertThat(custom.getCapabilities(Stage.OPERATION, SubState.JUDGING))
            .containsExactly(mockCap);
    }

    @Test
    @DisplayName("registerCapabilities 批量注册")
    void shouldRegisterCapabilitiesInBatch() {
        Capability c1 = mock(Capability.class);
        when(c1.stage()).thenReturn(Stage.OPERATION);
        when(c1.subState()).thenReturn(SubState.JUDGING);
        when(c1.id()).thenReturn("c1");
        when(c1.priority()).thenReturn(1);

        Capability c2 = mock(Capability.class);
        when(c2.stage()).thenReturn(Stage.OPERATION);
        when(c2.subState()).thenReturn(SubState.JUDGING);
        when(c2.id()).thenReturn("c2");
        when(c2.priority()).thenReturn(0);

        PipelineDefinition custom = new PipelineDefinition();
        custom.registerCapabilities(List.of(c1, c2));
        custom.sortByPriority();

        assertThat(custom.getCapabilities(Stage.OPERATION, SubState.JUDGING))
            .containsExactly(c2, c1);  // sorted by priority
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest="PipelineDefinitionTest" -DfailIfNoTests=false -q
```
Expected: 编译失败 "cannot find symbol: class PipelineDefinition"

- [ ] **Step 3: 实现 PipelineDefinition.java**

```java
package com.tightening.lifecycle;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.capability.Capability;
import lombok.Getter;

import java.util.*;

public class PipelineDefinition {

    private final Map<Stage, List<StageEntry>> orderedStages = new LinkedHashMap<>();
    private final Map<Stage, Map<SubState, List<Capability>>> caps = new HashMap<>();
    private final Map<Stage, Map<SubState, Transition>> transitions = new HashMap<>();

    @Getter
    public static class StageEntry {
        private final Stage stage;
        private final SubState subState;
        private final boolean waitingPoint;

        public StageEntry(Stage stage, SubState subState, boolean waitingPoint) {
            this.stage = stage;
            this.subState = subState;
            this.waitingPoint = waitingPoint;
        }
    }

    public record Transition(Stage nextStage, SubState nextSubState) {}

    public PipelineDefinition registerCapability(Capability cap) {
        caps.computeIfAbsent(cap.stage(), k -> new HashMap<>())
            .computeIfAbsent(cap.subState(), k -> new ArrayList<>())
            .add(cap);
        return this;
    }

    public PipelineDefinition registerCapabilities(Collection<Capability> capabilities) {
        capabilities.forEach(this::registerCapability);
        return this;
    }

    public PipelineDefinition sortByPriority() {
        caps.values().forEach(stageMap ->
            stageMap.values().forEach(list ->
                list.sort(Comparator.comparingInt(Capability::priority))
            )
        );
        return this;
    }

    public PipelineDefinition registerStage(Stage stage, List<StageEntry> entries) {
        orderedStages.put(stage, entries);
        return this;
    }

    public PipelineDefinition registerTransition(Stage fromStage, SubState fromSubState,
                                                  Stage toStage, SubState toSubState) {
        transitions.computeIfAbsent(fromStage, k -> new HashMap<>())
            .put(fromSubState, new Transition(toStage, toSubState));
        return this;
    }

    public List<Capability> getCapabilities(Stage stage, SubState subState) {
        return caps.getOrDefault(stage, Map.of())
            .getOrDefault(subState, List.of());
    }

    public Transition getNext(Stage stage, SubState subState) {
        Map<SubState, Transition> stageTransitions = transitions.get(stage);
        if (stageTransitions != null) {
            Transition t = stageTransitions.get(subState);
            if (t != null) return t;
        }
        List<StageEntry> entries = orderedStages.get(stage);
        if (entries != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).subState() == subState && i + 1 < entries.size()) {
                    StageEntry next = entries.get(i + 1);
                    return new Transition(stage, next.subState());
                }
            }
        }
        Stage[] stageOrder = Stage.values();
        int currentIdx = Arrays.asList(stageOrder).indexOf(stage);
        if (currentIdx >= 0 && currentIdx + 1 < stageOrder.length) {
            Stage nextStage = stageOrder[currentIdx + 1];
            List<StageEntry> nextEntries = orderedStages.get(nextStage);
            if (nextEntries != null && !nextEntries.isEmpty()) {
                return new Transition(nextStage, nextEntries.get(0).subState());
            }
        }
        return new Transition(stage, subState);  // terminal
    }

    public boolean isWaitingPoint(Stage stage, SubState subState) {
        List<StageEntry> entries = orderedStages.get(stage);
        if (entries != null) {
            return entries.stream()
                .filter(e -> e.subState() == subState)
                .anyMatch(e -> e.waitingPoint);
        }
        return false;
    }

    public static PipelineDefinition createDefault() {
        PipelineDefinition pd = new PipelineDefinition();

        pd.registerStage(Stage.VALIDATION, List.of(
            new StageEntry(Stage.VALIDATION, SubState.VALIDATING, false)
        ));
        pd.registerStage(Stage.ACTIVATION, List.of(
            new StageEntry(Stage.ACTIVATION, SubState.PREPARING, false),
            new StageEntry(Stage.ACTIVATION, SubState.ACTIVATING, false)
        ));
        pd.registerStage(Stage.OPERATION, List.of(
            new StageEntry(Stage.OPERATION, SubState.SWITCH_BOLT, false),
            new StageEntry(Stage.OPERATION, SubState.TIGHTENING_RECEIVED, true),
            new StageEntry(Stage.OPERATION, SubState.JUDGING, false),
            new StageEntry(Stage.OPERATION, SubState.STORING, false),
            new StageEntry(Stage.OPERATION, SubState.ADVANCING, false)
        ));
        pd.registerStage(Stage.FINALIZATION, List.of(
            new StageEntry(Stage.FINALIZATION, SubState.CLEANING_TASKS, false),
            new StageEntry(Stage.FINALIZATION, SubState.LOCKING_TOOLS, false),
            new StageEntry(Stage.FINALIZATION, SubState.RESETTING_STATE, false),
            new StageEntry(Stage.FINALIZATION, SubState.EXPORTING, false)
        ));

        pd.registerTransition(Stage.VALIDATION, SubState.VALIDATING,
            Stage.ACTIVATION, SubState.PREPARING);
        pd.registerTransition(Stage.ACTIVATION, SubState.ACTIVATING,
            Stage.OPERATION, SubState.SWITCH_BOLT);
        pd.registerTransition(Stage.OPERATION, SubState.ADVANCING,
            Stage.OPERATION, SubState.SWITCH_BOLT);

        return pd;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest="PipelineDefinitionTest" -DfailIfNoTests=false
```
Expected: Tests run: 10, Failures: 0

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/PipelineDefinition.java src/test/java/com/tightening/lifecycle/PipelineDefinitionTest.java
git commit -m "feat: add PipelineDefinition with stage routing and default pipeline"
```

---

### Task 6: LifecycleEngine — Actor 主循环

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/LifecycleEngine.java`
- Create: `src/test/java/com/tightening/lifecycle/LifecycleEngineTest.java`

**Interfaces:**
- Consumes: Tasks 1-5 (all enums, messages, context, capability interface, pipeline)
- Produces: `LifecycleEngine` — `start(MissionContext)`, `postMessage(InboundMessage)`, `interrupt(String)`, `isAlive()`, `getContext()`, `registerHandler(Class, MessageHandler)`, `onFaulted(Consumer)`, `onCompleted(Consumer)`, `startMonitorTicks()`, `stopMonitorTicks()`. Inner `MessageHandler` @FunctionalInterface: `void handle(InboundMessage msg, MissionContext ctx, LifecycleEngine engine)`

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.capability.Capability;
import com.tightening.lifecycle.capability.CapabilityResult;
import com.tightening.lifecycle.message.*;
import com.tightening.service.MissionRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LifecycleEngine Actor 主循环")
class LifecycleEngineTest {

    @Mock
    private MissionRecordService missionRecordService;

    private PipelineDefinition pd;
    private LifecycleEngine engine;

    @BeforeEach
    void setUp() {
        pd = PipelineDefinition.createDefault();
        engine = new LifecycleEngine(pd, missionRecordService, List.of(), List.of());
    }

    @Test
    @DisplayName("start() 启动 Actor 线程并设置 alive=true")
    void shouldStartActorThread() throws InterruptedException {
        MissionContext ctx = minimalContext();
        engine.start(ctx);
        Thread.sleep(200);
        assertThat(engine.isAlive()).isTrue();
        engine.postMessage(new EngineInternal.Faulted("stop"));
        Thread.sleep(100);
    }

    @Test
    @DisplayName("postMessage 将消息投递到 inbox")
    void shouldDispatchMessageToRegisteredHandler() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        engine.registerHandler(InboundCommand.SelfLoop.class, (msg, ctx, eng) -> latch.countDown());

        MissionContext ctx = minimalContext();
        engine.start(ctx);
        engine.postMessage(new InboundCommand.SelfLoop());

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        engine.postMessage(new EngineInternal.Faulted("stop"));
    }

    @Test
    @DisplayName("Faulted 消息触发 onFaulted 回调")
    void shouldHandleFaultedMessage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        String[] reason = new String[1];
        engine.onFaulted(r -> { reason[0] = r; latch.countDown(); });

        MissionContext ctx = minimalContext();
        engine.start(ctx);
        engine.postMessage(new EngineInternal.Faulted("test crash"));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(reason[0]).isEqualTo("test crash");
    }

    @Test
    @DisplayName("interrupt() 在 FINALIZATION 阶段被忽略")
    void shouldNotInterruptDuringFinalization() throws InterruptedException {
        MissionContext ctx = minimalContext();
        ctx.setCurrentStage(Stage.FINALIZATION);
        engine.start(ctx);
        Thread.sleep(100);
        engine.interrupt("test");
        assertThat(ctx.isInterruptRequested()).isFalse();
        engine.postMessage(new EngineInternal.Faulted("stop"));
    }

    @Test
    @DisplayName("interrupt() 在非 FINALIZATION 阶段设置标志")
    void shouldInterruptOutsideFinalization() throws InterruptedException {
        MissionContext ctx = minimalContext();
        ctx.setCurrentStage(Stage.OPERATION);
        engine.start(ctx);
        Thread.sleep(100);
        engine.interrupt("user requested");
        Thread.sleep(100);
        assertThat(ctx.isInterruptRequested()).isTrue();
        engine.postMessage(new EngineInternal.Faulted("stop"));
    }

    @Test
    @DisplayName("HandleActivateMission 初始化 BoltStates 并推进管道")
    void shouldActivateMissionAndAdvancePipeline() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Capability cap = mock(Capability.class);
        when(cap.id()).thenReturn("test");
        when(cap.stage()).thenReturn(Stage.ACTIVATION);
        when(cap.subState()).thenReturn(SubState.PREPARING);
        when(cap.priority()).thenReturn(0);
        when(cap.precondition(any())).thenReturn(true);
        when(cap.execute(any())).thenAnswer(inv -> {
            latch.countDown();
            return CapabilityResult.Pass;
        });

        PipelineDefinition customPd = PipelineDefinition.createDefault();
        customPd.registerCapability(cap).sortByPriority();
        engine = new LifecycleEngine(customPd, missionRecordService, List.of(cap), List.of());

        ProductBolt bolt = new ProductBolt().setBoltSerialNum(1);
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L)
            .missionData(new ProductMission().setId(1L))
            .boltConfigs(List.of(bolt))
            .deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .build();

        engine.start(ctx);
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        engine.postMessage(new EngineInternal.Faulted("stop"));
    }

    @Test
    @DisplayName("handleActorCrash 保存 checkpoint 到 DB")
    void shouldSaveCheckpointOnCrash() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Capability cap = mock(Capability.class);
        when(cap.id()).thenReturn("crash-test");
        when(cap.stage()).thenReturn(Stage.OPERATION);
        when(cap.subState()).thenReturn(SubState.STORING);
        when(cap.priority()).thenReturn(0);
        when(cap.precondition(any())).thenReturn(true);
        when(cap.execute(any())).thenThrow(new RuntimeException("boom"));

        PipelineDefinition customPd = PipelineDefinition.createDefault();
        customPd.registerCapability(cap).sortByPriority();
        engine = new LifecycleEngine(customPd, missionRecordService, List.of(cap), List.of());
        engine.onFaulted(r -> latch.countDown());

        MissionRecord record = new MissionRecord().setId(42L);
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L)
            .missionData(new ProductMission().setId(1L))
            .boltConfigs(List.of(new ProductBolt().setBoltSerialNum(1)))
            .deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .missionRecord(record)
            .currentStage(Stage.OPERATION)
            .currentSubState(SubState.STORING)
            .build();

        engine.start(ctx);
        Thread.sleep(100);
        engine.postMessage(new InboundCommand.AdvancePipeline());

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        verify(missionRecordService, timeout(2000))
            .updateSnapshot(eq(42L), anyString());
    }

    private static MissionContext minimalContext() {
        return MissionContext.builder()
            .productMissionId(1L)
            .missionData(new ProductMission().setId(1L))
            .boltConfigs(List.of())
            .deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .build();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest="LifecycleEngineTest" -DfailIfNoTests=false -q
```
Expected: 编译失败 "cannot find symbol: class LifecycleEngine"

- [ ] **Step 3: 实现 LifecycleEngine.java**

```java
package com.tightening.lifecycle;

import com.tightening.constant.BoltState;
import com.tightening.constant.MissionResult;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ITool;
import com.tightening.entity.MissionRecord;
import com.tightening.lifecycle.capability.Capability;
import com.tightening.lifecycle.capability.CapabilityResult;
import com.tightening.lifecycle.capability.ErrorAction;
import com.tightening.lifecycle.message.*;
import com.tightening.lifecycle.monitor.PersistentMonitor;
import com.tightening.service.MissionRecordService;
import com.tightening.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
public class LifecycleEngine {

    @FunctionalInterface
    public interface MessageHandler {
        void handle(InboundMessage msg, MissionContext ctx, LifecycleEngine engine);
    }

    private final BlockingQueue<InboundMessage> inbox = new LinkedBlockingQueue<>();
    private final Map<Class<?>, MessageHandler> handlers = new HashMap<>();

    private MissionContext context;
    private final PipelineDefinition pipeline;
    private final MissionRecordService missionRecordService;
    private final List<PersistentMonitor> monitors;

    private volatile boolean alive = false;
    private Thread actorThread;
    private final ScheduledExecutorService tickScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lifecycle-tick");
        t.setDaemon(true);
        return t;
    });

    private Consumer<String> onFaulted;
    private Consumer<Long> onCompleted;

    public LifecycleEngine(PipelineDefinition pipeline, MissionRecordService missionRecordService,
                           List<Capability> capabilities, List<PersistentMonitor> monitors) {
        this.pipeline = pipeline;
        this.missionRecordService = missionRecordService;
        this.monitors = monitors != null ? monitors : List.of();
        pipeline.registerCapabilities(capabilities).sortByPriority();
        registerDefaultHandlers();
    }

    public void registerHandler(Class<?> msgType, MessageHandler handler) {
        handlers.put(msgType, handler);
    }

    public void onFaulted(Consumer<String> callback) { this.onFaulted = callback; }
    public void onCompleted(Consumer<Long> callback) { this.onCompleted = callback; }

    private void registerDefaultHandlers() {
        registerHandler(InboundCommand.ActivateMission.class, this::handleActivateMission);
        registerHandler(InboundCommand.AdvancePipeline.class, this::handleAdvancePipeline);
        registerHandler(DeviceEvent.TighteningDataReceived.class, this::handleTighteningData);
        registerHandler(EngineInternal.Faulted.class, this::handleFaulted);
        registerHandler(InboundCommand.InterruptMission.class, this::handleInterrupt);
        registerHandler(EngineInternal.MonitorTick.class, this::handleMonitorTick);
    }

    private final Map<PersistentMonitor, Long> monitorLastRun = new ConcurrentHashMap<>();

    void handleMonitorTick(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
        if (ctx == null) return;
        long now = System.currentTimeMillis();
        for (PersistentMonitor m : monitors) {
            long last = monitorLastRun.getOrDefault(m, 0L);
            if (now - last < m.intervalMs()) continue;
            monitorLastRun.put(m, now);
            try {
                m.execute(ctx);
            } catch (Exception e) {
                log.warn("Monitor {} error", m.getClass().getSimpleName(), e);
            }
        }
    }

    // === Actor 主循环 ===

    private void actorLoop() {
        while (alive) {
            InboundMessage msg;
            try {
                msg = inbox.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                MessageHandler handler = handlers.get(msg.getClass());
                if (handler != null) {
                    handler.handle(msg, context, this);
                } else {
                    log.warn("Unknown message type: {}", msg.getClass().getSimpleName());
                }
            } catch (Exception e) {
                handleActorCrash(e, msg);
            }
        }
    }

    // === 消息 Handler ===

    void handleActivateMission(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
        var cmd = (InboundCommand.ActivateMission) msg;
        log.info("Engine activating mission: {}", cmd.missionData().getId());

        int boltCount = cmd.bolts().size();
        BoltState[] states = new BoltState[boltCount];
        Arrays.fill(states, BoltState.PENDING);

        ctx.setCurrentStage(Stage.VALIDATION);
        ctx.setCurrentSubState(SubState.VALIDATING);
        ctx.setBoltStates(states);

        postMessage(new InboundCommand.AdvancePipeline());
    }

    void handleAdvancePipeline(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
        if (ctx == null) return;
        advancePipeline();
    }

    void handleTighteningData(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
        if (ctx == null) return;
        var event = (DeviceEvent.TighteningDataReceived) msg;
        log.debug("Tightening data: device={}, tighteningId={}",
            event.deviceId(), event.data().getTighteningId());

        ctx.setCurrentOperationData(event.data());

        // 解析设备类型供 ExecuteJudgment 使用
        ITool tool = ctx.getDeviceRegistry().get(event.deviceId());
        if (tool != null) {
            ctx.setCurrentDeviceType(tool.type());
        }

        if (ctx.getCurrentBoltIndex() >= 0 && ctx.getCurrentBoltIndex() < ctx.getBoltStates().length) {
            ctx.getBoltStates()[ctx.getCurrentBoltIndex()] = BoltState.TIGHTENING;
        }

        // 从当前 TIGHTENING_RECEIVED 等待点开始推进管道
        advancePipeline();
    }

    void handleFaulted(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
        var fault = (EngineInternal.Faulted) msg;
        log.error("Engine faulted: {}", fault.reason());
        if (ctx != null) {
            ctx.setCurrentStage(Stage.FINALIZATION);
            ctx.setCurrentSubState(SubState.FAULTED);
        }
        if (onFaulted != null) onFaulted.accept(fault.reason());
        shutdown();
    }

    void handleInterrupt(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
        var cmd = (InboundCommand.InterruptMission) msg;
        log.warn("Engine interrupted: {}", cmd.reason());
        if (ctx != null) {
            ctx.setInterruptRequested(true);
            ctx.setInterruptReason(cmd.reason());
        }
    }

    // === 管道推进 ===

    private void advancePipeline() {
        if (context == null) return;
        if (context.isInterruptRequested()) {
            postMessage(new EngineInternal.Faulted("Interrupted: " + context.getInterruptReason()));
            return;
        }

        Stage stage = context.getCurrentStage();
        SubState subState = context.getCurrentSubState();

        log.debug("Advancing: stage={}, subState={}, bolt={}/{}",
            stage, subState, context.getCurrentBoltIndex() + 1, context.totalBolts());

        List<Capability> caps = pipeline.getCapabilities(stage, subState);
        for (Capability cap : caps) {
            if (!cap.precondition(context)) {
                log.debug("Capability {} precondition not met, skipping", cap.id());
                continue;
            }
            try {
                CapabilityResult result = cap.execute(context);
                switch (result) {
                    case Pass -> {}
                    case Fail -> { handleStageFailure(cap); return; }
                    case Skip -> log.debug("Capability {} skipped", cap.id());
                    case Interrupt -> {
                        postMessage(new EngineInternal.Faulted("Interrupted by: " + cap.id()));
                        return;
                    }
                }
            } catch (Exception e) {
                ErrorAction action = cap.onError(context, e);
                log.error("Capability {} threw: {}", cap.id(), e.getMessage(), e);
                switch (action) {
                    case FAIL_CAPABILITY, FAIL_STAGE -> { handleStageFailure(cap); return; }
                    case RETRY_LATER -> {
                        tickScheduler.schedule(
                            () -> inbox.offer(new InboundCommand.AdvancePipeline()),
                            100, TimeUnit.MILLISECONDS);
                        return;
                    }
                    case INTERRUPT -> {
                        postMessage(new EngineInternal.Faulted("Interrupted by error in: " + cap.id()));
                        return;
                    }
                }
            }
        }

        // Capability 可能重定向管道（如 AdvanceBolt → FINALIZATION）
        if (context.getCurrentStage() != stage || context.getCurrentSubState() != subState) {
            stage = context.getCurrentStage();
            subState = context.getCurrentSubState();
            log.debug("Pipeline redirected by Capability to: {}/{}", stage, subState);
            saveCheckpoint("PostCapRedirect-" + stage + "/" + subState);
            if (!pipeline.isWaitingPoint(stage, subState)) {
                postMessage(new InboundCommand.AdvancePipeline());
            }
            return;
        }

        // 推进到下一子状态
        PipelineDefinition.Transition next = pipeline.getNext(stage, subState);

        // 终点检测：不变则终止
        if (next.nextStage() == stage && next.nextSubState() == subState) {
            log.info("Pipeline reached terminal state: {}/{}", stage, subState);
            if (onCompleted != null && context.getMissionRecord() != null) {
                onCompleted.accept(context.getMissionRecord().getId());
            }
            shutdown();
            return;
        }

        context.setCurrentStage(next.nextStage());
        context.setCurrentSubState(next.nextSubState());
        log.debug("Advanced to {}/{}", next.nextStage(), next.nextSubState());

        saveCheckpoint("Post-" + stage + "/" + subState);

        if (!pipeline.isWaitingPoint(next.nextStage(), next.nextSubState())) {
            postMessage(new InboundCommand.AdvancePipeline());
        } else {
            log.debug("Pipeline waiting at: {}/{}", next.nextStage(), next.nextSubState());
        }
    }

    private void handleStageFailure(Capability failedCap) {
        log.error("Stage failure at: {}", failedCap.id());
        context.setCurrentStage(Stage.FINALIZATION);
        context.setCurrentSubState(SubState.FAULTED);
        if (context.getMissionRecord() != null && context.getMissionRecord().getId() != null) {
            missionRecordService.markFaulted(
                context.getMissionRecord().getId(),
                "Capability failed: " + failedCap.id());
        }
        saveCheckpoint("StageFailure:" + failedCap.id());
        if (onFaulted != null) onFaulted.accept("Capability failed: " + failedCap.id());
        shutdown();
    }

    // === 崩溃恢复 ===

    private void handleActorCrash(Exception e, InboundMessage msg) {
        log.error("Actor thread crashed, message={}", msg, e);
        if (context == null) return;
        if (context.getMissionRecord() != null && context.getMissionRecord().getId() != null) {
            try {
                missionRecordService.markFaulted(
                    context.getMissionRecord().getId(), e.getMessage());
            } catch (Exception markEx) {
                log.error("Failed to mark faulted", markEx);
            }
        }
        try {
            saveCheckpoint("Crash:" + (msg != null ? msg.getClass().getSimpleName() : "unknown"));
        } catch (Exception cpEx) {
            log.error("Failed to save checkpoint", cpEx);
        }
        inbox.offer(new EngineInternal.Faulted(e.getMessage()));
    }

    private void saveCheckpoint(String reason) {
        if (context == null || context.getMissionRecord() == null) return;
        ContextCheckpoint cp = ContextCheckpoint.builder()
            .missionId(context.getProductMissionId())
            .missionRecordId(context.getMissionRecord().getId())
            .stage(context.getCurrentStage())
            .subState(context.getCurrentSubState())
            .currentBoltIndex(context.getCurrentBoltIndex())
            .currentSideIndex(context.getCurrentSideIndex())
            .completedBolts((int) Arrays.stream(context.getBoltStates())
                .filter(s -> s == BoltState.JUDGED_OK || s == BoltState.JUDGED_NG).count())
            .dataStored(context.getCurrentOperationData() == null
                || context.getCurrentOperationData().getMissionRecordId() != null)
            .snapshotReason(reason)
            .timestamp(System.currentTimeMillis())
            .build();
        context.setCheckpoint(cp);
        // 持久化到 DB
        String json = JsonUtils.toJson(cp);
        missionRecordService.updateSnapshot(context.getMissionRecord().getId(), json);
    }

    // === MonitorTick ===

    public void startMonitorTicks() {
        tickScheduler.scheduleAtFixedRate(
            () -> inbox.offer(new EngineInternal.MonitorTick()),
            0, 50, TimeUnit.MILLISECONDS);
    }

    public void stopMonitorTicks() {
        tickScheduler.shutdownNow();
    }

    // === 生命周期控制 ===

    public void start(MissionContext ctx) {
        this.context = ctx;
        this.alive = true;
        actorThread = new Thread(this::actorLoop, "lifecycle-engine-" + ctx.getProductMissionId());
        actorThread.setUncaughtExceptionHandler((t, throwable) -> {
            log.error("Actor thread uncaught exception", throwable);
            // 线程即将死亡，直接执行崩溃逻辑并 shutdown，不投递到 inbox
            if (context != null && context.getMissionRecord() != null && context.getMissionRecord().getId() != null) {
                try { missionRecordService.markFaulted(context.getMissionRecord().getId(), throwable.getMessage()); }
                catch (Exception ex) { log.error("Failed to mark faulted", ex); }
            }
            try { saveCheckpoint("Uncaught:" + throwable.getClass().getSimpleName()); }
            catch (Exception ex) { log.error("Failed to save checkpoint", ex); }
            if (onFaulted != null) onFaulted.accept(throwable.getMessage());
            shutdown();
        });
        actorThread.start();
        inbox.offer(new InboundCommand.ActivateMission(
            ctx.getMissionData(), List.of(), ctx.getBoltConfigs(), List.of()));
    }

    public void postMessage(InboundMessage msg) {
        if (alive) inbox.offer(msg);
    }

    public void interrupt(String reason) {
        if (context != null && context.getCurrentStage() == Stage.FINALIZATION) return;
        postMessage(new InboundCommand.InterruptMission(reason));
    }

    public boolean isAlive() { return alive; }

    public MissionContext getContext() { return context; }

    private void shutdown() {
        alive = false;
        stopMonitorTicks();
        if (actorThread != null) actorThread.interrupt();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest="LifecycleEngineTest" -DfailIfNoTests=false
```
Expected: Tests run: 7, Failures: 0

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/LifecycleEngine.java src/test/java/com/tightening/lifecycle/LifecycleEngineTest.java
git commit -m "feat: add LifecycleEngine with Actor loop, message dispatch, and crash recovery"
```

---

### Task 7a: PrepareBolts Capability

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/PrepareBolts.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/PrepareBoltsTest.java`

**Interfaces:**
- Consumes: `Capability` (Task 4), `MissionContext`, `BoltState` (Task 1/3)
- Produces: `PrepareBolts` (ACTIVATION/PREPARING, pri=0)

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.BoltState;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PrepareBolts Capability")
class PrepareBoltsTest {

    private final PrepareBolts cap = new PrepareBolts();

    @Test
    @DisplayName("有螺栓时初始化 BoltState[] 全部 PENDING")
    void shouldInitializeBoltStates() {
        MissionContext ctx = ctxWithBolts(3);
        CapabilityResult result = cap.execute(ctx);

        assertThat(result).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getBoltStates()).hasSize(3);
        assertThat(ctx.getBoltStates()).allMatch(s -> s == BoltState.PENDING);
        assertThat(ctx.getCurrentBoltIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("零螺栓返回 Fail")
    void shouldFailWhenNoBolts() {
        MissionContext ctx = ctxWithBolts(0);
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("boltConfigs 为 null 返回 Fail")
    void shouldFailWhenBoltConfigsNull() {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(null).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    private static MissionContext ctxWithBolts(int count) {
        List<ProductBolt> bolts = IntStream.range(0, count)
            .mapToObj(i -> new ProductBolt().setBoltSerialNum(i + 1))
            .toList();
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(bolts).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
mvn test -Dtest="PrepareBoltsTest" -DfailIfNoTests=false -q
```
Expected: 编译失败

- [ ] **Step 3: 实现**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class PrepareBolts implements Capability {

    @Override public String id() { return "PrepareBolts"; }
    @Override public Stage stage() { return Stage.ACTIVATION; }
    @Override public SubState subState() { return SubState.PREPARING; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        if (ctx.getBoltConfigs() == null || ctx.getBoltConfigs().isEmpty()) {
            log.warn("No bolts configured for mission {}", ctx.getProductMissionId());
            return CapabilityResult.Fail;
        }
        int count = ctx.getBoltConfigs().size();
        BoltState[] states = new BoltState[count];
        Arrays.fill(states, BoltState.PENDING);
        ctx.setBoltStates(states);
        ctx.setCurrentBoltIndex(0);
        ctx.setCurrentSideIndex(0);
        log.info("PrepareBolts: {} bolts initialized", count);
        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
mvn test -Dtest="PrepareBoltsTest" -DfailIfNoTests=false
```
Expected: Tests run: 3, Failures: 0

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/PrepareBolts.java src/test/java/com/tightening/lifecycle/capability/PrepareBoltsTest.java
git commit -m "feat: add PrepareBolts Capability"
```

---

### Task 7b: CreateMissionRecord Capability

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/CreateMissionRecord.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/CreateMissionRecordTest.java`

**Interfaces:**
- Consumes: `MissionRecordService.createRecord()` (existing)
- Produces: `CreateMissionRecord` (ACTIVATION/ACTIVATING, pri=0)

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle.capability;

import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.MissionRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateMissionRecord Capability")
class CreateMissionRecordTest {

    @Mock private MissionRecordService missionRecordService;
    private CreateMissionRecord cap;

    @BeforeEach
    void setUp() {
        cap = new CreateMissionRecord(missionRecordService);
    }

    @Test
    @DisplayName("创建 MissionRecord 并回写 Context")
    void shouldCreateRecordAndSetOnContext() {
        MissionRecord record = new MissionRecord().setId(42L);
        when(missionRecordService.createRecord(1L, null, 0)).thenReturn(record);

        MissionContext ctx = minimalContext();
        CapabilityResult result = cap.execute(ctx);

        assertThat(result).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getMissionRecord().getId()).isEqualTo(42L);
        verify(missionRecordService).createRecord(1L, null, 0);
    }

    private static MissionContext minimalContext() {
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
    }
}
```

- [ ] **Step 2: 运行测试确认失败** → **Step 3: 实现**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.MissionRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CreateMissionRecord implements Capability {

    private final MissionRecordService missionRecordService;

    @Override public String id() { return "CreateMissionRecord"; }
    @Override public Stage stage() { return Stage.ACTIVATION; }
    @Override public SubState subState() { return SubState.ACTIVATING; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        var record = missionRecordService.createRecord(
            ctx.getProductMissionId(), null, 0);
        ctx.setMissionRecord(record);
        log.info("MissionRecord created: id={}", record.getId());
        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 4: 运行测试确认通过** → **Step 5: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/CreateMissionRecord.java src/test/java/com/tightening/lifecycle/capability/CreateMissionRecordTest.java
git commit -m "feat: add CreateMissionRecord Capability"
```

---

### Task 7c: SWITCH_BOLT Capabilities — SendPSet + 3 stubs

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/SendPSet.java`
- Create: `src/main/java/com/tightening/lifecycle/capability/SendArrangerSignal.java`
- Create: `src/main/java/com/tightening/lifecycle/capability/SendSetterSelector.java`
- Create: `src/main/java/com/tightening/lifecycle/capability/BoltBarCodeCheck.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/SendPSetTest.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/StubCapabilityTest.java`

- [ ] **Step 1: 写失败测试 — SendPSetTest**

```java
package com.tightening.lifecycle.capability;

import com.tightening.device.contract.ITool;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendPSet Capability")
class SendPSetTest {

    @Mock private ITool tool;
    private SendPSet cap;

    @BeforeEach
    void setUp() {
        cap = new SendPSet();
    }

    @Test
    @DisplayName("precondition 不满足时返回 false（bolt 为 null）")
    void preconditionShouldReturnFalseWhenBoltNull() {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("precondition 不满足时返回 false（parameterSetId 为 null）")
    void preconditionShouldReturnFalseWhenNoPSet() {
        ProductBolt bolt = new ProductBolt().setBoltSerialNum(1);
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of(bolt)).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }
}
```

- [ ] **Step 2: 写失败测试 — StubCapabilityTest**

```java
package com.tightening.lifecycle.capability;

import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stub Capabilities")
class StubCapabilityTest {

    @Test
    @DisplayName("SendArrangerSignal precondition 始终返回 false")
    void sendArrangerSignalShouldAlwaysSkip() {
        var cap = new SendArrangerSignal();
        assertThat(cap.precondition(null)).isFalse();
    }

    @Test
    @DisplayName("SendSetterSelector precondition 始终返回 false")
    void sendSetterSelectorShouldAlwaysSkip() {
        var cap = new SendSetterSelector();
        assertThat(cap.precondition(null)).isFalse();
    }

    @Test
    @DisplayName("BoltBarCodeCheck precondition 始终返回 false")
    void boltBarCodeCheckShouldAlwaysSkip() {
        var cap = new BoltBarCodeCheck();
        assertThat(cap.precondition(null)).isFalse();
    }
}
```

- [ ] **Step 3: 运行测试确认失败** → **Step 4: 实现 SendPSet.java**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ITool;
import com.tightening.entity.ProductBolt;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendPSet implements Capability {

    @Override public String id() { return "SendPSet"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 2; }

    @Override
    public boolean precondition(MissionContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        return bolt != null && bolt.getParameterSetId() != null;
    }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        ITool tool = resolveTool(ctx);
        if (tool == null) {
            log.warn("No tool found for bolt {}", bolt.getBoltSerialNum());
            return CapabilityResult.Fail;
        }
        tool.sendPSet(bolt.getParameterSetId().intValue())
            .whenComplete((ok, ex) -> {
                if (ex != null || !Boolean.TRUE.equals(ok)) {
                    log.warn("SendPSet failed for bolt {}: ok={}", bolt.getBoltSerialNum(), ok, ex);
                }
            });
        log.info("PSet {} sent for bolt {}", bolt.getParameterSetId(), bolt.getBoltSerialNum());
        return CapabilityResult.Pass;
    }

    private ITool resolveTool(MissionContext ctx) {
        return ctx.getDeviceRegistry().values().stream()
            .findFirst().orElse(null);
    }
}
```

- [ ] **Step 5: 实现 3 个 stub Capability**

```java
// SendArrangerSignal.java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;

public class SendArrangerSignal implements Capability {
    @Override public String id() { return "SendArrangerSignal"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 0; }

    @Override
    public boolean precondition(MissionContext ctx) { return false; }

    @Override
    public CapabilityResult execute(MissionContext ctx) { return CapabilityResult.Skip; }
}
```

```java
// SendSetterSelector.java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;

public class SendSetterSelector implements Capability {
    @Override public String id() { return "SendSetterSelector"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 1; }

    @Override
    public boolean precondition(MissionContext ctx) { return false; }

    @Override
    public CapabilityResult execute(MissionContext ctx) { return CapabilityResult.Skip; }
}
```

```java
// BoltBarCodeCheck.java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;

public class BoltBarCodeCheck implements Capability {
    @Override public String id() { return "BoltBarCodeCheck"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 3; }

    @Override
    public boolean precondition(MissionContext ctx) { return false; }

    @Override
    public CapabilityResult execute(MissionContext ctx) { return CapabilityResult.Skip; }
}
```

- [ ] **Step 6: 运行测试** → **Step 7: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/SendPSet.java src/main/java/com/tightening/lifecycle/capability/SendArrangerSignal.java src/main/java/com/tightening/lifecycle/capability/SendSetterSelector.java src/main/java/com/tightening/lifecycle/capability/BoltBarCodeCheck.java src/test/java/com/tightening/lifecycle/capability/SendPSetTest.java src/test/java/com/tightening/lifecycle/capability/StubCapabilityTest.java
git commit -m "feat: add SendPSet and SWITCH_BOLT stub Capabilities"
```

---

### Task 7d: JUDGING Capabilities — ControllerStatusCheck + ExecuteJudgment

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/ControllerStatusCheck.java`
- Create: `src/main/java/com/tightening/lifecycle/capability/ExecuteJudgment.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/ControllerStatusCheckTest.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/ExecuteJudgmentTest.java`

- [ ] **Step 1: 写失败测试 — ControllerStatusCheckTest**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.TighteningStatus;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ControllerStatusCheck Capability")
class ControllerStatusCheckTest {

    private final ControllerStatusCheck cap = new ControllerStatusCheck();

    @Test
    @DisplayName("控制器 OK → Pass，写 tighteningStatus")
    void shouldPassWhenStatusOk() {
        MissionContext ctx = ctxWithData(TighteningStatus.OK.getCode());
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getTighteningStatus()).isEqualTo(TighteningStatus.OK.getCode());
    }

    @Test
    @DisplayName("控制器 NG → 仍然 Pass（不阻断，留给综合判定）")
    void shouldPassEvenWhenStatusNg() {
        MissionContext ctx = ctxWithData(TighteningStatus.NG.getCode());
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getTighteningStatus()).isEqualTo(TighteningStatus.NG.getCode());
    }

    @Test
    @DisplayName("无 currentOperationData → Fail")
    void shouldFailWhenNoData() {
        MissionContext ctx = minimalContext();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    private static MissionContext ctxWithData(int status) {
        var data = new TighteningData().setTighteningStatus(status);
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).currentOperationData(data).build();
    }

    private static MissionContext minimalContext() {
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
    }
}
```

- [ ] **Step 2: 写失败测试 — ExecuteJudgmentTest**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.DeviceType;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.judgment.JudgmentResult;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteJudgment Capability")
class ExecuteJudgmentTest {

    @Mock private JudgmentStrategy judgmentStrategy;

    @Test
    @DisplayName("调用 JudgmentStrategy 判定 → 写 judgeResult")
    void shouldExecuteJudgmentAndSetResult() {
        when(judgmentStrategy.judge(any(TighteningDataDTO.class)))
            .thenReturn(JudgmentResult.ok());

        var cap = new ExecuteJudgment(Map.of(DeviceType.ATLAS_PF4000, judgmentStrategy));
        var data = new TighteningData().setTighteningId(100L);
        var ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).currentOperationData(data).build();
        ctx.setCurrentDeviceType(DeviceType.ATLAS_PF4000);

        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getJudgeResult().isOk()).isTrue();
    }

    @Test
    @DisplayName("无 currentOperationData → precondition 返回 false")
    void shouldSkipWhenNoData() {
        var cap = new ExecuteJudgment(Map.of());
        var ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();

        assertThat(cap.precondition(ctx)).isFalse();
    }
}
```

- [ ] **Step 3: 运行测试确认失败** → **Step 4: 实现 ControllerStatusCheck.java**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ControllerStatusCheck implements Capability {

    @Override public String id() { return "ControllerStatusCheck"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.JUDGING; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        var data = ctx.getCurrentOperationData();
        if (data == null) {
            log.warn("No current operation data");
            return CapabilityResult.Fail;
        }
        ctx.setTighteningStatus(data.getTighteningStatus());
        log.debug("ControllerStatusCheck: status={}", data.getTighteningStatus());
        return CapabilityResult.Pass;  // NG 也不阻断
    }
}
```

- [ ] **Step 5: 实现 ExecuteJudgment.java**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.DeviceType;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.TighteningData;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.lifecycle.MissionContext;
import com.tightening.util.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ExecuteJudgment implements Capability {

    private final Map<DeviceType, JudgmentStrategy> judgmentStrategies;

    @Override public String id() { return "ExecuteJudgment"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.JUDGING; }
    @Override public int priority() { return 1; }

    @Override
    public boolean precondition(MissionContext ctx) {
        return ctx.getCurrentOperationData() != null;
    }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        TighteningData data = ctx.getCurrentOperationData();
        TighteningDataDTO dto = Converter.entity2Dto(data, TighteningDataDTO::new);

        // 根据设备类型选择 JudgmentStrategy（由 handleTighteningData 解析）
        DeviceType deviceType = ctx.getCurrentDeviceType();
        JudgmentStrategy strategy = deviceType != null ? judgmentStrategies.get(deviceType) : null;
        if (strategy == null) {
            log.warn("No JudgmentStrategy available");
            return CapabilityResult.Fail;
        }
        ctx.setJudgeResult(strategy.judge(dto));
        log.debug("ExecuteJudgment: ok={}", ctx.getJudgeResult().isOk());
        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 6: 运行测试** → **Step 7: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/ControllerStatusCheck.java src/main/java/com/tightening/lifecycle/capability/ExecuteJudgment.java src/test/java/com/tightening/lifecycle/capability/ControllerStatusCheckTest.java src/test/java/com/tightening/lifecycle/capability/ExecuteJudgmentTest.java
git commit -m "feat: add JUDGING Capabilities — ControllerStatusCheck and ExecuteJudgment"
```

---

### Task 7e: StoreData Capability

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/StoreData.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/StoreDataTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle.capability;

import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.TighteningDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreData Capability")
class StoreDataTest {

    @Mock private TighteningDataService tighteningDataService;
    private StoreData cap;

    @BeforeEach
    void setUp() {
        cap = new StoreData(tighteningDataService);
    }

    @Test
    @DisplayName("存储数据并关联 MissionRecord")
    void shouldStoreDataWithMissionRecordId() {
        var data = new TighteningData().setTighteningId(100L);
        var record = new MissionRecord().setId(42L);
        MissionContext ctx = ctxWith(data, record);

        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(data.getMissionRecordId()).isEqualTo(42L);
        verify(tighteningDataService).save(data);
        assertThat(ctx.getTighteningDataList()).contains(data);
        assertThat(ctx.getPreviousOperationData()).isSameAs(data);
        assertThat(ctx.getCurrentOperationData()).isNull();
    }

    @Test
    @DisplayName("无 data 时 precondition 返回 false")
    void shouldSkipWhenNoData() {
        MissionContext ctx = ctxWith(null, new MissionRecord().setId(1L));
        assertThat(cap.precondition(ctx)).isFalse();
    }

    private static MissionContext ctxWith(TighteningData data, MissionRecord record) {
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).currentOperationData(data)
            .missionRecord(record).build();
    }
}
```

- [ ] **Step 2: 运行测试确认失败** → **Step 3: 实现 StoreData.java**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.TighteningDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StoreData implements Capability {

    private final TighteningDataService tighteningDataService;

    @Override public String id() { return "StoreData"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.STORING; }
    @Override public int priority() { return 0; }

    @Override
    public boolean precondition(MissionContext ctx) {
        return ctx.getCurrentOperationData() != null;
    }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        TighteningData data = ctx.getCurrentOperationData();
        if (ctx.getMissionRecord() != null) {
            data.setMissionRecordId(ctx.getMissionRecord().getId());
        }
        tighteningDataService.save(data);
        ctx.getTighteningDataList().add(data);
        ctx.setPreviousOperationData(data);
        ctx.setCurrentOperationData(null);
        log.info("StoreData: id={}, missionRecordId={}", data.getId(), data.getMissionRecordId());
        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 4: 运行测试确认通过** → **Step 5: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/StoreData.java src/test/java/com/tightening/lifecycle/capability/StoreDataTest.java
git commit -m "feat: add StoreData Capability"
```

---

### Task 7f: AdvanceBolt Capability

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/AdvanceBolt.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/AdvanceBoltTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.judgment.JudgmentResult;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.MissionRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdvanceBolt Capability")
class AdvanceBoltTest {

    @Mock private MissionRecordService missionRecordService;
    private AdvanceBolt cap;

    @BeforeEach
    void setUp() {
        cap = new AdvanceBolt(missionRecordService);
    }

    @Test
    @DisplayName("OK 判定后标记为 JUDGED_OK 并推进索引")
    void shouldMarkBoltJudgedOk() {
        MissionContext ctx = ctxWithBolts(2);
        ctx.setCurrentBoltIndex(0);
        ctx.setJudgeResult(new JudgmentResult(true, "OK"));

        cap.execute(ctx);

        assertThat(ctx.getBoltStates()[0]).isEqualTo(BoltState.JUDGED_OK);
        assertThat(ctx.getCurrentBoltIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("NG 判定后标记为 JUDGED_NG 并推进")
    void shouldMarkBoltJudgedNg() {
        MissionContext ctx = ctxWithBolts(2);
        ctx.setCurrentBoltIndex(0);
        ctx.setJudgeResult(new JudgmentResult(false, "NG"));

        cap.execute(ctx);

        assertThat(ctx.getBoltStates()[0]).isEqualTo(BoltState.JUDGED_NG);
        assertThat(ctx.getCurrentBoltIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("全部螺栓完成 → 进入 FINALIZATION")
    void shouldEnterFinalizationWhenAllDone() {
        MissionContext ctx = ctxWithBolts(1);
        ctx.setCurrentBoltIndex(0);

        cap.execute(ctx);

        assertThat(ctx.getCurrentStage()).isEqualTo(Stage.FINALIZATION);
        assertThat(ctx.getCurrentSubState()).isEqualTo(SubState.CLEANING_TASKS);
    }

    @Test
    @DisplayName("最后一个螺栓 OK 时调用 markAsOk")
    void shouldMarkMissionOkWhenAllJudgedOk() {
        MissionContext ctx = ctxWithBolts(1);
        ctx.setCurrentBoltIndex(0);
        ctx.setBoltStates(new BoltState[]{BoltState.JUDGED_OK});
        ctx.setJudgeResult(new JudgmentResult(true, "OK"));
        ctx.setMissionRecord(new MissionRecord().setId(42L));

        cap.execute(ctx);

        verify(missionRecordService).markAsOk(42L);
    }

    private static MissionContext ctxWithBolts(int count) {
        List<ProductBolt> bolts = IntStream.range(0, count)
            .mapToObj(i -> new ProductBolt().setBoltSerialNum(i + 1))
            .toList();
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(bolts).deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .boltStates(new BoltState[count])
            .build();
    }
}
```

- [ ] **Step 2: 运行测试确认失败** → **Step 3: 实现 AdvanceBolt.java**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.judgment.JudgmentResult;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.MissionRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
@RequiredArgsConstructor
public class AdvanceBolt implements Capability {

    private final MissionRecordService missionRecordService;

    @Override public String id() { return "AdvanceBolt"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.ADVANCING; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        JudgmentResult jr = ctx.getJudgeResult();
        if (jr != null) {
            BoltState state = jr.isOk() ? BoltState.JUDGED_OK : BoltState.JUDGED_NG;
            int idx = ctx.getCurrentBoltIndex();
            if (idx >= 0 && idx < ctx.getBoltStates().length) {
                ctx.getBoltStates()[idx] = state;
            }
            log.info("Bolt {} result: {}", idx + 1, state);

            boolean allOk = Arrays.stream(ctx.getBoltStates()).allMatch(s -> s == BoltState.JUDGED_OK);
            if (allOk && ctx.getMissionRecord() != null) {
                missionRecordService.markAsOk(ctx.getMissionRecord().getId());
                log.info("MissionRecord {} marked OK", ctx.getMissionRecord().getId());
            }
        }

        if (ctx.hasMoreBolts()) {
            ctx.setCurrentBoltIndex(ctx.getCurrentBoltIndex() + 1);
            ctx.setCurrentOperationData(null);
            ctx.setJudgeResult(null);
            log.info("Advancing to bolt {}/{}", ctx.getCurrentBoltIndex() + 1, ctx.totalBolts());
            return CapabilityResult.Pass;
        }

        log.info("All bolts completed for mission {}", ctx.getProductMissionId());
        ctx.setCurrentStage(Stage.FINALIZATION);
        ctx.setCurrentSubState(SubState.CLEANING_TASKS);
        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 4: 运行测试** → **Step 5: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/capability/AdvanceBolt.java src/test/java/com/tightening/lifecycle/capability/AdvanceBoltTest.java
git commit -m "feat: add AdvanceBolt Capability"
```

---

### Task 8: LifecycleEngineFactory

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java`
- Create: `src/test/java/com/tightening/lifecycle/LifecycleEngineFactoryTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle;

import com.tightening.constant.DeviceType;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.service.MissionRecordService;
import com.tightening.service.TighteningDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("LifecycleEngineFactory")
class LifecycleEngineFactoryTest {

    @Mock private MissionRecordService missionRecordService;
    @Mock private TighteningDataService tighteningDataService;
    @Mock private JudgmentStrategy judgmentStrategy;

    private LifecycleEngineFactory factory;

    @BeforeEach
    void setUp() {
        factory = new LifecycleEngineFactory(
            missionRecordService, tighteningDataService,
            Map.of(DeviceType.ATLAS_PF4000, judgmentStrategy));
    }

    @Test
    @DisplayName("createEngine 返回已组装的引擎")
    void shouldCreateEngineWithContext() {
        var mission = new ProductMission().setId(1L);
        var engine = factory.createEngine(mission, List.of(), Map.of(), false);

        assertThat(engine).isNotNull();
        assertThat(engine.getContext().getProductMissionId()).isEqualTo(1L);
        assertThat(engine.isAlive()).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试确认失败** → **Step 3: 实现 LifecycleEngineFactory.java**

```java
package com.tightening.lifecycle;

import com.tightening.constant.DeviceType;
import com.tightening.device.contract.ITool;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.lifecycle.capability.*;
import com.tightening.lifecycle.monitor.DeviceConnectionMonitor;
import com.tightening.lifecycle.monitor.LockStateMonitor;
import com.tightening.lifecycle.monitor.PersistentMonitor;
import com.tightening.service.MissionRecordService;
import com.tightening.service.TighteningDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LifecycleEngineFactory {

    private final MissionRecordService missionRecordService;
    private final TighteningDataService tighteningDataService;
    private final Map<DeviceType, JudgmentStrategy> judgmentStrategies;

    public LifecycleEngine createEngine(
            ProductMission mission,
            List<ProductBolt> bolts,
            Map<Long, ITool> deviceMap,
            boolean shouldSelfLoop) {

        MissionContext ctx = MissionContext.builder()
            .productMissionId(mission.getId())
            .missionData(mission)
            .boltConfigs(bolts)
            .deviceRegistry(deviceMap)
            .shouldSelfLoop(shouldSelfLoop)
            .build();

        PipelineDefinition pipeline = PipelineDefinition.createDefault();

        List<Capability> capabilities = List.of(
            new PrepareBolts(),
            new CreateMissionRecord(missionRecordService),
            new SendArrangerSignal(),
            new SendSetterSelector(),
            new SendPSet(),
            new ControllerStatusCheck(),
            new ExecuteJudgment(judgmentStrategies),
            new StoreData(tighteningDataService),
            new AdvanceBolt(missionRecordService)
        );

        List<PersistentMonitor> monitors = List.of(
            new LockStateMonitor(),
            new DeviceConnectionMonitor()
        );

        LifecycleEngine engine = new LifecycleEngine(pipeline, missionRecordService, capabilities, monitors);

        engine.onFaulted(reason -> { /* 后续接事件发布 */ });
        engine.onCompleted(recordId -> { /* 后续接完成通知 */ });

        return engine;
    }
}
```

- [ ] **Step 4: 运行测试确认通过** → **Step 5: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java src/test/java/com/tightening/lifecycle/LifecycleEngineFactoryTest.java
git commit -m "feat: add LifecycleEngineFactory"
```

---

### Task 9: PersistentMonitor + LockStateMonitor + DeviceConnectionMonitor

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/monitor/PersistentMonitor.java`
- Create: `src/main/java/com/tightening/lifecycle/monitor/LockStateMonitor.java`
- Create: `src/main/java/com/tightening/lifecycle/monitor/DeviceConnectionMonitor.java`
- Create: `src/test/java/com/tightening/lifecycle/monitor/LockStateMonitorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle.monitor;

import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.LockMessage;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("LockStateMonitor")
class LockStateMonitorTest {

    @Test
    @DisplayName("空 Context 不抛异常")
    void shouldNotThrowOnEmptyContext() {
        var monitor = new LockStateMonitor();
        var ctx = minimalContext();
        assertThatCode(() -> monitor.execute(ctx)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("intervalMs 为正")
    void intervalShouldBePositive() {
        assertThat(new LockStateMonitor().intervalMs()).isPositive();
    }

    @Test
    @DisplayName("有 MANUAL_LOCK 时正常执行")
    void shouldDetectManualLock() {
        var monitor = new LockStateMonitor();
        var ctx = minimalContext();
        ctx.getLockMessages().add(new LockMessage("MANUAL_LOCK", "operator"));
        assertThatCode(() -> monitor.execute(ctx)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DeviceConnectionMonitor intervalMs 为正")
    void deviceConnectionMonitorIntervalShouldBePositive() {
        assertThat(new DeviceConnectionMonitor().intervalMs()).isPositive();
    }

    private static MissionContext minimalContext() {
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
    }
}
```

- [ ] **Step 2: 运行测试确认失败** → **Step 3: 实现 PersistentMonitor.java**

```java
package com.tightening.lifecycle.monitor;

import com.tightening.lifecycle.MissionContext;

public interface PersistentMonitor {
    long intervalMs();
    void execute(MissionContext ctx);
}
```

- [ ] **Step 4: 实现 LockStateMonitor.java**

```java
package com.tightening.lifecycle.monitor;

import com.tightening.lifecycle.LockMessage;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LockStateMonitor implements PersistentMonitor {

    @Override
    public long intervalMs() {
        return 50;
    }

    @Override
    public void execute(MissionContext ctx) {
        for (LockMessage lm : ctx.getLockMessages()) {
            if (lm.isManual()) {
                log.debug("LockStateMonitor: {} — {}", lm.source(), lm.reason());
            }
        }
    }
}
```

- [ ] **Step 5: 实现 DeviceConnectionMonitor.java**

```java
package com.tightening.lifecycle.monitor;

import com.tightening.device.contract.ITool;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeviceConnectionMonitor implements PersistentMonitor {

    @Override
    public long intervalMs() {
        return 1000;
    }

    @Override
    public void execute(MissionContext ctx) {
        for (ITool tool : ctx.getDeviceRegistry().values()) {
            if (!tool.isConnected()) {
                log.warn("Device {} disconnected", tool.id());
            }
        }
    }
}
```

- [ ] **Step 6: 运行测试** → **Step 7: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/monitor/PersistentMonitor.java src/main/java/com/tightening/lifecycle/monitor/LockStateMonitor.java src/main/java/com/tightening/lifecycle/monitor/DeviceConnectionMonitor.java src/test/java/com/tightening/lifecycle/monitor/LockStateMonitorTest.java
git commit -m "feat: add PersistentMonitor, LockStateMonitor, and DeviceConnectionMonitor"
```

---

### Task 10: MissionRecordService 补充方法 + 数据 Fork 接线

**Files:**
- Modify: `src/main/java/com/tightening/service/MissionRecordService.java`
- Create: 无新文件（接线代码在 Task 8 的 `LifecycleEngineFactory` 回调中体现）
- Create: `src/test/java/com/tightening/service/MissionRecordServiceTest.java`（如不存在）

- [ ] **Step 1: 先读取现有 MissionRecordService.java 确认目标位置**

- [ ] **Step 2: 修改 MissionRecordService.java — 新增 markFaulted 和 updateSnapshot**

在现有 `markAsOk` 方法后追加：

```java
public void markFaulted(Long recordId, String message) {
    lambdaUpdate().eq(MissionRecord::getId, recordId)
            .set(MissionRecord::getMissionResult, MissionResult.NG.getCode())
            .set(MissionRecord::getFaultMessage, message)
            .update();
}

public void updateSnapshot(Long recordId, String snapshotJson) {
    lambdaUpdate().eq(MissionRecord::getId, recordId)
            .set(MissionRecord::getContextSnapshot, snapshotJson)
            .update();
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 运行全体测试确认无回归**

```bash
mvn test -DfailIfNoTests=false
```

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/tightening/service/MissionRecordService.java
git commit -m "feat: add markFaulted and updateSnapshot to MissionRecordService"
```

---

## 验证

```bash
mvn clean test -DfailIfNoTests=false
```

## 文件统计

**新建: 34 源文件 + 15 测试文件 = 49 文件，修改 1 文件，零删除**
