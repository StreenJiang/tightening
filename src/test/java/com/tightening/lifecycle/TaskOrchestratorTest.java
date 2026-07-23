package com.tightening.lifecycle;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.tightening.constant.DeviceType;
import com.tightening.device.DeviceRegistry;
import com.tightening.device.contract.ITool;
import com.tightening.entity.BoltPartsBarcode;
import com.tightening.entity.TaskRecord;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.BoltPartsBarcodeService;
import com.tightening.service.SseService;
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
    @Mock private BoltPartsBarcodeService partsBarcodeService;
    @Mock private ITool mockTool;

    private LifecycleEngineFactory factory;
    private TaskOrchestrator orchestrator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(settings.exportTypes()).thenReturn(List.of("standard_excel"));
        lenient().when(mockTool.id()).thenReturn(1L);
        lenient().when(mockTool.type()).thenReturn(DeviceType.ATLAS_PF4000);
        lenient().doNothing().when(exportTaskService).createTask(anyString(), anyLong(), anyString());
        var barcodeChain = (LambdaQueryChainWrapper<BoltPartsBarcode>) mock(LambdaQueryChainWrapper.class);
        lenient().when(barcodeChain.in(any(), anyCollection())).thenReturn(barcodeChain);
        lenient().when(barcodeChain.list()).thenReturn(List.of());
        lenient().when(partsBarcodeService.lambdaQuery()).thenReturn(barcodeChain);
        factory = new LifecycleEngineFactory(
            taskRecordService, tighteningDataService, exportTaskService, settings,
            Map.of(DeviceType.ATLAS_PF4000, judgmentStrategy), barCodeMatchingRuleService,
            partsBarcodeService, workplaceStatusService);
        orchestrator = new TaskOrchestrator(factory, deviceRegistry, mock(SseService.class));
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
        b.setSerialNum(serialNum);
        return b;
    }

    @Test
    @DisplayName("trigger 成功创建引擎")
    void shouldCreateEngineOnTrigger() {
        lenient().when(taskRecordService.createRecord(anyLong(), any(), any(), anyInt()))
            .thenReturn(taskRecordWithId(42L, null));
        when(deviceRegistry.getAllTools()).thenReturn(List.of(mockTool));

        LifecycleEngine engine = orchestrator.trigger(
            taskWithId(1L), List.of(boltWithId(10L, 1)), null, null);

        assertThat(engine).isNotNull();
    }

    @Test
    @DisplayName("重复 trigger 同一 task 返回 null")
    void shouldRejectDuplicateTrigger() {
        lenient().when(taskRecordService.createRecord(anyLong(), any(), any(), anyInt()))
            .thenReturn(taskRecordWithId(42L, null));
        when(deviceRegistry.getAllTools()).thenReturn(List.of(mockTool));

        orchestrator.trigger(taskWithId(1L), List.of(boltWithId(10L, 1)), null, null);
        LifecycleEngine second = orchestrator.trigger(
            taskWithId(1L), List.of(boltWithId(10L, 1)), null, null);

        assertThat(second).isNull();
    }
}
