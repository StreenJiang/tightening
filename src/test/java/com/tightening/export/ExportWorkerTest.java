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

        exportWorker.processPending();

        verify(exportTaskService).findPending(10);
        verify(exportTaskService, never()).markProcessing(any());
        verify(exportTaskService, never()).markCompleted(any());
        verify(exportTaskService, never()).markFailed(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("导出成功时标记 COMPLETED")
    void shouldMarkCompletedOnSuccess() {
        ExportTask task = task(1L, "excel", 42L, "{\"k\":\"v\"}", 0, 3);
        when(exportTaskService.findPending(10)).thenReturn(List.of(task));
        when(exporterRegistry.get("excel")).thenReturn(exporter(ExportResult.ok("done")));

        exportWorker.processPending();

        verify(exportTaskService).markProcessing(1L);
        verify(exportTaskService).markCompleted(1L);
        verify(exportTaskService, never()).markFailed(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("导出返回失败时标记重试")
    void shouldMarkFailedWhenExporterReturnsFailure() {
        ExportTask task = task(1L, "excel", 42L, "{}", 0, 3);
        when(exportTaskService.findPending(10)).thenReturn(List.of(task));
        when(exporterRegistry.get("excel")).thenReturn(exporter(ExportResult.fail("bad data")));

        exportWorker.processPending();

        verify(exportTaskService).markProcessing(1L);
        verify(exportTaskService).markFailed(eq(1L), eq("bad data"), eq(1), eq(3));
        verify(exportTaskService, never()).markCompleted(any());
    }

    @Test
    @DisplayName("导出抛异常时标记重试")
    void shouldMarkFailedWhenExportThrows() {
        ExportTask task = task(1L, "excel", 42L, "{}", 0, 3);
        when(exportTaskService.findPending(10)).thenReturn(List.of(task));
        when(exporterRegistry.get("excel")).thenThrow(new RuntimeException("conn lost"));

        exportWorker.processPending();

        verify(exportTaskService).markProcessing(1L);
        verify(exportTaskService).markFailed(eq(1L), eq("conn lost"), eq(1), eq(3));
        verify(exportTaskService, never()).markCompleted(any());
    }

    @Test
    @DisplayName("多个任务依次处理，一个失败不影响其他")
    void shouldProcessMultipleTasksIndependently() {
        ExportTask task1 = task(1L, "excel", 42L, "{}", 0, 3);
        ExportTask task2 = task(2L, "pdf", 43L, "{}", 0, 3);
        when(exportTaskService.findPending(10)).thenReturn(List.of(task1, task2));
        when(exporterRegistry.get("excel")).thenReturn(exporter(ExportResult.ok("done")));
        when(exporterRegistry.get("pdf")).thenReturn(exporter(ExportResult.fail("no template")));

        exportWorker.processPending();

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
