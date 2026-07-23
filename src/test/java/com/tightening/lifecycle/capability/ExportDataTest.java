package com.tightening.lifecycle.capability;

import com.tightening.config.LocalSettings;
import com.tightening.constant.TaskResult;
import com.tightening.entity.TaskRecord;
import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.ExportTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        TaskRecord record = new TaskRecord();
        record.setId(42L);
        record.setProductCode("P001");
        record.setIsRework(0);
        record.setTaskResult(TaskResult.OK.getCode());
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask())
                .boltConfigs(List.of()).deviceRegistry(Map.of())
                
                .taskRecord(record).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(exportTaskService).createTask(eq("standard_excel"), eq(42L), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("\"productCode\":\"P001\"");
        assertThat(payload).contains("\"isRework\":0");
        assertThat(payload).contains("\"timestamp\":");
        verify(exportTaskService).createTask(eq("outer_db_store"), eq(42L), anyString());
    }

    @Test
    @DisplayName("无 TaskRecord 时 precondition 返回 false")
    void shouldSkipWhenNoRecord() {
        ExportData cap = new ExportData(exportTaskService, new LocalSettings(false, null));
        TaskContext ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask())
                .boltConfigs(List.of()).deviceRegistry(Map.of())
                .build();
        assertThat(cap.precondition(ctx)).isFalse();
    }
}
