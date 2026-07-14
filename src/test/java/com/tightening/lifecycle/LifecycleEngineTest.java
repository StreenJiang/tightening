package com.tightening.lifecycle;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.capability.Capability;
import com.tightening.lifecycle.capability.CapabilityResult;
import com.tightening.lifecycle.capability.ErrorAction;
import com.tightening.lifecycle.message.*;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.MissionRecordService;
import com.tightening.service.WorkplaceStatusService;
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
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LifecycleEngine Actor 主循环")
class LifecycleEngineTest {

    @Mock
    private MissionRecordService missionRecordService;

    @Mock
    private BarCodeMatchingRuleService barCodeMatchingRuleService;

    @Mock
    private WorkplaceStatusService workplaceStatusService;

    private PipelineDefinition pd;
    private LifecycleEngine engine;

    @BeforeEach
    void setUp() {
        pd = PipelineDefinition.createDefault();
        engine = new LifecycleEngine(pd, missionRecordService, List.of(), List.of(), List.of(), workplaceStatusService);
    }

    @Test
    @DisplayName("start() 启动 Actor 线程并设置 alive=true")
    void shouldStartActorThread() throws InterruptedException {
        MissionContext ctx = minimalContext();
        engine.start(ctx);
        Thread.sleep(200);
        assertThat(engine.isAlive()).isTrue();
        engine.postMessage(new EngineInternal.Faulted("stop"));
        Thread.sleep(100);
    }

    @Test
    @DisplayName("Faulted 消息触发 onFaulted 回调")
    void shouldHandleFaultedMessage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        String[] reason = new String[1];
        engine.onFaulted(r -> { reason[0] = r; latch.countDown(); });

        MissionContext ctx = minimalContext();
        engine.start(ctx);
        engine.postMessage(new EngineInternal.Faulted("test crash"));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(reason[0]).isEqualTo("test crash");
    }

    @Test
    @DisplayName("interrupt() 在 FINALIZATION 阶段被忽略")
    void shouldNotInterruptDuringFinalization() throws InterruptedException {
        MissionContext ctx = minimalContext();
        ctx.setCurrentStage(Stage.FINALIZATION);
        engine.start(ctx);
        Thread.sleep(100);
        engine.interrupt("test");
        assertThat(ctx.isInterruptRequested()).isFalse();
        engine.postMessage(new EngineInternal.Faulted("stop"));
    }

    @Test
    @DisplayName("interrupt() 在非 FINALIZATION 阶段设置标志")
    void shouldInterruptOutsideFinalization() throws InterruptedException {
        MissionContext ctx = minimalContext();
        ctx.setCurrentStage(Stage.OPERATION);
        engine.start(ctx);
        Thread.sleep(100);
        engine.interrupt("user requested");
        Thread.sleep(100);
        assertThat(ctx.isInterruptRequested()).isTrue();
        engine.postMessage(new EngineInternal.Faulted("stop"));
    }

    @Test
    @DisplayName("TriggerRequest 初始化 BoltStates 并推进管道")
    void shouldTriggerAndAdvancePipeline() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Capability cap = mock(Capability.class);
        when(cap.id()).thenReturn("test");
        when(cap.stage()).thenReturn(Stage.ACTIVATION);
        when(cap.subState()).thenReturn(SubState.PREPARING);
        when(cap.priority()).thenReturn(0);
        when(cap.precondition(any())).thenReturn(true);
        when(cap.execute(any())).thenAnswer(inv -> {
            latch.countDown();
            return CapabilityResult.Pass;
        });

        PipelineDefinition customPd = PipelineDefinition.createDefault();
        customPd.registerCapability(cap).sortByPriority();
        engine = new LifecycleEngine(customPd, missionRecordService, List.of(cap), List.of(), List.of(), workplaceStatusService);

        ProductMission pm = new ProductMission();
        pm.setId(1L);
        ProductBolt bolt = new ProductBolt().setBoltSerialNum(1);
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L)
            .missionData(pm)
            .boltConfigs(List.of(bolt))
            .deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .build();

        engine.start(ctx);
        engine.postMessage(new InboundCommand.TriggerRequest(null, null));
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        engine.postMessage(new EngineInternal.Faulted("stop"));
    }

    @Test
    @DisplayName("handleActorCrash 保存 checkpoint 到 DB")
    void shouldSaveCheckpointOnCrash() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Capability cap = mock(Capability.class);
        when(cap.id()).thenReturn("crash-test");
        when(cap.stage()).thenReturn(Stage.OPERATION);
        when(cap.subState()).thenReturn(SubState.STORING);
        when(cap.priority()).thenReturn(0);
        when(cap.precondition(any())).thenReturn(true);
        when(cap.execute(any())).thenThrow(new RuntimeException("boom"));
        when(cap.onError(any(), any())).thenReturn(ErrorAction.FAIL_STAGE);

        PipelineDefinition customPd = PipelineDefinition.createDefault();
        customPd.registerCapability(cap).sortByPriority();
        engine = new LifecycleEngine(customPd, missionRecordService, List.of(cap), List.of(), List.of(), workplaceStatusService);
        engine.onFaulted(r -> latch.countDown());

        MissionRecord record = new MissionRecord();
        record.setId(42L);
        ProductMission pm2 = new ProductMission();
        pm2.setId(1L);
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L)
            .missionData(pm2)
            .boltConfigs(List.of(new ProductBolt().setBoltSerialNum(1)))
            .deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .missionRecord(record)
            .currentStage(Stage.OPERATION)
            .currentSubState(SubState.STORING)
            .build();

        engine.start(ctx);
        Thread.sleep(100);
        engine.postMessage(new InboundCommand.AdvancePipeline());

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        verify(missionRecordService, timeout(2000).atLeastOnce())
            .updateSnapshot(eq(42L), anyString());
    }

    private static MissionContext minimalContext() {
        ProductMission pm = new ProductMission();
        pm.setId(1L);
        return MissionContext.builder()
            .productMissionId(1L)
            .missionData(pm)
            .boltConfigs(List.of())
            .deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .build();
    }
}
