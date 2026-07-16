package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TorqueRangeCheck")
class TorqueRangeCheckTest {

    private TorqueRangeCheck cap;
    private ProductBolt bolt;
    private TighteningData data;

    @BeforeEach
    void setUp() {
        cap = new TorqueRangeCheck();
        bolt = new ProductBolt().setTorqueMin(10.0).setTorqueMax(20.0);
        data = new TighteningData();
        data.setTorque(15.0);
    }

    @Test
    @DisplayName("扭矩在范围内时 Pass 且 extras[torqueInRange]=true")
    void shouldPassWhenTorqueInRange() {
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of(bolt)).deviceRegistry(Map.of())
                
                .currentOperationData(data).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("torqueInRange")).isEqualTo(true);
        assertThat(ctx.getExtras().get("torqueMin")).isEqualTo(10.0);
        assertThat(ctx.getExtras().get("torqueMax")).isEqualTo(20.0);
        assertThat(ctx.getExtras().get("torqueActual")).isEqualTo(15.0);
    }

    @Test
    @DisplayName("扭矩低于下限时 Pass 且 extras[torqueInRange]=false")
    void shouldPassWhenTorqueBelowMin() {
        data.setTorque(5.0);
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of(bolt)).deviceRegistry(Map.of())
                
                .currentOperationData(data).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("torqueInRange")).isEqualTo(false);
    }

    @Test
    @DisplayName("扭矩高于上限时 Pass 且 extras[torqueInRange]=false")
    void shouldPassWhenTorqueAboveMax() {
        data.setTorque(25.0);
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of(bolt)).deviceRegistry(Map.of())
                
                .currentOperationData(data).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("torqueInRange")).isEqualTo(false);
    }

    @Test
    @DisplayName("限值为 null 时 precondition 返回 false")
    void shouldSkipWhenNoLimits() {
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of(new ProductBolt())).deviceRegistry(Map.of())
                
                .currentOperationData(data).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("无当前螺栓时 precondition 返回 false")
    void shouldSkipWhenNoCurrentBolt() {
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of()).deviceRegistry(Map.of())
                .build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("currentOperationData 为 null 时 precondition 返回 false")
    void shouldSkipWhenNoOperationData() {
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of(bolt)).deviceRegistry(Map.of())
                
                .currentOperationData(null).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("返回正确的 identity")
    void shouldReturnCorrectIdentity() {
        assertThat(cap.id()).isEqualTo("TorqueRangeCheck");
        assertThat(cap.stage()).isEqualTo(Stage.OPERATION);
        assertThat(cap.subState()).isEqualTo(SubState.JUDGING);
        assertThat(cap.priority()).isEqualTo(1);
    }
}
