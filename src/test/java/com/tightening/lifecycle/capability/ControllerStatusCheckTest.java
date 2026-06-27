package com.tightening.lifecycle.capability;

import com.tightening.constant.TighteningStatus;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ControllerStatusCheck Capability")
class ControllerStatusCheckTest {

    private final ControllerStatusCheck cap = new ControllerStatusCheck();

    @Test
    @DisplayName("控制器 OK → Pass，写 tighteningStatus")
    void shouldPassWhenStatusOk() {
        MissionContext ctx = ctxWithData(TighteningStatus.OK.getCode());
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getTighteningStatus()).isEqualTo(TighteningStatus.OK.getCode());
    }

    @Test
    @DisplayName("控制器 NG → 仍然 Pass（不阻断，留给综合判定）")
    void shouldPassEvenWhenStatusNg() {
        MissionContext ctx = ctxWithData(TighteningStatus.NG.getCode());
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getTighteningStatus()).isEqualTo(TighteningStatus.NG.getCode());
    }

    @Test
    @DisplayName("无 currentOperationData → Fail")
    void shouldFailWhenNoData() {
        MissionContext ctx = minimalContext();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    private static MissionContext ctxWithData(int status) {
        var data = new TighteningData().setTighteningStatus(status);
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).currentOperationData(data).build();
    }

    private static MissionContext minimalContext() {
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
    }
}
