package com.tightening.lifecycle;

import com.tightening.constant.DeviceType;
import com.tightening.device.DeviceRegistry;
import com.tightening.device.contract.ITool;
import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.ExportTaskService;
import com.tightening.service.MissionRecordService;
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
    @Mock private BarCodeMatchingRuleService barCodeMatchingRuleService;
    @Mock private WorkplaceStatusService workplaceStatusService;
    @Mock private ITool mockTool;

    private LifecycleEngineFactory factory;
    private MissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(settings.exportTypes()).thenReturn(List.of("standard_excel"));
        lenient().when(mockTool.id()).thenReturn(1L);
        lenient().when(mockTool.type()).thenReturn(DeviceType.ATLAS_PF4000);
        lenient().doNothing().when(exportTaskService).createTask(anyString(), anyLong(), anyString());
        factory = new LifecycleEngineFactory(
            missionRecordService, tighteningDataService, exportTaskService, settings,
            Map.of(DeviceType.ATLAS_PF4000, judgmentStrategy), barCodeMatchingRuleService,
            workplaceStatusService);
        orchestrator = new MissionOrchestrator(factory, deviceRegistry);
    }

    private static MissionRecord missionRecordWithId(long id, Integer missionResult) {
        MissionRecord r = new MissionRecord();
        r.setId(id);
        if (missionResult != null) r.setMissionResult(missionResult);
        return r;
    }

    private static ProductMission missionWithId(long id) {
        ProductMission m = new ProductMission();
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
        lenient().when(missionRecordService.createRecord(anyLong(), any(), anyInt()))
            .thenReturn(missionRecordWithId(42L, null));
        when(deviceRegistry.getAllTools()).thenReturn(List.of(mockTool));

        LifecycleEngine engine = orchestrator.trigger(
            missionWithId(1L), List.of(boltWithId(10L, 1)), null, null);

        assertThat(engine).isNotNull();
    }

    @Test
    @DisplayName("重复 trigger 同一 mission 返回 null")
    void shouldRejectDuplicateTrigger() {
        lenient().when(missionRecordService.createRecord(anyLong(), any(), anyInt()))
            .thenReturn(missionRecordWithId(42L, null));
        when(deviceRegistry.getAllTools()).thenReturn(List.of(mockTool));

        orchestrator.trigger(missionWithId(1L), List.of(boltWithId(10L, 1)), null, null);
        LifecycleEngine second = orchestrator.trigger(
            missionWithId(1L), List.of(boltWithId(10L, 1)), null, null);

        assertThat(second).isNull();
    }
}
