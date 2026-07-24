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
