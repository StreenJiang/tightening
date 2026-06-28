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
        MissionRecord record = new MissionRecord();
        record.setId(42L);
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of()).deviceRegistry(Map.of())
                .shouldSelfLoop(false)
                .missionRecord(record).build();
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
