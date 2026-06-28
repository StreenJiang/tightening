package com.tightening.export;

import com.tightening.entity.ExportTask;
import com.tightening.service.ExportTaskService;
import com.tightening.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

    @SuppressWarnings("unchecked")
    private void doProcess() {
        List<ExportTask> tasks = exportTaskService.findPending(10);
        if (tasks.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending export tasks", tasks.size());
        for (ExportTask task : tasks) {
            try {
                exportTaskService.markProcessing(task.getId());
                Exporter exporter = exporterRegistry.get(task.getType());
                Map<String, Object> data = JsonUtils.parse(task.getPayload(), Map.class);
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
