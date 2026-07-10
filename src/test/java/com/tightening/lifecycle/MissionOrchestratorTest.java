package com.tightening.lifecycle;

import com.tightening.config.LocalSettings;
import com.tightening.constant.DeviceType;
import com.tightening.constant.MissionResult;
import com.tightening.device.DeviceRegistry;
import com.tightening.device.contract.ITool;
import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.judgment.JudgmentResult;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.lifecycle.message.DeviceEvent;
import com.tightening.service.BarCodeMatchingRuleService;
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
import java.util.concurrent.CompletableFuture;

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
    @Mock private ApplicationEventPublisher publisher;
    @Mock private ITool mockTool;

    private LifecycleEngineFactory factory;
    private MissionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(settings.exportTypes()).thenReturn(List.of("standard_excel"));
        factory = new LifecycleEngineFactory(
            missionRecordService, tighteningDataService, exportTaskService, settings,
            Map.of(DeviceType.ATLAS_PF4000, judgmentStrategy), barCodeMatchingRuleService);
        orchestrator = new MissionOrchestrator(factory, deviceRegistry, settings, publisher);
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
    @DisplayName("startMission 创建并启动引擎")
    void shouldCreateAndStartEngine() throws InterruptedException {
        lenient().when(missionRecordService.createRecord(anyLong(), any(), anyInt()))
            .thenReturn(missionRecordWithId(42L, null));
        lenient().when(deviceRegistry.getAllTools()).thenReturn(List.of());

        LifecycleEngine engine = orchestrator.startMission(
            missionWithId(1L), List.of(boltWithId(10L, 1)));

        assertThat(engine).isNotNull();
        assertThat(engine.isAlive()).isTrue();
        engine.interrupt("test done");
        Thread.sleep(300);
    }

    @Test
    @DisplayName("OK 且 selfLoopEnabled 时发布 MissionCompletedEvent")
    void shouldPublishEventOnOkCompletion() throws Exception {
        when(missionRecordService.createRecord(anyLong(), any(), anyInt()))
            .thenReturn(missionRecordWithId(42L, MissionResult.OK.getCode()));
        when(deviceRegistry.getAllTools()).thenReturn(List.of(mockTool));
        when(mockTool.id()).thenReturn(1L);
        when(mockTool.type()).thenReturn(DeviceType.ATLAS_PF4000);
        when(mockTool.sendLock()).thenReturn(CompletableFuture.completedFuture(true));
        when(settings.selfLoopEnabled()).thenReturn(true);
        when(judgmentStrategy.judge(any())).thenReturn(JudgmentResult.ok());

        LifecycleEngine engine = orchestrator.startMission(
            missionWithId(1L), List.of(boltWithId(10L, 1)));

        TighteningData data = new TighteningData();
        data.setTighteningId(100L);
        data.setTighteningStatus(1);
        engine.postMessage(new DeviceEvent.TighteningDataReceived(data, 1L));

        verify(publisher, timeout(5000)).publishEvent(any(MissionCompletedEvent.class));
    }

    @Test
    @DisplayName("NG 时不允许自循环")
    void shouldNotSelfLoopOnNg() throws Exception {
        when(missionRecordService.createRecord(anyLong(), any(), anyInt()))
            .thenReturn(missionRecordWithId(42L, null));
        when(deviceRegistry.getAllTools()).thenReturn(List.of(mockTool));
        when(mockTool.id()).thenReturn(1L);
        when(mockTool.type()).thenReturn(DeviceType.ATLAS_PF4000);
        when(mockTool.sendLock()).thenReturn(CompletableFuture.completedFuture(true));
        when(settings.selfLoopEnabled()).thenReturn(true);
        when(judgmentStrategy.judge(any())).thenReturn(JudgmentResult.ng("Test NG"));

        LifecycleEngine engine = orchestrator.startMission(
            missionWithId(1L), List.of(boltWithId(10L, 1)));

        TighteningData data = new TighteningData();
        data.setTighteningId(100L);
        data.setTighteningStatus(1);
        engine.postMessage(new DeviceEvent.TighteningDataReceived(data, 1L));

        Thread.sleep(1000);
        verify(publisher, times(0)).publishEvent(any(MissionCompletedEvent.class));
    }
}
