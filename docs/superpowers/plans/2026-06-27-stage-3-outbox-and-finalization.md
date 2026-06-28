# Stage 3: 锁语义修正 + Outbox + FINALIZATION + MissionOrchestrator — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Outbox subsystem (export_task table, Exporter, ExportWorker), add `settings.yml` external config, implement 4 FINALIZATION-stage Capabilities, and create `MissionOrchestrator` as the engine lifecycle manager with event-driven self-loop restart. Rename ToolHandler enable/disable → lock/unlock at the end.

**Architecture:** `ExportWorker` is a `@Scheduled` Spring bean polling `export_task` every 5s. `LocalSettings` reads `~/tightening_system/settings.yml` (prefix `tightening`). `MissionOrchestrator` creates engines via `LifecycleEngineFactory`, holds active engine refs, routes tightening data via `DataRouter` interface, and handles self-loop restart via Spring `@Async` events. `DataRouter` interface eliminates the `DeviceRegistry` ↔ `MissionOrchestrator` circular dependency.

**Tech Stack:** Java 21, Spring Boot 3.5.10, MyBatis-Plus 3.5.9, Flyway, SQLite, JUnit 5, AssertJ 3.27.7, Mockito, SnakeYAML

## Global Constraints

- 新建 ~22 源文件 + ~8 测试文件，修改 ~7 源文件 + ~7 测试文件，零删除
- `settings.yml` 位于 `~/tightening_system/settings.yml`，缺失时使用内置默认值，prefix 为 `tightening`
- `@ConfigurationProperties` 放在 `@Component` 上（非 `@Configuration`，record 是 final 不可被 CGLIB proxy）
- `ExportWorker` 通过 `@Scheduled(fixedDelay = 5000)` 轮询，`@EnableScheduling` 在主应用类（非 Worker 类）
- `Exporter` 实现通过 `ExporterRegistry`（Spring 自动注入 `List<Exporter>`）注册
- `MissionOrchestrator` 实现 `DataRouter` 接口，消除与 `DeviceRegistry` 的循环依赖
- `DeviceRegistry` 依赖 `DataRouter` 接口（不依赖 `MissionOrchestrator`）
- **自循环：事件驱动** — `MissionOrchestrator.onCompleted` 发布 `MissionCompletedEvent`，`@Async @EventListener` 执行重启
- **自循环保护**：`maxSelfLoops` 上限（默认 1000），防止逻辑错误导致的无限循环
- `LifecycleEngine.inbox` 有界：`LinkedBlockingQueue<>(1024)`，`offer()` 返回 false 时 WARN
- **锁语义统一** — ToolHandler: `enableToolOp`→`unlock`，`disableToolOp`→`lock`，`isToolEnabled`→`isUnlocked`（布尔方向不变）
- `ExportTask.status` 使用 enum `ExportTaskStatus`（PENDING, PROCESSING, COMPLETED, FAILED），与项目 int-code enum 模式不同但无需 int code（不存储为整数）
- `ExportTask.completedAt` 类型 `LocalDateTime`（与 `BaseEntity` 时间字段一致）
- `export_task` 表 Flyway 迁移编号 V1.0.13
- `JsonUtils.parse()`（非 `fromJson`）
- `LockTools` 用 `whenComplete` 记录失败日志

---

### Task 0: export_task Flyway 迁移 + ExportTask 实体 + ExportTaskStatus 枚举

**Files:**
- Create: `src/main/resources/db/migration/V1.0.13__add_export_task.sql`
- Create: `src/main/java/com/tightening/constant/ExportTaskStatus.java`
- Create: `src/main/java/com/tightening/entity/ExportTask.java`
- Create: `src/main/java/com/tightening/mapper/ExportTaskMapper.java`
- Create: `src/test/java/com/tightening/entity/ExportTaskTest.java`

**Interfaces:**
- Consumes: `BaseEntity`
- Produces: `ExportTaskStatus` enum, `ExportTask` entity, `ExportTaskMapper`

- [ ] **Step 1: 实现 ExportTaskStatus.java（int-code enum，对齐项目模式）**

```java
package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum ExportTaskStatus {
    PENDING(0),
    PROCESSING(1),
    COMPLETED(2),
    FAILED(3);

    private final int code;

    ExportTaskStatus(int code) {
        this.code = code;
    }

    public static Optional<ExportTaskStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
```

- [ ] **Step 2: 写失败测试 — ExportTaskTest**

```java
package com.tightening.entity;

import com.tightening.constant.ExportTaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExportTask 实体")
class ExportTaskTest {

    @Test
    @DisplayName("链式 setter 设置字段")
    void shouldSetFieldsWithChain() {
        ExportTask task = new ExportTask()
                .setType("standard_excel")
                .setMissionRecordId(42L)
                .setPayload("{\"key\":\"value\"}")
                .setStatus(ExportTaskStatus.PENDING.getCode())
                .setRetryCount(0)
                .setMaxRetries(3);

        assertThat(task.getType()).isEqualTo("standard_excel");
        assertThat(task.getMissionRecordId()).isEqualTo(42L);
        assertThat(task.getPayload()).isEqualTo("{\"key\":\"value\"}");
        assertThat(task.getStatus()).isEqualTo(ExportTaskStatus.PENDING.getCode());
        assertThat(task.getRetryCount()).isEqualTo(0);
        assertThat(task.getMaxRetries()).isEqualTo(3);
        assertThat(task.getErrorMessage()).isNull();
        assertThat(task.getCompletedAt()).isNull();
    }
}
```

- [ ] **Step 3: 运行测试确认失败** → **Step 4: 实现 Flyway 迁移 V1.0.13**

```sql
CREATE TABLE export_task (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    type              TEXT    NOT NULL,
    mission_record_id INTEGER NOT NULL,
    payload           TEXT    NOT NULL,
    status            INTEGER NOT NULL DEFAULT 0,
    retry_count       INTEGER NOT NULL DEFAULT 0,
    max_retries       INTEGER NOT NULL DEFAULT 3,
    error_message     TEXT,
    completed_at      TEXT,
    deleted           INTEGER DEFAULT 0,
    creator_id        INTEGER,
    modifier_id       INTEGER,
    create_time       TEXT,
    modify_time       TEXT
);
```

- [ ] **Step 5: 实现 ExportTask.java（status=ExportTaskStatus, completedAt=LocalDateTime）**

```java
package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tightening.constant.ExportTaskStatus;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Setter
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("export_task")
public class ExportTask extends BaseEntity {
    private String type;
    private Long missionRecordId;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private Integer maxRetries;
    private String errorMessage;
    private LocalDateTime completedAt;
}
```

- [ ] **Step 6: 实现 ExportTaskMapper.java**

```java
package com.tightening.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tightening.entity.ExportTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTask> {
}
```

- [ ] **Step 7: 运行测试确认通过** → **Step 8: 提交**

```bash
git add src/main/resources/db/migration/V1.0.13__add_export_task.sql \
        src/main/java/com/tightening/constant/ExportTaskStatus.java \
        src/main/java/com/tightening/entity/ExportTask.java \
        src/main/java/com/tightening/mapper/ExportTaskMapper.java \
        src/test/java/com/tightening/entity/ExportTaskTest.java
git commit -m "feat: add export_task table, ExportTaskStatus enum, entity, and mapper"
```

---

### Task 1: ExportPayload + ExportResult records

**Files:**
- Create: `src/main/java/com/tightening/export/ExportPayload.java`
- Create: `src/main/java/com/tightening/export/ExportResult.java`
- Create: `src/test/java/com/tightening/export/ExportResultTest.java`

**Interfaces:**
- Consumes: nothing (standalone records)
- Produces: `ExportPayload` (missionRecordId, type, data Map), `ExportResult` (success, message)

- [ ] **Step 1: 写失败测试 — ExportResultTest**（见前版本）→ **Step 2: 运行确认失败** → **Step 3-4: 实现 ExportPayload.java + ExportResult.java**（见前版本）→ **Step 5: 运行确认通过** → **Step 6: 提交**

---

### Task 2: ExportTaskService（使用 ExportTaskStatus 枚举）

**Files:**
- Create: `src/main/java/com/tightening/service/ExportTaskService.java`
- Create: `src/test/java/com/tightening/service/ExportTaskServiceTest.java`

**Interfaces:**
- Consumes: `ExportTask`, `ExportTaskMapper` (Task 0)
- Produces: `ExportTaskService` — `createTask(type, missionRecordId, payload)`, `findPending(limit)`, `markProcessing(id)`, `markCompleted(id)`, `markFailed(id, errorMessage, retryCount, maxRetries)`

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportTaskService")
class ExportTaskServiceTest {

    @Mock
    private ExportTaskMapper mapper;

    private ExportTaskService service;

    @BeforeEach
    void setUp() {
        service = new ExportTaskService();
        service.baseMapper = mapper;
    }

    @Test
    @DisplayName("createTask 保存 PENDING 状态的 ExportTask")
    void shouldCreateTask() {
        ArgumentCaptor<ExportTask> captor = ArgumentCaptor.forClass(ExportTask.class);
        when(mapper.insert(any())).thenReturn(1);

        service.createTask("standard_excel", 42L, "{\"k\":\"v\"}");

        verify(mapper).insert(captor.capture());
        ExportTask task = captor.getValue();
        assertThat(task.getType()).isEqualTo("standard_excel");
        assertThat(task.getStatus()).isEqualTo(ExportTaskStatus.PENDING.getCode());
    }

    @Test
    @DisplayName("markProcessing 将状态改为 PROCESSING")
    void shouldMarkProcessing() {
        service.markProcessing(1L);
        verify(mapper).updateById(any());
    }

    @Test
    @DisplayName("markCompleted 更新状态和完成时间")
    void shouldMarkCompleted() {
        ArgumentCaptor<ExportTask> captor = ArgumentCaptor.forClass(ExportTask.class);
        when(mapper.updateById(any())).thenReturn(1);

        service.markCompleted(1L);

        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ExportTaskStatus.COMPLETED.getCode());
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed retryCount < maxRetries 时设为 FAILED，否则保持 PENDING 重试")
    void shouldMarkFailed() {
        ArgumentCaptor<ExportTask> captor = ArgumentCaptor.forClass(ExportTask.class);
        service.markFailed(1L, "timeout", 2, 3);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("timeout");
    }
}
```

- [ ] **Step 2: 运行测试确认失败** → **Step 3: 实现 ExportTaskService.java**

```java
package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.ExportTaskStatus;
import com.tightening.entity.ExportTask;
import com.tightening.mapper.ExportTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ExportTaskService extends ServiceImpl<ExportTaskMapper, ExportTask> {

    public void createTask(String type, Long missionRecordId, String payload) {
        ExportTask task = new ExportTask()
                .setType(type)
                .setMissionRecordId(missionRecordId)
                .setPayload(payload)
                .setStatus(ExportTaskStatus.PENDING.getCode())
                .setRetryCount(0)
                .setMaxRetries(3);
        save(task);
    }

    public List<ExportTask> findPending(int limit) {
        return lambdaQuery()
                .eq(ExportTask::getStatus, ExportTaskStatus.PENDING.getCode())
                .orderByAsc(ExportTask::getId)
                .last("LIMIT " + limit)
                .list();
    }

    public void markProcessing(Long id) {
        lambdaUpdate()
                .eq(ExportTask::getId, id)
                .eq(ExportTask::getStatus, ExportTaskStatus.PENDING.getCode())
                .set(ExportTask::getStatus, ExportTaskStatus.PROCESSING.getCode())
                .update();
    }

    public void markCompleted(Long id) {
        lambdaUpdate().eq(ExportTask::getId, id)
                .set(ExportTask::getStatus, ExportTaskStatus.COMPLETED.getCode())
                .set(ExportTask::getCompletedAt, LocalDateTime.now())
                .update();
    }

    public void markFailed(Long id, String errorMessage, int retryCount, int maxRetries) {
        int newStatus = retryCount >= maxRetries
                ? ExportTaskStatus.FAILED.getCode() : ExportTaskStatus.PENDING.getCode();
        lambdaUpdate().eq(ExportTask::getId, id)
                .set(ExportTask::getStatus, newStatus)
                .set(ExportTask::getRetryCount, retryCount)
                .set(ExportTask::getErrorMessage, errorMessage)
                .update();
    }
}
```

- [ ] **Step 4: 运行测试确认通过** → **Step 5: 提交**

---

### Task 3: Exporter 接口 + ExporterRegistry

**Files:**
- Create: `src/main/java/com/tightening/export/Exporter.java`
- Create: `src/main/java/com/tightening/export/ExporterRegistry.java`
- Create: `src/test/java/com/tightening/export/ExporterRegistryTest.java`

**Interfaces:**
- Consumes: `ExportPayload`, `ExportResult` (Task 1)
- Produces: `Exporter` (type, execute), `ExporterRegistry` (get, register, Spring List auto-assembly)

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExporterRegistry")
class ExporterRegistryTest {

    @Test
    @DisplayName("get 通过 type 返回注册的 Exporter")
    void shouldGetRegisteredExporter() {
        ExporterRegistry registry = new ExporterRegistry(List.of(
                new FakeExporter("type_a"),
                new FakeExporter("type_b")));
        assertThat(registry.get("type_a")).isNotNull();
        assertThat(registry.get("type_a").type()).isEqualTo("type_a");
    }

    @Test
    @DisplayName("未注册的 type 抛异常")
    void shouldThrowForUnknownType() {
        assertThatThrownBy(() -> new ExporterRegistry(List.of()).get("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    @DisplayName("register 手动注册")
    void shouldRegisterManually() {
        ExporterRegistry registry = new ExporterRegistry(List.of());
        registry.register(new FakeExporter("manual"));
        assertThat(registry.get("manual")).isNotNull();
    }

    private record FakeExporter(String type) implements Exporter {
        @Override
        public ExportResult execute(ExportPayload payload) { return ExportResult.ok("done"); }
    }
}
```

- [ ] **Step 2: 运行测试确认失败** → **Step 3: 实现 Exporter.java**

```java
package com.tightening.export;

public interface Exporter {
    String type();
    ExportResult execute(ExportPayload payload);
}
```

- [ ] **Step 4: 实现 ExporterRegistry.java**

```java
package com.tightening.export;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExporterRegistry {
    private final Map<String, Exporter> exporters = new ConcurrentHashMap<>();

    public ExporterRegistry(List<Exporter> exporterList) {
        exporterList.forEach(e -> exporters.put(e.type(), e));
    }

    public Exporter get(String type) {
        Exporter exporter = exporters.get(type);
        if (exporter == null) throw new IllegalArgumentException("Unknown exporter type: " + type);
        return exporter;
    }

    public void register(Exporter exporter) { exporters.put(exporter.type(), exporter); }
}
```

- [ ] **Step 5: 运行测试确认通过** (Tests run: 3, Failures: 0) → **Step 6: 提交**

---

### Task 4: ExportWorker — @Scheduled 轮询（使用 JsonUtils.parse）

**Files:**
- Create: `src/main/java/com/tightening/export/ExportWorker.java`
- Create: `src/test/java/com/tightening/export/ExportWorkerTest.java`

**关键变更**: 使用 `JsonUtils.parse()`（非 `fromJson`），`processPending()` 外层包 try-catch

- [ ] **Step 1: 写失败测试**（见前版）→ **Step 2: 运行确认失败** → **Step 3: 实现 ExportWorker.java**

```java
package com.tightening.export;

import com.tightening.entity.ExportTask;
import com.tightening.service.ExportTaskService;
import com.tightening.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportWorker {

    private final ExportTaskService exportTaskService;
    private final ExporterRegistry exporterRegistry;

    @Scheduled(fixedDelay = 5000)
    public void processPending() {
        try {
            doProcess();
        } catch (Exception e) {
            log.error("ExportWorker.processPending failed", e);
        }
    }

    private void doProcess() {
        List<ExportTask> tasks = exportTaskService.findPending(10);
        if (tasks.isEmpty()) return;

        log.debug("Processing {} pending export tasks", tasks.size());
        for (ExportTask task : tasks) {
            try {
                exportTaskService.markProcessing(task.getId());
                Exporter exporter = exporterRegistry.get(task.getType());
                @SuppressWarnings("unchecked")
                var data = JsonUtils.parse(task.getPayload(), java.util.Map.class);
                ExportPayload payload = new ExportPayload(
                        task.getMissionRecordId(),
                        task.getType(),
                        data);
                ExportResult result = exporter.execute(payload);
                if (result.success()) {
                    exportTaskService.markCompleted(task.getId());
                    log.info("Export task {} completed: {}", task.getId(), result.message());
                } else {
                    int newRetry = task.getRetryCount() + 1;
                    exportTaskService.markFailed(task.getId(), result.message(), newRetry, task.getMaxRetries());
                    log.warn("Export task {} returned failure: {}", task.getId(), result.message());
                }
            } catch (Exception e) {
                int newRetry = task.getRetryCount() + 1;
                exportTaskService.markFailed(task.getId(), e.getMessage(), newRetry, task.getMaxRetries());
                log.error("Export task {} failed (retry {}/{}): {}",
                        task.getId(), newRetry, task.getMaxRetries(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过** → **Step 5: 提交**

---

### Task 5: 三个内置 Exporter 实现（均为 Stub）

**Files:**
- Create: `src/main/java/com/tightening/export/StandardExcelExporter.java`
- Create: `src/main/java/com/tightening/export/OuterDatabaseStorer.java`
- Create: `src/main/java/com/tightening/export/PlcResultSender.java`
- Create: `src/test/java/com/tightening/export/ExporterStubTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("内置 Exporter（Stub）")
class ExporterStubTest {

    @Test
    @DisplayName("StandardExcelExporter type=standard_excel")
    void standardExcelExporterShouldWork() {
        Exporter e = new StandardExcelExporter();
        assertThat(e.type()).isEqualTo("standard_excel");
        assertThat(e.execute(new ExportPayload(1L, "standard_excel", Map.of())).success()).isTrue();
    }

    @Test
    @DisplayName("OuterDatabaseStorer type=outer_db_store")
    void outerDatabaseStorerShouldWork() {
        Exporter e = new OuterDatabaseStorer();
        assertThat(e.type()).isEqualTo("outer_db_store");
        assertThat(e.execute(new ExportPayload(1L, "outer_db_store", Map.of())).success()).isTrue();
    }

    @Test
    @DisplayName("PlcResultSender type=send_plc_result")
    void plcResultSenderShouldWork() {
        Exporter e = new PlcResultSender();
        assertThat(e.type()).isEqualTo("send_plc_result");
        assertThat(e.execute(new ExportPayload(1L, "send_plc_result", Map.of())).success()).isTrue();
    }
}
```

- [ ] **Step 2: 运行确认失败** → **Step 3-5: 逐一实现 3 个 stub**（每个 `@Component`，`execute()` 打 log 返回 ok）→ **Step 6: 运行确认通过** → **Step 7: 提交**

---

### Task 6: settings.yml + LocalSettings（@Component, prefix=tightening）

**Files:**
- Create: `src/main/java/com/tightening/config/LocalSettings.java`
- Create: `src/main/java/com/tightening/config/YamlPropertySourceFactory.java`
- Create: `src/test/java/com/tightening/config/LocalSettingsTest.java`

**关键变更**: `@Component`（非 `@Configuration`），prefix `tightening`，删除 `defaultConfig()`

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalSettings 配置")
class LocalSettingsTest {

    @Test
    @DisplayName("compact constructor 为 exportTypes 提供默认值")
    void shouldDefaultExportTypes() {
        LocalSettings settings = new LocalSettings(false, null);
        assertThat(settings.exportTypes()).containsExactly("standard_excel");
    }

    @Test
    @DisplayName("selfLoopEnabled 默认 false")
    void shouldDefaultSelfLoopToFalse() {
        LocalSettings settings = new LocalSettings(false, null);
        assertThat(settings.selfLoopEnabled()).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试确认失败** → **Step 3: 实现 YamlPropertySourceFactory.java**（同前版）

- [ ] **Step 4: 实现 LocalSettings.java**

```java
package com.tightening.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@PropertySource(
    value = "file:${user.home}/tightening_system/settings.yml",
    factory = YamlPropertySourceFactory.class,
    ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "tightening")
public record LocalSettings(boolean selfLoopEnabled, List<String> exportTypes) {

    public LocalSettings {
        if (exportTypes == null || exportTypes.isEmpty()) {
            exportTypes = List.of("standard_excel");
        }
    }
}
```

settings.yml 示例：

```yaml
# ~/tightening_system/settings.yml — 部署本地化配置
tightening:
  self-loop-enabled: true
  export-types:
    - standard_excel
    - outer_db_store
```

- [ ] **Step 5: 运行测试确认通过** → **Step 6: 提交**

---

### Task 7: FINALIZATION Capabilities — CancelTasks + LockTools + ResetState

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/CancelTasks.java`
- Create: `src/main/java/com/tightening/lifecycle/capability/LockTools.java`
- Create: `src/main/java/com/tightening/lifecycle/capability/ResetState.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/CancelTasksTest.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/LockToolsTest.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/ResetStateTest.java`

**关键变更**: LockTools 使用 `whenComplete` 记录锁失败日志

- [ ] **Step 1-3: 测试 CancelTasksTest, LockToolsTest, ResetStateTest**（CancelTasks/ResetState 测试同前版）

- [ ] **Step 4: 实现 LockTools.java（whenComplete 记录失败）**

```java
package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ITool;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LockTools implements Capability {

    @Override public String id() { return "LockTools"; }
    @Override public Stage stage() { return Stage.FINALIZATION; }
    @Override public SubState subState() { return SubState.LOCKING_TOOLS; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        for (ITool tool : ctx.getDeviceRegistry().values()) {
            log.info("LockTools: locking deviceId={}", tool.id());
            tool.sendLock().whenComplete((ok, ex) -> {
                if (ex != null || !Boolean.TRUE.equals(ok)) {
                    log.warn("LockTools: deviceId={} lock failed (ok={})", tool.id(), ok, ex);
                }
            });
        }
        return CapabilityResult.Pass;
    }
}
```

- [ ] **Step 5-8: CancelTasks + ResetState 实现（同前版）** → **提交**

---

### Task 8: ExportData Capability（从 settings.yml 读取导出类型）

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/capability/ExportData.java`
- Create: `src/test/java/com/tightening/lifecycle/capability/ExportDataTest.java`

**Interfaces:**
- Consumes: `Capability`, `MissionContext`, `ExportTaskService` (Task 2), `LocalSettings` (Task 6)
- Produces: `ExportData` (FINALIZATION/EXPORTING, pri=0) — 按 `settings.exportTypes` 列表逐条创建 export_task

- [ ] **Step 1: 写失败测试**

```java
package com.tightening.lifecycle.capability;

import com.tightening.config.LocalSettings;
import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.ExportTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportData Capability")
class ExportDataTest {

    @Mock private ExportTaskService exportTaskService;

    @Test
    @DisplayName("按 settings exportTypes 列表创建多条 export_task")
    void shouldCreateTasksPerExportType() {
        LocalSettings settings = new LocalSettings(false, List.of("standard_excel", "outer_db_store"));
        ExportData cap = new ExportData(exportTaskService, settings);
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of()).deviceRegistry(Map.of())
                .shouldSelfLoop(false)
                .missionRecord(new MissionRecord().setId(42L)).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        verify(exportTaskService).createTask(eq("standard_excel"), eq(42L), anyString());
        verify(exportTaskService).createTask(eq("outer_db_store"), eq(42L), anyString());
    }

    @Test
    @DisplayName("无 MissionRecord 时 precondition 返回 false")
    void shouldSkipWhenNoRecord() {
        ExportData cap = new ExportData(exportTaskService, new LocalSettings(false, null));
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of()).deviceRegistry(Map.of())
                .shouldSelfLoop(false).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }
}
```

- [ ] **Step 2: 运行确认失败** → **Step 3: 实现**（`@RequiredArgsConstructor`，注入 `ExportTaskService` + `LocalSettings`，`execute()` 中 `JsonUtils.toJson` + 按 `settings.exportTypes()` 循环调 `createTask`）→ **Step 4: 运行确认通过** → **Step 5: 提交**

---

### Task 9: 接线 — LifecycleEngineFactory 注入 FINALIZATION Capabilities

**Files:**
- Modify: `src/main/java/com/tightening/lifecycle/LifecycleEngineFactory.java`
- Update: `src/test/java/com/tightening/lifecycle/LifecycleEngineFactoryTest.java`

**Interfaces:**
- Consumes: Tasks 7-8 (FINALIZATION caps), `ExportTaskService`, `LocalSettings`
- Produces: Updated `createEngine()` with full pipeline (ACTIVATION+OPERATION+FINALIZATION caps)

- [ ] **Step 1: 修改 LifecycleEngineFactory.java** — 注入 `ExportTaskService` + `LocalSettings`，追加 4 个 FINALIZATION Capability 到列表

```java
@Component
@RequiredArgsConstructor
public class LifecycleEngineFactory {
    private final MissionRecordService missionRecordService;
    private final TighteningDataService tighteningDataService;
    private final ExportTaskService exportTaskService;
    private final LocalSettings settings;
    private final Map<DeviceType, JudgmentStrategy> judgmentStrategies;

    public LifecycleEngine createEngine(ProductMission mission, List<ProductBolt> bolts,
            Map<Long, ITool> deviceMap, boolean shouldSelfLoop) {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(mission.getId()).missionData(mission)
            .boltConfigs(bolts).deviceRegistry(deviceMap)
            .shouldSelfLoop(shouldSelfLoop).build();
        PipelineDefinition pipeline = PipelineDefinition.createDefault();

        List<Capability> capabilities = List.of(
            new PrepareBolts(), new CreateMissionRecord(missionRecordService),
            new SendArrangerSignal(), new SendSetterSelector(), new SendPSet(),
            new BoltBarCodeCheck(), new ControllerStatusCheck(),
            new ExecuteJudgment(judgmentStrategies), new StoreData(tighteningDataService),
            new AdvanceBolt(missionRecordService),
            new CancelTasks(), new LockTools(), new ResetState(),
            new ExportData(exportTaskService, settings));

        LifecycleEngine engine = new LifecycleEngine(pipeline, missionRecordService,
            capabilities, List.of(new LockStateMonitor(), new DeviceConnectionMonitor()));
        engine.initContext(ctx);
        engine.onFaulted(reason -> log.warn("Engine faulted (no orchestrator): {}", reason));
        engine.onCompleted(recordId -> log.info("Engine completed (no orchestrator): recordId={}", recordId));
        return engine;
    }
}
```

- [ ] **Step 2: 更新 LifecycleEngineFactoryTest** — @Mock `ExportTaskService` + `LocalSettings`，构造传入 factory
- [ ] **Step 3: 运行测试确认通过** → **Step 4: 运行全量测试** → **Step 5: 提交**

---

### Task 10: DataRouter 接口 + MissionOrchestrator（事件驱动自循环 + 数据路由）

**Files:**
- Create: `src/main/java/com/tightening/lifecycle/DataRouter.java`
- Create: `src/main/java/com/tightening/lifecycle/MissionOrchestrator.java`
- Create: `src/main/java/com/tightening/lifecycle/MissionCompletedEvent.java`
- Create: `src/main/java/com/tightening/controller/MissionLifecycleController.java`
- Create: `src/test/java/com/tightening/lifecycle/MissionOrchestratorTest.java`
- Modify: `src/main/java/com/tightening/device/DeviceRegistry.java`
- Update: `src/test/java/com/tightening/device/DeviceRegistryTest.java`

**Interfaces:**
- Consumes: `LifecycleEngineFactory`, `DeviceRegistry`, `LocalSettings`, `ApplicationEventPublisher`
- Produces: `DataRouter` (interface), `MissionOrchestrator` (implements DataRouter), `MissionCompletedEvent`, `MissionLifecycleController`

- [ ] **Step 1: 实现 DataRouter.java（接口，消除循环依赖）**

```java
package com.tightening.lifecycle;

import com.tightening.dto.TighteningDataDTO;

public interface DataRouter {
    void routeTighteningData(long deviceId, TighteningDataDTO dto);
}
```

- [ ] **Step 2: 实现 MissionCompletedEvent.java（事件 record）**

```java
package com.tightening.lifecycle;

import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;

import java.util.List;

public record MissionCompletedEvent(
    Long missionId,
    ProductMission mission,
    List<ProductBolt> bolts,
    boolean ok) {
}
```

- [ ] **Step 3: 修改 DeviceRegistry.java — 依赖 DataRouter 接口**

```java
// 新增 import
import com.tightening.lifecycle.DataRouter;

// 依赖改为接口（不再需要 @Lazy）
private final DataRouter dataRouter;

public DeviceRegistry(DeviceHandlerFactory handlerFactory, DataRouter dataRouter) {
    this.handlerFactory = handlerFactory;
    this.dataRouter = dataRouter;
}

// registerTool() 中新增
toolAdapter.onTighteningData(dto -> dataRouter.routeTighteningData(device.getId(), dto));
```

- [ ] **Step 4: DeviceRegistryTest 适配**

```java
// 新增 @Mock
@Mock
private DataRouter dataRouter;

@BeforeEach
void setUp() {
    registry = new DeviceRegistry(handlerFactory, dataRouter);
}
// 所有原有测试不变
```

- [ ] **Step 5: 写 MissionOrchestrator 测试**

```java
package com.tightening.lifecycle;

import com.tightening.config.LocalSettings;
import com.tightening.constant.DeviceType;
import com.tightening.constant.MissionResult;
import com.tightening.device.DeviceRegistry;
import com.tightening.device.contract.ITool;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.service.ExportTaskService;
import com.tightening.service.MissionRecordService;
import com.tightening.service.TighteningDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionOrchestrator")
class MissionOrchestratorTest {

    @Mock private MissionRecordService missionRecordService;
    @Mock private TighteningDataService tighteningDataService;
    @Mock private ExportTaskService exportTaskService;
    @Mock private JudgmentStrategy judgmentStrategy;
    @Mock private DeviceRegistry deviceRegistry;
    @Mock private LocalSettings settings;
    @Mock private ApplicationEventPublisher publisher;

    private LifecycleEngineFactory factory;
    private MissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(settings.exportTypes()).thenReturn(List.of("standard_excel"));
        factory = new LifecycleEngineFactory(
            missionRecordService, tighteningDataService, exportTaskService, settings,
            Map.of(DeviceType.ATLAS_PF4000, judgmentStrategy));
        orchestrator = new MissionOrchestrator(factory, deviceRegistry, settings, publisher);
    }

    @Test
    @DisplayName("startMission 创建并启动引擎")
    void shouldCreateAndStartEngine() throws InterruptedException {
        when(missionRecordService.createRecord(anyLong(), any(), anyInt()))
            .thenReturn(new MissionRecord().setId(42L));
        when(deviceRegistry.getAllTools()).thenReturn(List.of());

        ProductMission mission = new ProductMission().setId(1L);
        LifecycleEngine engine = orchestrator.startMission(mission, List.of());

        assertThat(engine).isNotNull();
        assertThat(engine.isAlive()).isTrue();
        engine.interrupt("test done");
        Thread.sleep(300);
    }

    @Test
    @DisplayName("OK 且 selfLoopEnabled 时发布 MissionCompletedEvent")
    void shouldPublishEventOnOkCompletion() throws Exception {
        when(missionRecordService.createRecord(anyLong(), any(), anyInt()))
            .thenReturn(new MissionRecord().setId(42L).setMissionResult(MissionResult.OK.getCode()));
        when(deviceRegistry.getAllTools()).thenReturn(List.of());
        when(settings.selfLoopEnabled()).thenReturn(true);

        ProductMission mission = new ProductMission().setId(1L);
        orchestrator.startMission(mission, List.of());

        // 等待引擎完成（会被 interrupt 加速）
        Thread.sleep(500);
        verify(publisher, timeout(3000)).publishEvent(any(MissionCompletedEvent.class));
    }

    @Test
    @DisplayName("NG 时不允许自循环")
    void shouldNotSelfLoopOnNg() throws Exception {
        when(missionRecordService.createRecord(anyLong(), any(), anyInt()))
            .thenReturn(new MissionRecord().setId(42L).setMissionResult(MissionResult.NG.getCode()));
        when(deviceRegistry.getAllTools()).thenReturn(List.of());
        when(settings.selfLoopEnabled()).thenReturn(true);

        ProductMission mission = new ProductMission().setId(1L);
        orchestrator.startMission(mission, List.of());

        Thread.sleep(500);
        verify(publisher, timeout(3000).times(0)).publishEvent(any(MissionCompletedEvent.class));
    }
}
```

- [ ] **Step 6: 运行测试确认失败** → **Step 7: 实现 MissionOrchestrator.java**

```java
package com.tightening.lifecycle;

import com.tightening.config.LocalSettings;
import com.tightening.constant.MissionResult;
import com.tightening.device.DeviceRegistry;
import com.tightening.device.contract.ITool;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.message.DeviceEvent;
import com.tightening.util.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MissionOrchestrator implements DataRouter {

    private static final int MAX_SELF_LOOPS = 1000;

    private final LifecycleEngineFactory factory;
    private final DeviceRegistry deviceRegistry;
    private final LocalSettings settings;
    private final ApplicationEventPublisher publisher;

    /** missionId → 活跃引擎 */
    private final Map<Long, LifecycleEngine> activeEngines = new ConcurrentHashMap<>();
    /** deviceId → missionId（数据路由用） */
    private final Map<Long, Long> deviceToMissionId = new ConcurrentHashMap<>();
    /** 自循环计数 */
    private final Map<Long, Integer> selfLoopCounts = new ConcurrentHashMap<>();

    public MissionOrchestrator(LifecycleEngineFactory factory,
                               DeviceRegistry deviceRegistry,
                               LocalSettings settings,
                               ApplicationEventPublisher publisher) {
        this.factory = factory;
        this.deviceRegistry = deviceRegistry;
        this.settings = settings;
        this.publisher = publisher;
    }

    // === DataRouter 接口实现 ===

    @Override
    public void routeTighteningData(long deviceId, TighteningDataDTO dto) {
        Long missionId = deviceToMissionId.get(deviceId);
        if (missionId == null) {
            log.trace("No active mission for deviceId={}, ignoring", deviceId);
            return;
        }
        LifecycleEngine engine = activeEngines.get(missionId);
        if (engine == null) {
            log.trace("Engine for missionId={} not alive, ignoring", missionId);
            return;
        }
        TighteningData data = Converter.dto2Entity(dto, TighteningData::new);
        engine.postMessage(new DeviceEvent.TighteningDataReceived(data, deviceId));
    }

    // === 引擎生命周期管理 ===

    public LifecycleEngine startMission(ProductMission mission, List<ProductBolt> bolts) {
        int loopCount = selfLoopCounts.getOrDefault(mission.getId(), 0);
        if (loopCount >= MAX_SELF_LOOPS) {
            log.warn("Mission {} reached maxSelfLoops, not restarting", mission.getId());
            return null;
        }
        selfLoopCounts.put(mission.getId(), loopCount + 1);

        Map<Long, ITool> devices = deviceRegistry.getAllTools().stream()
                .collect(java.util.stream.Collectors.toMap(ITool::id, t -> t));
        devices.keySet().forEach(deviceId -> deviceToMissionId.put(deviceId, mission.getId()));

        boolean shouldSelfLoop = settings.selfLoopEnabled();
        LifecycleEngine engine = factory.createEngine(mission, bolts, devices, shouldSelfLoop);

        engine.onCompleted(recordId -> {
            boolean ok = isMissionOk(engine);
            cleanup(mission.getId());
            if (shouldSelfLoop && ok) {
                log.info("Self-loop: publishing event for mission {}", mission.getId());
                publisher.publishEvent(new MissionCompletedEvent(
                        mission.getId(), mission, bolts, true));
            } else {
                selfLoopCounts.remove(mission.getId());
                log.info("Mission {} completed, recordId={}", mission.getId(), recordId);
            }
        });

        engine.onFaulted(reason -> {
            cleanup(mission.getId());
            selfLoopCounts.remove(mission.getId());
            log.warn("Mission {} faulted: {}", mission.getId(), reason);
        });

        activeEngines.put(mission.getId(), engine);
        engine.startMonitorTicks();
        engine.start(engine.getContext());
        log.info("Mission {} started (selfLoop={}, loopCount={})",
                mission.getId(), shouldSelfLoop, loopCount);
        return engine;
    }

    // === 事件监听：自循环重启（@Async 独立线程，不递归） ===

    @Async
    @TransactionalEventListener
    void handleRestart(MissionCompletedEvent event) {
        if (!event.ok()) return;
        log.info("Restarting mission {} (loop {})", event.missionId(),
                selfLoopCounts.getOrDefault(event.missionId(), 0));
        startMission(event.mission(), event.bolts());
    }

    // === 内部辅助 ===

    private void cleanup(Long missionId) {
        activeEngines.remove(missionId);
        deviceToMissionId.values().removeIf(v -> v.equals(missionId));
    }

    private boolean isMissionOk(LifecycleEngine engine) {
        MissionContext ctx = engine.getContext();
        if (ctx == null || ctx.getMissionRecord() == null) return false;
        return Integer.valueOf(MissionResult.OK.getCode())
                .equals(ctx.getMissionRecord().getMissionResult());
    }

    public Optional<LifecycleEngine> getActiveEngine(Long missionId) {
        return Optional.ofNullable(activeEngines.get(missionId));
    }

    public void postMessage(Long missionId, InboundMessage msg) {
        LifecycleEngine engine = activeEngines.get(missionId);
        if (engine != null) {
            engine.postMessage(msg);
        } else {
            log.debug("No active engine for missionId={}", missionId);
        }
    }
}
```

- [ ] **Step 8: 实现 MissionLifecycleController.java**

```java
package com.tightening.controller;

import com.tightening.dto.ApiResponse;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionOrchestrator;
import com.tightening.service.ProductMissionService;
import com.tightening.service.ProductBoltService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
public class MissionLifecycleController {

    private final MissionOrchestrator orchestrator;
    private final ProductMissionService missionService;
    private final ProductBoltService boltService;

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Long>> activateMission(@PathVariable Long id) {
        ProductMission mission = missionService.getById(id);
        if (mission == null) {
            return ResponseEntity.ok(ApiResponse.fail("mission not found: " + id));
        }
        List<ProductBolt> bolts = boltService.listByMissionId(id);
        if (bolts.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.fail("mission has no bolts: " + id));
        }
        var engine = orchestrator.startMission(mission, bolts);
        return ResponseEntity.ok(ApiResponse.ok(mission.getId()));
    }
}
```

- [ ] **Step 9: 运行测试确认通过** → **Step 10: 提交**

```bash
git add src/main/java/com/tightening/lifecycle/DataRouter.java \
        src/main/java/com/tightening/lifecycle/MissionOrchestrator.java \
        src/main/java/com/tightening/lifecycle/MissionCompletedEvent.java \
        src/test/java/com/tightening/lifecycle/MissionOrchestratorTest.java \
        src/main/java/com/tightening/device/DeviceRegistry.java \
        src/test/java/com/tightening/device/DeviceRegistryTest.java \
        src/main/java/com/tightening/controller/MissionLifecycleController.java
git commit -m "feat: add DataRouter, MissionOrchestrator with event-driven self-loop, MissionLifecycleController"
```

---

### Task 11: ToolHandler 锁语义重命名（收尾任务）

**Files:**
- Modify: `src/main/java/com/tightening/device/handler/ToolHandler.java`
- Modify: `src/main/java/com/tightening/device/DeviceHolder.java`
- Modify: `src/main/java/com/tightening/device/contract/ToolAdapter.java`
- Modify: `src/main/java/com/tightening/controller/DeviceController.java`
- Modify: `src/main/java/com/tightening/config/ToolCommonConfig.java`
- Modify: `src/main/java/com/tightening/netty/protocol/handler/atlas/AtlasPFSeriesInitHandler.java`
- Modify: `src/main/java/com/tightening/netty/protocol/handler/fit/FitSeriesInitHandler.java`
- Modify: `src/main/resources/application.yml`（或 `application-dev.yml`）
- Modify: `src/test/java/com/tightening/device/contract/ToolAdapterTest.java`
- Modify: `src/test/java/com/tightening/device/handler/ToolHandlerTest.java`
- Modify: `src/test/java/com/tightening/device/DeviceHolderTest.java`
- Modify: `src/test/java/com/tightening/controller/DeviceControllerTest.java`
- Modify: `src/test/java/com/tightening/netty/protocol/handler/atlas/AtlasPFSeriesInitHandlerTest.java`
- Modify: `src/test/java/com/tightening/netty/protocol/handler/fit/FitSeriesInitHandlerTest.java`

**重命名表（纯 rename，语义不变，布尔方向不变）：**

| 旧名 | 新名 |
|------|------|
| `enableToolOp()` | `unlock()` |
| `disableToolOp()` | `lock()` |
| `forceEnableToolOp()` | `forceUnlock()` |
| `forceDisableToolOp()` | `forceLock()` |
| `isToolEnabled()` | `isUnlocked()` |
| `isToolEnabled` (field) | `isUnlocked` (field) |
| `lastEnableTime` (field) | `lastUnlockTime` (field) |
| `lastDisableTime` (field) | `lastLockTime` (field) |
| `enableDisableCooldownMs` (config field) | `lockUnlockCooldownMs` (config field) |
| `enable-disable-cooldown-ms` (yml key) | `lock-unlock-cooldown-ms` (yml key) |

**ToolAdapter 映射（语义不变 — sendLock 仍然是解锁工具）：**

```java
sendLock() → handler.unlock(device.getId())
sendUnlock() → handler.lock(device.getId())
```

- [ ] **Step 1: IDE 重命名** → **Step 2: 编译 + 测试验证** → **Step 3: 提交**

---

## 验证

```bash
mvn clean test -DfailIfNoTests=false
```

## 文件统计

**新建: 22 源文件 + 7 测试文件 = 29 文件，修改 ~7 源文件 + ~7 测试文件，零删除**

## 已知的后续未实现

### Capability

| Capability | 位置 | 说明 |
|------------|------|------|
| WorkstationConfigCheck | VALIDATION/VALIDATING | 工作台配置校验 |
| ReceiveData | OPERATION/TIGHTENING_RECEIVED | 拧紧数据接收 |
| TorqueRangeCheck | OPERATION/JUDGING | 扭矩范围判定 |
| AngleRangeCheck | OPERATION/JUDGING | 角度范围判定 |

### Exporter / Outbox

| 项目 | 说明 |
|------|------|
| ExportData payload 扩展 | 当前只传 `missionRecordId`，需补充 missionId、missionResult、boltCount、tighteningDataCount 等上下文数据，供 Exporter 生成完整导出内容 |
| StandardExcelExporter 实现 | 当前为 stub（仅 log），后续实现真正的 Excel 文件生成 |
| OuterDatabaseStorer 实现 | 当前为 stub，后续实现外部数据库写入 |
| PlcResultSender 实现 | 当前为 stub，后续实现 PLC 结果回传 |
| export_task TTL 清理 | 已完成/失败的导出任务无限累积，无定期清理机制 |

### 出站消息

| 项目 | 说明 |
|------|------|
| 前端通知架构 | 引擎事件（锁失败、任务完成、异常）无法推送到前端。需设计出站消息通道（WebSocket / SSE / comet） |
| MissionStatus DTO | 当前 `GET /api/missions/{id}/status` 返回简单字符串，后续替换为结构化 `MissionStatus` record（stage、subState、currentBoltIndex、totalBolts、missionRecordId） |

### 数据流

| 项目 | 说明 |
|------|------|
| ToolHandler 双写移除 | 当前拧紧数据同时直接存 DB（ToolHandler）和经过引擎管道存 DB（StoreData Capability），Stage 4 应移除 ToolHandler 侧的直写 |
| LifecycleEngine inbox 背压回调 | inbox 有界（1024）后 offer() 失败只打 WARN，无降级策略 |
