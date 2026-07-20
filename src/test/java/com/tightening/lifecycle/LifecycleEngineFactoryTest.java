package com.tightening.lifecycle;

import com.tightening.config.LocalSettings;
import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.DeviceType;
import com.tightening.device.contract.ITool;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.lifecycle.message.InboundCommand;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.tightening.entity.BoltPartsBarcode;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.BoltPartsBarcodeService;
import com.tightening.service.ExportTaskService;
import com.tightening.service.MissionRecordService;
import com.tightening.service.TighteningDataService;
import com.tightening.service.WorkplaceStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("LifecycleEngineFactory")
class LifecycleEngineFactoryTest {

    @Mock private MissionRecordService missionRecordService;
    @Mock private TighteningDataService tighteningDataService;
    @Mock private ExportTaskService exportTaskService;
    @Mock private LocalSettings settings;
    @Mock private JudgmentStrategy judgmentStrategy;
    @Mock private BarCodeMatchingRuleService barCodeMatchingRuleService;
    @Mock private WorkplaceStatusService workplaceStatusService;
    @Mock private BoltPartsBarcodeService partsBarcodeService;
    @Mock private ITool mockTool;

    private LifecycleEngineFactory factory;
    private LifecycleEngine testEngine;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(settings.exportTypes()).thenReturn(List.of("standard_excel"));
        lenient().when(mockTool.id()).thenReturn(1L);
        lenient().when(mockTool.type()).thenReturn(DeviceType.ATLAS_PF4000);
        var barcodeChain = (LambdaQueryChainWrapper<BoltPartsBarcode>) mock(LambdaQueryChainWrapper.class);
        lenient().when(barcodeChain.in(any(), anyCollection())).thenReturn(barcodeChain);
        lenient().when(barcodeChain.list()).thenReturn(List.of());
        lenient().when(partsBarcodeService.lambdaQuery()).thenReturn(barcodeChain);
        factory = new LifecycleEngineFactory(
            missionRecordService, tighteningDataService, exportTaskService, settings,
            Map.of(DeviceType.ATLAS_PF4000, judgmentStrategy), barCodeMatchingRuleService,
            partsBarcodeService, workplaceStatusService);
    }

    @AfterEach
    void tearDown() {
        if (testEngine != null && testEngine.isAlive()) {
            testEngine.shutdown();
        }
    }

    @Test
    @DisplayName("createEngine 返回已组装的引擎")
    void shouldCreateEngineWithContext() {
        var mission = new ProductMission();
        mission.setId(1L);
        var engine = factory.createEngine(mission, List.of(), Map.of(), null, null);

        assertThat(engine).isNotNull();
        assertThat(engine.getContext().getProductMissionId()).isEqualTo(1L);
        assertThat(engine.isAlive()).isFalse();
    }

    @Test
    @DisplayName("有 PRODUCT_TRACE 规则但未提供 productCode → trigger pipeline 应 fault")
    void shouldFaultWhenProductCodeRequiredButMissing() throws Exception {
        var rule = new BarCodeMatchingRule();
        rule.setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode());
        when(barCodeMatchingRuleService.listByMissionId(1L)).thenReturn(List.of(rule));

        var mission = new ProductMission();
        mission.setId(1L);
        var bolt = new ProductBolt();
        bolt.setSerialNum(1);
        bolt.setTorqueMin(1.0);
        bolt.setTorqueMax(10.0);

        testEngine = factory.createEngine(mission, List.of(bolt),
                Map.of(1L, mockTool), null, null);

        CountDownLatch triggered = new CountDownLatch(1);
        CountDownLatch faulted = new CountDownLatch(1);
        testEngine.onTriggered(mId -> triggered.countDown());
        testEngine.onFaulted(reason -> faulted.countDown());
        testEngine.start(testEngine.getContext());
        testEngine.postMessage(new InboundCommand.TriggerRequest(null, null));

        assertThat(faulted.await(3, TimeUnit.SECONDS))
                .as("onFaulted should fire when productCode missing but rule exists")
                .isTrue();
        assertThat(triggered.getCount())
                .as("onTriggered should NOT fire")
                .isEqualTo(1);
    }
}
