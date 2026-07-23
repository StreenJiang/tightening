# 代码清理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 self-loop 后端逻辑、清理 5 个未使用枚举/修正 InspectionScope 类型、清除死配置、移除空 close()

**Architecture:** 纯删除/修正，不新增功能。4 个独立任务，无相互依赖，可任意顺序执行。

**Tech Stack:** Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.16, Maven

## Global Constraints

- 修改后 `mvn compile` 必须通过
- 修改后 `mvn test` 必须通过
- 不新增 import，只删除不再使用的 import
- 匹配现有代码风格（Lombok、4 空格缩进）

---

### Task 1: N1 — 删除 Self-loop 后端逻辑

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/TaskOrchestrator.java`
- Delete: `src/main/java/com/tightening/lifecycle/TaskCompletedEvent.java`
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java`
- Modify: `src/main/java/com/tightening/lifecycle/TaskContext.java`
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngine.java`
- Modify: `src/test/java/com/tightening/lifecycle/TaskOrchestratorTest.java`

**Interfaces:**
- Produces: `TaskOrchestrator.trigger()` 签名不变，内部不再接受/传递 self-loop 参数；`LifecycleEngineFactory.createEngine()` 删除 `boolean shouldSelfLoop` 参数

- [ ] **Step 1: 修改 TaskOrchestrator.java — 删除自循环逻辑**

删除 `MAX_SELF_LOOPS`、`selfLoopCounts`、`settings`、`publisher` 字段和相关 import，简化 `trigger()`、`onCompleted`、`onFaulted`，删除 `handleRestart()`、`isTaskOk()`。

最终文件：

```java
package com.tightening.lifecycle;

import com.tightening.constant.TaskResult;
import com.tightening.device.DeviceRegistry;
import com.tightening.device.contract.ITool;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.message.DeviceEvent;
import com.tightening.lifecycle.message.InboundCommand;
import com.tightening.lifecycle.message.InboundMessage;
import com.tightening.util.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TaskOrchestrator implements DataRouter {

    private final LifecycleEngineFactory factory;
    private final DeviceRegistry deviceRegistry;

    /** taskId -> 活跃引擎 */
    private final Map<Long, LifecycleEngine> activeEngines = new ConcurrentHashMap<>();
    /** deviceId -> taskId（数据路由用） */
    private final Map<Long, Long> deviceToTaskId = new ConcurrentHashMap<>();

    public TaskOrchestrator(LifecycleEngineFactory factory,
                               @Lazy DeviceRegistry deviceRegistry) {
        this.factory = factory;
        this.deviceRegistry = deviceRegistry;
    }

    // === DataRouter 接口实现 ===

    @Override
    public void routeTighteningData(long deviceId, TighteningDataDTO dto) {
        Long taskId = deviceToTaskId.get(deviceId);
        if (taskId == null) {
            log.warn("No active task for deviceId={}, dropping tightening data", deviceId);
            return;
        }
        LifecycleEngine engine = activeEngines.get(taskId);
        if (engine == null) {
            log.warn("Engine for taskId={} not alive, dropping tightening data", taskId);
            return;
        }
        TighteningData data = Converter.dto2Entity(dto, TighteningData::new);
        engine.postMessage(new DeviceEvent.TighteningDataReceived(data, deviceId));
    }

    // === 触发阶段入口 ===

    public LifecycleEngine trigger(ProductTask task, List<ProductBolt> bolts,
                                    String productCode, String partsCode) {
        Long taskId = task.getId();
        if (activeEngines.containsKey(taskId)) {
            log.warn("Task {} already active", taskId);
            return null;
        }

        Map<Long, ITool> devices = deviceRegistry.getAllTools().stream()
                .collect(Collectors.toMap(ITool::id, t -> t));
        LifecycleEngine engine = factory.createEngine(
                task, bolts, devices,
                productCode, partsCode);

        engine.onTriggered(mId -> {
            engine.getContext().getDeviceRegistry().keySet()
                    .forEach(deviceId -> deviceToTaskId.put(deviceId, mId));
            engine.startMonitorTicks();
        });

        engine.onCompleted(recordId -> cleanup(taskId));

        engine.onFaulted(reason -> {
            cleanup(taskId);
            log.warn("Task {} trigger faulted: {}", taskId, reason);
        });

        LifecycleEngine prev = activeEngines.putIfAbsent(taskId, engine);
        if (prev != null) {
            engine.shutdown();
            log.warn("Concurrent trigger for task {}, rejected", taskId);
            return null;
        }
        engine.start(engine.getContext());
        engine.postMessage(new InboundCommand.TriggerRequest(productCode, partsCode));
        log.info("Task {} trigger posted", taskId);
        return engine;
    }

    // === 内部辅助 ===

    private void cleanup(Long taskId) {
        activeEngines.remove(taskId);
        deviceToTaskId.values().removeIf(v -> v.equals(taskId));
    }

    public Optional<LifecycleEngine> getActiveEngine(Long taskId) {
        return Optional.ofNullable(activeEngines.get(taskId));
    }

    public void postMessage(Long taskId, InboundMessage msg) {
        LifecycleEngine engine = activeEngines.get(taskId);
        if (engine != null) {
            engine.postMessage(msg);
        } else {
            log.debug("No active engine for taskId={}", taskId);
        }
    }
}
```

- [ ] **Step 2: 删除 TaskCompletedEvent.java**

```bash
rm src/main/java/com/tightening/lifecycle/TaskCompletedEvent.java
```

- [ ] **Step 3: 修改 LifecycleEngineFactory.java — 删除 shouldSelfLoop 参数**

`createEngine()` 方法签名和 TaskContext builder 移除 `shouldSelfLoop`：

```java
public LifecycleEngine createEngine(
        ProductTask task,
        List<ProductBolt> bolts,
        Map<Long, ITool> deviceMap,
        @Nullable String productCode,
        @Nullable String partsCode) {

    TaskContext ctx = TaskContext.builder()
        .productTaskId(task.getId())
        .taskData(task)
        .boltConfigs(bolts)
        .deviceRegistry(deviceMap)
        .productCode(productCode)
        .partsCode(partsCode)
        .build();
    // ... 其余不变
}
```

同时删除已无用的 `import com.tightening.config.LocalSettings;`（settings 字段仍被 ExportData 使用，检查后确认保留）。

- [ ] **Step 4: 修改 TaskContext.java — 删除 shouldSelfLoop 字段**

删除第 29-30 行：

```java
    /** 可变 — 引擎在运行时可能修改（例如 SkipScrew 快速路径禁用自循环） */
    @Builder.Default @Setter private boolean shouldSelfLoop;
```

- [ ] **Step 5: 修改 LifecycleEngine.java — 删除 setShouldSelfLoop 调用**

`startSkipScrewLifecycle()` 中删除 `ctx.setShouldSelfLoop(false);` 行（第 196 行），变成：

```java
    private void startSkipScrewLifecycle(TaskContext ctx) {
        var record = taskRecordService.createRecord(
                ctx.getProductTaskId(), ctx.getProductCode(), 0);
        taskRecordService.markAsOk(record.getId());
        ctx.setTaskRecord(record);
        ctx.setCurrentStage(Stage.FINALIZATION);
        ctx.setCurrentSubState(SubState.CLEANING_TASKS);
        postMessage(new InboundCommand.AdvancePipeline());
    }
```

- [ ] **Step 6: 修改 TaskOrchestratorTest.java — 替换自循环测试为基本 trigger 测试**

删除两个自循环测试，替换为 trigger 基本行为验证。注意：测试中 `LifecycleEngineFactory` 构造不再需要 `settings`（LocalSettings 字段仍需 — 传给 ExportData），`TaskOrchestrator` 构造不再需要 `settings` 和 `publisher`。

```java
package com.tightening.lifecycle;

import com.tightening.constant.DeviceType;
import com.tightening.device.DeviceRegistry;
import com.tightening.device.contract.ITool;
import com.tightening.entity.TaskRecord;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
import com.tightening.judgment.JudgmentResult;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.ExportTaskService;
import com.tightening.service.TaskRecordService;
import com.tightening.service.TighteningDataService;
import com.tightening.service.WorkplaceStatusService;
import com.tightening.config.LocalSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskOrchestrator")
class TaskOrchestratorTest {

    @Mock private TaskRecordService taskRecordService;
    @Mock private TighteningDataService tighteningDataService;
    @Mock private ExportTaskService exportTaskService;
    @Mock private JudgmentStrategy judgmentStrategy;
    @Mock private DeviceRegistry deviceRegistry;
    @Mock private LocalSettings settings;
    @Mock private BarCodeMatchingRuleService barCodeMatchingRuleService;
    @Mock private WorkplaceStatusService workplaceStatusService;
    @Mock private ITool mockTool;

    private LifecycleEngineFactory factory;
    private TaskOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(settings.exportTypes()).thenReturn(List.of("standard_excel"));
        lenient().when(mockTool.id()).thenReturn(1L);
        lenient().when(mockTool.type()).thenReturn(DeviceType.ATLAS_PF4000);
        lenient().doNothing().when(exportTaskService).createTask(anyString(), anyLong(), anyString());
        factory = new LifecycleEngineFactory(
            taskRecordService, tighteningDataService, exportTaskService, settings,
            Map.of(DeviceType.ATLAS_PF4000, judgmentStrategy), barCodeMatchingRuleService,
            workplaceStatusService);
        orchestrator = new TaskOrchestrator(factory, deviceRegistry);
    }

    private static TaskRecord taskRecordWithId(long id, Integer taskResult) {
        TaskRecord r = new TaskRecord();
        r.setId(id);
        if (taskResult != null) r.setTaskResult(taskResult);
        return r;
    }

    private static ProductTask taskWithId(long id) {
        ProductTask m = new ProductTask();
        m.setId(id);
        return m;
    }

    private static ProductBolt boltWithId(long id, int serialNum) {
        ProductBolt b = new ProductBolt();
        b.setId(id);
        b.setBoltSerialNum(serialNum);
        return b;
    }

    @Test
    @DisplayName("trigger 成功创建引擎")
    void shouldCreateEngineOnTrigger() {
        when(taskRecordService.createRecord(anyLong(), any(), anyInt()))
            .thenReturn(taskRecordWithId(42L, null));
        when(deviceRegistry.getAllTools()).thenReturn(List.of(mockTool));
        when(mockTool.sendLock()).thenReturn(CompletableFuture.completedFuture(true));
        when(judgmentStrategy.judge(any())).thenReturn(JudgmentResult.ok());

        LifecycleEngine engine = orchestrator.trigger(
            taskWithId(1L), List.of(boltWithId(10L, 1)), null, null);

        assertThat(engine).isNotNull();
    }

    @Test
    @DisplayName("重复 trigger 同一 task 返回 null")
    void shouldRejectDuplicateTrigger() {
        when(taskRecordService.createRecord(anyLong(), any(), anyInt()))
            .thenReturn(taskRecordWithId(42L, null));
        when(deviceRegistry.getAllTools()).thenReturn(List.of(mockTool));
        when(mockTool.sendLock()).thenReturn(CompletableFuture.completedFuture(true));
        when(judgmentStrategy.judge(any())).thenReturn(JudgmentResult.ok());

        orchestrator.trigger(taskWithId(1L), List.of(boltWithId(10L, 1)), null, null);
        LifecycleEngine second = orchestrator.trigger(
            taskWithId(1L), List.of(boltWithId(10L, 1)), null, null);

        assertThat(second).isNull();
    }
}
```

- [ ] **Step 7: 编译 + 测试验证**

```bash
mvn compile
mvn test -pl . -Dtest=TaskOrchestratorTest
```

预期: COMPILE SUCCESS, Tests PASS (2/2)

---

### Task 2: G5 — 枚举清理

**Files:**
- Delete: `src/main/java/com/tightening/constant/DeleteStatus.java`
- Delete: `src/main/java/com/tightening/constant/TCPCommand.java`
- Modify: `src/main/java/com/tightening/constant/InspectionScope.java`
- Modify: `src/main/java/com/tightening/entity/ProductTask.java`
- Modify: `src/main/java/com/tightening/dto/ProductTaskDTO.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application.yaml`

**Interfaces:**
- Consumes: `application.yaml` mybatis-plus 段
- Produces: `InspectionScope` 枚举实现 MyBatis-Plus `@EnumValue` + Jackson `@JsonValue`；`ProductTask.inspectionScope` / `ProductTaskDTO.inspectionScope` 类型变为 `InspectionScope`

- [ ] **Step 1: 删除 DeleteStatus.java**

```bash
rm src/main/java/com/tightening/constant/DeleteStatus.java
```

- [ ] **Step 2: 删除 TCPCommand.java**

```bash
rm src/main/java/com/tightening/constant/TCPCommand.java
```

- [ ] **Step 3: 给 InspectionScope 加 @EnumValue 和 @JsonValue**

在 `code` 字段上加两个注解：

```java
package com.tightening.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum InspectionScope {
    ALL(1),
    CHOSEN(2);

    @EnumValue
    @JsonValue
    private final int code;
    private static final Map<Integer, InspectionScope> BY_CODE =
        Arrays.stream(values()).collect(Collectors.toMap(InspectionScope::getCode, Function.identity()));

    InspectionScope(int code) { this.code = code; }

    @JsonCreator
    public static InspectionScope fromCode(int code) {
        return Optional.ofNullable(BY_CODE.get(code))
            .orElseThrow(() -> new IllegalArgumentException("Unknown InspectionScope code: " + code));
    }
}
```

- [ ] **Step 4: 修改 ProductTask.java**

`inspectionScope` 字段从 `Integer` 改为 `InspectionScope`，新增 import：

```java
package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tightening.constant.InspectionScope;

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
    private InspectionScope inspectionScope;
}
```

- [ ] **Step 5: 修改 ProductTaskDTO.java**

同上，`inspectionScope` 字段从 `Integer` 改为 `InspectionScope`，新增 import：

```java
package com.tightening.dto;

import com.tightening.constant.InspectionScope;

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
    private InspectionScope inspectionScope;
}
```

- [ ] **Step 6: application.yaml（main + test）— 加 default-enum-type-handler**

两个文件的 `mybatis-plus.configuration` 段末都加：

```yaml
    default-enum-type-handler: com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler
```

- [ ] **Step 7: 新增 InspectionScopeMappingTest**

创建 `src/test/java/com/tightening/constant/InspectionScopeMappingTest.java`，验证枚举持久化映射：

```java
package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InspectionScope 枚举持久化映射")
class InspectionScopeMappingTest {

    @Test
    @DisplayName("ALL → code=1 → fromCode(1) 回 ALL")
    void allRoundTrip() {
        assertThat(InspectionScope.ALL.getCode()).isEqualTo(1);
        assertThat(InspectionScope.fromCode(1)).isEqualTo(InspectionScope.ALL);
    }

    @Test
    @DisplayName("CHOSEN → code=2 → fromCode(2) 回 CHOSEN")
    void chosenRoundTrip() {
        assertThat(InspectionScope.CHOSEN.getCode()).isEqualTo(2);
        assertThat(InspectionScope.fromCode(2)).isEqualTo(InspectionScope.CHOSEN);
    }

    @Test
    @DisplayName("未知 code 抛 IllegalArgumentException")
    void unknownCodeShouldThrow() {
        assertThatThrownBy(() -> InspectionScope.fromCode(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("@EnumValue 和 @JsonValue 都注解在 code 字段上")
    void annotationsShouldBeOnCodeField() throws Exception {
        var field = InspectionScope.class.getDeclaredField("code");
        assertThat(field.isAnnotationPresent(com.baomidou.mybatisplus.annotation.EnumValue.class)).isTrue();
        assertThat(field.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonValue.class)).isTrue();
    }
}
```

- [ ] **Step 8: 编译 + 测试验证**

```bash
mvn compile
mvn test
```

预期: COMPILE SUCCESS, all tests PASS

---

### Task 3: G6 — 清理死配置

**Files:**
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application.yaml`
- Modify: `src/main/resources/application-dev.yml`

**Interfaces:**
- Produces: 无新增接口

- [ ] **Step 1: application.yaml（main + test）— 修 repository→mapper + 删 jpa 块**

两个文件均有同样的 `com.tightening.repository: DEBUG` 和 `spring.jpa.hibernate.ddl-auto: validate`。main 还有 `monitoring` 块（test 没有）。

a) 修 `com.tightening.repository` → `com.tightening.mapper`（main 第 81 行，test 第 76 行）
b) 删 `spring.jpa.hibernate.ddl-auto` 块（main 第 25-28 行，test 第 25-28 行）
c) 删 `monitoring` 块（仅 main 第 83-88 行）

- [ ] **Step 2: application-dev.yml — 删除空 atlas 段**

删除 `atlas:` 行（第 16 行，在 `tool-control:` 下）：

```yaml
tool-control:
  common:
    lock_unlock_cooldown_ms: 5000
  fit:
    heart-beat-interval-ms: 30000
    heart-beat-retry-max: 3
```

（`atlas:` 行删除，下面的 `fit:` 保留）

- [ ] **Step 3: 启动验证**

```bash
mvn compile
mvn spring-boot:run
# 确认启动日志无报错，Ctrl+C 退出
```

预期: 应用正常启动，无配置解析错误

---

### Task 4: G7 — 删除 TCPDeviceHandler 空 close()

**Files:**
- Modify: `src/main/java/com/tightening/device/handler/impl/TCPDeviceHandler.java`

**Interfaces:**
- Consumes: 无
- Produces: `TCPDeviceHandler` 不再实现 `Closeable`

- [ ] **Step 1: 删除 Closeable 实现、close() 方法、import**

三处改动：

a) 删除 `import java.io.Closeable;`（第 23 行）
b) 类声明 `implements Closeable` → 删除（第 32 行）
c) 删除空 `close()` 方法（第 288-291 行）

改动后的类声明：

```java
public abstract class TCPDeviceHandler implements DeviceHandler {
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile
```

预期: COMPILE SUCCESS

- [ ] **Step 3: 全量测试**

```bash
mvn test
```

预期: all tests PASS

---

## 执行顺序

Task 1-4 无相互依赖，可任意顺序。推荐顺序：1 → 2 → 3 → 4（按影响范围从大到小）。
