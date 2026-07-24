# ExportWorker 零轮询优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除 ExportWorker 每 5 秒的无效 DB 轮询，改为 Spring Event 触发 + @PostConstruct 启动兜底

**Architecture:** ExportTaskService 创建任务后发布 ExportTaskCreatedEvent，ExportWorker 通过 @Async @EventListener 异步消费，doProcess() 内部用 while 循环处理完所有 PENDING 任务才退出。@PostConstruct 覆盖进程重启遗留任务场景。

**Tech Stack:** Java 21, Spring Boot 3.5.10, Spring ApplicationEvent, @Async（已在 TighteningApplication 启用）

## Global Constraints

- 包名：`com.tightening.*`
- 使用 Lombok（`@RequiredArgsConstructor`, `@Slf4j`）
- 日志级别：`com.tightening: DEBUG`
- 严禁全限定类名，所有类型通过 import 导入
- `ExportData` 和 `LifecycleEngineFactory` 零改动
- @EnableAsync(proxyTargetClass = true) 已在 TighteningApplication 启用

---

### Task 1: 创建 ExportTaskCreatedEvent

**Files:**
- Create: `src/main/java/com/tightening/export/ExportTaskCreatedEvent.java`

**Interfaces:**
- Produces: `ExportTaskCreatedEvent` — 由 ExportTaskService 发布，ExportWorker 监听

- [ ] **Step 1: 创建事件类**

参考 `DeviceChangeEvent` 模式，继承 `ApplicationEvent`：

```java
package com.tightening.export;

import org.springframework.context.ApplicationEvent;

/**
 * 导出任务创建事件。ExportTaskService 写入 outbox 后发布，
 * ExportWorker 异步监听并触发处理。
 */
public class ExportTaskCreatedEvent extends ApplicationEvent {

    public ExportTaskCreatedEvent(Object source) {
        super(source);
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/tightening/export/ExportTaskCreatedEvent.java
git commit -m "feat: add ExportTaskCreatedEvent for outbox processing trigger

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: ExportTaskService 发布事件

**Files:**
- Modify: `src/main/java/com/tightening/service/ExportTaskService.java`

**Interfaces:**
- Consumes: `ExportTaskCreatedEvent` (from Task 1)
- Produces: `createTask()` 在 `save()` 后发布 `ExportTaskCreatedEvent`

- [ ] **Step 1: 注入 ApplicationEventPublisher 并发布事件**

在 `createTask()` 的 `save(task)` 之后添加事件发布。参照 `DeviceService` 的构造器注入模式：

```java
package com.tightening.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.ExportTaskStatus;
import com.tightening.entity.ExportTask;
import com.tightening.export.ExportTaskCreatedEvent;
import com.tightening.mapper.ExportTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ExportTaskService extends ServiceImpl<ExportTaskMapper, ExportTask> {

    private final ApplicationEventPublisher eventPublisher;

    public ExportTaskService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void createTask(String type, Long taskRecordId, String payload) {
        ExportTask task = new ExportTask()
                .setType(type)
                .setTaskRecordId(taskRecordId)
                .setPayload(payload)
                .setStatus(ExportTaskStatus.PENDING.getCode())
                .setRetryCount(0)
                .setMaxRetries(3);
        save(task);
        eventPublisher.publishEvent(new ExportTaskCreatedEvent(this));
    }

    // findPending, markProcessing, markCompleted, markFailed, cleanupTasks 不变
    // ... 省略，与当前代码完全一致
```

- [ ] **Step 2: 运行现有测试确认不破坏**

```bash
mvn test -pl . -Dtest="ExportTaskServiceTest,ExportTaskServiceCleanupTest" -DfailIfNoTests=false
```

Expected: PASS（如果不存在这些测试类则自然通过）

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/tightening/service/ExportTaskService.java
git commit -m "feat: publish ExportTaskCreatedEvent after outbox task creation

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: ExportWorker 改造

**Files:**
- Modify: `src/main/java/com/tightening/export/ExportWorker.java`

**Interfaces:**
- Consumes: `ExportTaskCreatedEvent` (from Task 1)
- Consumes: `ExportTaskService.findPending`, `markProcessing`, `markCompleted`, `markFailed`（已有接口，不变）
- Produces: `doProcess()` — package-private，供测试调用

- [ ] **Step 1: 写入改造后的 ExportWorker**

```java
package com.tightening.export;

import com.tightening.entity.ExportTask;
import com.tightening.service.ExportTaskService;
import com.tightening.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportWorker {

    private final ExportTaskService exportTaskService;
    private final ExporterRegistry exporterRegistry;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        doProcess();
    }

    @Async
    @EventListener
    public void onExportTaskCreated(ExportTaskCreatedEvent event) {
        doProcess();
    }

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

    @SuppressWarnings("unchecked")
    void doProcess() {
        if (!processing.compareAndSet(false, true)) {
            return;
        }
        try {
            List<ExportTask> tasks;
            while (!(tasks = exportTaskService.findPending(10)).isEmpty()) {
                log.debug("Processing {} pending export tasks", tasks.size());
                for (ExportTask task : tasks) {
                    processOne(task);
                }
            }
        } finally {
            processing.set(false);
        }
    }

    private void processOne(ExportTask task) {
        try {
            exportTaskService.markProcessing(task.getId());
            Exporter exporter = exporterRegistry.get(task.getType());
            Map<String, Object> data = JsonUtils.parse(task.getPayload(), Map.class);
            ExportPayload payload = new ExportPayload(
                    task.getTaskRecordId(),
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
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/tightening/export/ExportWorker.java
git commit -m "refactor: replace polling with event-driven outbox processing

- Remove @Scheduled(fixedDelay=5000) processPending() polling
- Add @PostConstruct init() for startup catch-up
- Add @Async @EventListener for event-driven triggering
- Add AtomicBoolean reentry guard
- While-loop until queue empty instead of single batch
- Extract processOne() for separation of concerns

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: 更新 ExportWorkerTest

**Files:**
- Modify: `src/test/java/com/tightening/export/ExportWorkerTest.java`

**Interfaces:**
- Consumes: `ExportWorker.doProcess()` (package-private, from Task 3)

- [ ] **Step 1: 写入更新后的测试**

所有测试从 `processPending()` 改为 `doProcess()`，mock 链式返回以适配 while 循环（返回数据后返回空列表终止循环）：

```java
package com.tightening.export;

import com.tightening.entity.ExportTask;
import com.tightening.service.ExportTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportWorker")
class ExportWorkerTest {

    @Mock
    private ExportTaskService exportTaskService;

    @Mock
    private ExporterRegistry exporterRegistry;

    @InjectMocks
    private ExportWorker exportWorker;

    @Test
    @DisplayName("没有 PENDING 任务时不执行任何导出")
    void shouldDoNothingWhenNoPendingTasks() {
        when(exportTaskService.findPending(10)).thenReturn(List.of());

        exportWorker.doProcess();

        verify(exportTaskService).findPending(10);
        verify(exportTaskService, never()).markProcessing(any());
        verify(exportTaskService, never()).markCompleted(any());
        verify(exportTaskService, never()).markFailed(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("导出成功时标记 COMPLETED")
    void shouldMarkCompletedOnSuccess() {
        ExportTask task = task(1L, "excel", 42L, "{\"k\":\"v\"}", 0, 3);
        when(exportTaskService.findPending(10))
                .thenReturn(List.of(task))
                .thenReturn(List.of());
        when(exporterRegistry.get("excel")).thenReturn(exporter(ExportResult.ok("done")));

        exportWorker.doProcess();

        verify(exportTaskService).markProcessing(1L);
        verify(exportTaskService).markCompleted(1L);
        verify(exportTaskService, never()).markFailed(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("导出返回失败时标记重试")
    void shouldMarkFailedWhenExporterReturnsFailure() {
        ExportTask task = task(1L, "excel", 42L, "{}", 0, 3);
        when(exportTaskService.findPending(10))
                .thenReturn(List.of(task))
                .thenReturn(List.of());
        when(exporterRegistry.get("excel")).thenReturn(exporter(ExportResult.fail("bad data")));

        exportWorker.doProcess();

        verify(exportTaskService).markProcessing(1L);
        verify(exportTaskService).markFailed(eq(1L), eq("bad data"), eq(1), eq(3));
        verify(exportTaskService, never()).markCompleted(any());
    }

    @Test
    @DisplayName("导出抛异常时标记重试")
    void shouldMarkFailedWhenExportThrows() {
        ExportTask task = task(1L, "excel", 42L, "{}", 0, 3);
        when(exportTaskService.findPending(10))
                .thenReturn(List.of(task))
                .thenReturn(List.of());
        when(exporterRegistry.get("excel")).thenThrow(new RuntimeException("conn lost"));

        exportWorker.doProcess();

        verify(exportTaskService).markProcessing(1L);
        verify(exportTaskService).markFailed(eq(1L), eq("conn lost"), eq(1), eq(3));
        verify(exportTaskService, never()).markCompleted(any());
    }

    @Test
    @DisplayName("多个任务依次处理，一个失败不影响其他")
    void shouldProcessMultipleTasksIndependently() {
        ExportTask task1 = task(1L, "excel", 42L, "{}", 0, 3);
        ExportTask task2 = task(2L, "pdf", 43L, "{}", 0, 3);
        when(exportTaskService.findPending(10))
                .thenReturn(List.of(task1, task2))
                .thenReturn(List.of());
        when(exporterRegistry.get("excel")).thenReturn(exporter(ExportResult.ok("done")));
        when(exporterRegistry.get("pdf")).thenReturn(exporter(ExportResult.fail("no template")));

        exportWorker.doProcess();

        verify(exportTaskService).markCompleted(1L);
        verify(exportTaskService).markFailed(eq(2L), eq("no template"), eq(1), eq(3));
    }

    /**
     * Helper: 创建 ExportTask，避免 Lombok 链式调用中 setId 返回 BaseEntity 的编译问题。
     */
    private static ExportTask task(Long id, String type, Long taskRecordId,
                                   String payload, int retryCount, int maxRetries) {
        ExportTask t = new ExportTask()
                .setType(type).setTaskRecordId(taskRecordId)
                .setPayload(payload).setStatus(0)
                .setRetryCount(retryCount).setMaxRetries(maxRetries);
        t.setId(id);
        return t;
    }

    /**
     * Helper: 创建一个返回固定结果的 Exporter 匿名实现。
     */
    private static Exporter exporter(ExportResult result) {
        return new Exporter() {
            @Override
            public String type() {
                return "";
            }

            @Override
            public ExportResult execute(ExportPayload payload) {
                return result;
            }
        };
    }
}
```

- [ ] **Step 2: 运行测试确认通过**

```bash
mvn test -pl . -Dtest="ExportWorkerTest" -DfailIfNoTests=false
```

Expected: 5 tests PASS

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/tightening/export/ExportWorkerTest.java
git commit -m "test: update ExportWorkerTest for event-driven doProcess()

- Replace processPending() calls with doProcess()
- Chain mock returns to terminate while-loop (data then empty)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: 全量测试验证

- [ ] **Step 1: 运行完整测试套件**

```bash
mvn test
```

Expected: All tests PASS

- [ ] **Step 2: 手动验证改动范围**

```bash
# 确认 ExportData 和 LifecycleEngineFactory 未被修改
git diff --name-only HEAD~4 HEAD | grep -E "ExportData|LifecycleEngineFactory"
```

Expected: 空输出（零改动）
