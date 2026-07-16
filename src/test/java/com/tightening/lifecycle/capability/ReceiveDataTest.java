package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReceiveData")
class ReceiveDataTest {

    private final ReceiveData cap = new ReceiveData();

    @Test
    @DisplayName("有效数据到达时返回 Pass")
    void shouldPassWhenDataReceived() {
        TighteningData data = new TighteningData();
        data.setTighteningId(12345L);
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of()).deviceRegistry(Map.of())
                
                .currentOperationData(data).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
    }

    @Test
    @DisplayName("无数据时返回 Fail")
    void shouldFailWhenNoData() {
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of()).deviceRegistry(Map.of())
                
                .currentOperationData(null).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("tighteningId 为 0 时返回 Fail")
    void shouldFailWhenTighteningIdZero() {
        TighteningData data = new TighteningData();
        data.setTighteningId(0L);
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of()).deviceRegistry(Map.of())
                
                .currentOperationData(data).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("返回正确的 identity")
    void shouldReturnCorrectIdentity() {
        assertThat(cap.id()).isEqualTo("ReceiveData");
        assertThat(cap.stage()).isEqualTo(Stage.OPERATION);
        assertThat(cap.subState()).isEqualTo(SubState.TIGHTENING_RECEIVED);
        assertThat(cap.priority()).isEqualTo(0);
    }
}
