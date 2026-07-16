package com.tightening.controller;

import com.tightening.dto.ApiResponse;
import com.tightening.dto.MissionStatus;
import com.tightening.lifecycle.LifecycleEngine;
import com.tightening.lifecycle.MissionContext;
import com.tightening.lifecycle.MissionOrchestrator;
import com.tightening.service.ProductBoltService;
import com.tightening.service.ProductMissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionLifecycleController")
class MissionLifecycleControllerTest {

    @Mock private MissionOrchestrator orchestrator;
    @Mock private ProductMissionService missionService;
    @Mock private ProductBoltService boltService;
    @InjectMocks private MissionLifecycleController controller;

    @Test
    @DisplayName("无活跃引擎时返回 idle")
    void shouldReturnIdleWhenNoEngine() {
        when(orchestrator.getActiveEngine(1L)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<MissionStatus>> response = controller.getMissionStatus(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        MissionStatus status = response.getBody().data();
        assertThat(status.status()).isEqualTo("idle");
        assertThat(status.stage()).isNull();
        assertThat(status.subState()).isNull();
        assertThat(status.currentBoltIndex()).isEqualTo(0);
        assertThat(status.totalBolts()).isEqualTo(0);
        assertThat(status.missionRecordId()).isNull();
    }

    @Test
    @DisplayName("活跃引擎时返回 running 状态和上下文信息")
    void shouldReturnRunningWhenEngineActive() {
        LifecycleEngine engine = mock(LifecycleEngine.class);
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L)
                .missionData(new com.tightening.entity.ProductMission())
                .boltConfigs(java.util.List.of())
                .deviceRegistry(java.util.Map.of())
                
                .build();
        ctx.setCurrentStage(com.tightening.constant.Stage.OPERATION);
        ctx.setCurrentSubState(com.tightening.constant.SubState.JUDGING);
        when(engine.getContext()).thenReturn(ctx);
        when(engine.isAlive()).thenReturn(true);
        when(orchestrator.getActiveEngine(1L)).thenReturn(Optional.of(engine));

        ResponseEntity<ApiResponse<MissionStatus>> response = controller.getMissionStatus(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        MissionStatus status = response.getBody().data();
        assertThat(status.status()).isEqualTo("running");
        assertThat(status.stage()).isEqualTo("OPERATION");
        assertThat(status.subState()).isEqualTo("JUDGING");
    }

    @Test
    @DisplayName("引擎已结束时返回 finished")
    void shouldReturnFinishedWhenEngineNotAlive() {
        LifecycleEngine engine = mock(LifecycleEngine.class);
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L)
                .missionData(new com.tightening.entity.ProductMission())
                .boltConfigs(java.util.List.of())
                .deviceRegistry(java.util.Map.of())
                
                .build();
        when(engine.getContext()).thenReturn(ctx);
        when(engine.isAlive()).thenReturn(false);
        when(orchestrator.getActiveEngine(1L)).thenReturn(Optional.of(engine));

        ResponseEntity<ApiResponse<MissionStatus>> response = controller.getMissionStatus(1L);

        MissionStatus status = response.getBody().data();
        assertThat(status.status()).isEqualTo("finished");
    }
}
