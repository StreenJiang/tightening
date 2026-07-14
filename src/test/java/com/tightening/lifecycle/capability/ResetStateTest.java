package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductMission;
import com.tightening.constant.LockReason;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResetState Capability")
class ResetStateTest {

    private ResetState cap;

    @BeforeEach
    void setUp() {
        cap = new ResetState();
    }

    @Test
    @DisplayName("id 返回 ResetState")
    void shouldReturnCorrectId() {
        assertThat(cap.id()).isEqualTo("ResetState");
    }

    @Test
    @DisplayName("stage 返回 FINALIZATION")
    void shouldBindToFinalizationStage() {
        assertThat(cap.stage()).isEqualTo(Stage.FINALIZATION);
    }

    @Test
    @DisplayName("subState 返回 RESETTING_STATE")
    void shouldBindToResettingStateSubState() {
        assertThat(cap.subState()).isEqualTo(SubState.RESETTING_STATE);
    }

    @Test
    @DisplayName("priority 返回 0")
    void shouldHaveZeroPriority() {
        assertThat(cap.priority()).isZero();
    }

    @Test
    @DisplayName("execute 清除 extras 和 lockReasons 并返回 Pass")
    void executeShouldClearStateAndReturnPass() {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
        ctx.getExtras().put("key1", "value1");
        ctx.getLockReasons().add(LockReason.PSET_SENDING);

        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras()).isEmpty();
        assertThat(ctx.getLockReasons()).isEmpty();
    }
}
