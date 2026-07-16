package com.tightening.lifecycle.capability;

import com.tightening.constant.DeviceType;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.judgment.JudgmentResult;
import com.tightening.judgment.JudgmentStrategy;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteJudgment Capability")
class ExecuteJudgmentTest {

    @Mock private JudgmentStrategy judgmentStrategy;

    @Test
    @DisplayName("调用 JudgmentStrategy 判定 → 写 judgeResult")
    void shouldExecuteJudgmentAndSetResult() {
        when(judgmentStrategy.judge(any(TighteningDataDTO.class)))
            .thenReturn(JudgmentResult.ok());

        var cap = new ExecuteJudgment(Map.of(DeviceType.ATLAS_PF4000, judgmentStrategy));
        var data = new TighteningData().setTighteningId(100L);
        var ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .currentOperationData(data).build();
        ctx.setCurrentDeviceType(DeviceType.ATLAS_PF4000);

        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getJudgeResult().isOk()).isTrue();
    }

    @Test
    @DisplayName("无 currentOperationData → precondition 返回 false")
    void shouldSkipWhenNoData() {
        var cap = new ExecuteJudgment(Map.of());
        var ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .build();

        assertThat(cap.precondition(ctx)).isFalse();
    }
}
