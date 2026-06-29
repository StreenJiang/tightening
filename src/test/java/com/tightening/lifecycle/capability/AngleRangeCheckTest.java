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

@DisplayName("AngleRangeCheck")
class AngleRangeCheckTest {

    private AngleRangeCheck cap;
    private ProductBolt bolt;
    private TighteningData data;

    @BeforeEach
    void setUp() {
        cap = new AngleRangeCheck();
        bolt = new ProductBolt().setAngleMin(30.0).setAngleMax(60.0);
        data = new TighteningData();
        data.setAngle(45.0);
    }

    @Test
    @DisplayName("角度在范围内时 Pass 且 extras[angleInRange]=true")
    void shouldPassWhenAngleInRange() {
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of(bolt)).deviceRegistry(Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("angleInRange")).isEqualTo(true);
        assertThat(ctx.getExtras().get("angleMin")).isEqualTo(30.0);
        assertThat(ctx.getExtras().get("angleMax")).isEqualTo(60.0);
        assertThat(ctx.getExtras().get("angleActual")).isEqualTo(45.0);
    }

    @Test
    @DisplayName("角度低于下限时 Pass 且 extras[angleInRange]=false")
    void shouldPassWhenAngleBelowMin() {
        data.setAngle(15.0);
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of(bolt)).deviceRegistry(Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("angleInRange")).isEqualTo(false);
    }

    @Test
    @DisplayName("角度高于上限时 Pass 且 extras[angleInRange]=false")
    void shouldPassWhenAngleAboveMax() {
        data.setAngle(75.0);
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of(bolt)).deviceRegistry(Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data).build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getExtras().get("angleInRange")).isEqualTo(false);
    }

    @Test
    @DisplayName("限值为 null 时 precondition 返回 false")
    void shouldSkipWhenNoLimits() {
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of(new ProductBolt())).deviceRegistry(Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(data).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("无当前螺栓时 precondition 返回 false")
    void shouldSkipWhenNoCurrentBolt() {
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of()).deviceRegistry(Map.of())
                .shouldSelfLoop(false).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("currentOperationData 为 null 时 precondition 返回 false")
    void shouldSkipWhenNoOperationData() {
        MissionContext ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .boltConfigs(List.of(bolt)).deviceRegistry(Map.of())
                .shouldSelfLoop(false)
                .currentOperationData(null).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("返回正确的 identity")
    void shouldReturnCorrectIdentity() {
        assertThat(cap.id()).isEqualTo("AngleRangeCheck");
        assertThat(cap.stage()).isEqualTo(Stage.OPERATION);
        assertThat(cap.subState()).isEqualTo(SubState.JUDGING);
        assertThat(cap.priority()).isEqualTo(2);
    }
}
