package com.tightening.lifecycle;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MissionContext 三层字段 + Builder")
class MissionContextTest {

    @Test
    @DisplayName("Builder 默认值正确")
    void shouldUseDefaultsForOptionalFields() {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L)
            .missionData(missionWithId(1L))
            .boltConfigs(List.of())
            .deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .build();

        assertThat(ctx.getCurrentStage()).isEqualTo(Stage.VALIDATION);
        assertThat(ctx.getCurrentSubState()).isEqualTo(SubState.IDLE);
        assertThat(ctx.getTighteningDataList()).isEmpty();
        assertThat(ctx.getExtras()).isEmpty();
        assertThat(ctx.getLockReasons()).isEmpty();
    }

    @Test
    @DisplayName("currentBolt() 返回当前索引的螺栓")
    void currentBoltShouldReturnCorrectBolt() {
        ProductBolt b1 = new ProductBolt().setBoltSerialNum(1).setBoltName("Bolt1");
        ProductBolt b2 = new ProductBolt().setBoltSerialNum(2).setBoltName("Bolt2");
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(missionWithId(1L))
            .boltConfigs(List.of(b1, b2)).deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .currentBoltIndex(1).build();

        assertThat(ctx.currentBolt().getBoltName()).isEqualTo("Bolt2");
    }

    @Test
    @DisplayName("currentBolt() 索引越界返回 null")
    void currentBoltShouldReturnNullWhenOutOfRange() {
        MissionContext ctx = minimalContext();
        ctx.setCurrentBoltIndex(99);
        assertThat(ctx.currentBolt()).isNull();
    }

    @Test
    @DisplayName("hasMoreBolts() 正确判断")
    void hasMoreBoltsShouldWork() {
        ProductBolt b1 = new ProductBolt().setBoltSerialNum(1);
        ProductBolt b2 = new ProductBolt().setBoltSerialNum(2);
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(missionWithId(1L))
            .boltConfigs(List.of(b1, b2)).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();

        ctx.setCurrentBoltIndex(0);
        assertThat(ctx.hasMoreBolts()).isTrue();
        ctx.setCurrentBoltIndex(1);
        assertThat(ctx.hasMoreBolts()).isFalse();
    }

    @Test
    @DisplayName("allBoltsCompleted() 全部 JDGED_OK/JUDGED_NG 返回 true")
    void allBoltsCompletedShouldReturnTrueWhenAllDone() {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(missionWithId(1L))
            .boltConfigs(List.of(new ProductBolt(), new ProductBolt()))
            .deviceRegistry(Map.of()).shouldSelfLoop(false)
            .boltStates(new BoltState[]{BoltState.JUDGED_OK, BoltState.JUDGED_NG})
            .build();

        assertThat(ctx.allBoltsCompleted()).isTrue();
    }

    @Test
    @DisplayName("allBoltsCompleted() 有 PENDING 返回 false")
    void allBoltsCompletedShouldReturnFalseWhenPendingExists() {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(missionWithId(1L))
            .boltConfigs(List.of(new ProductBolt(), new ProductBolt()))
            .deviceRegistry(Map.of()).shouldSelfLoop(false)
            .boltStates(new BoltState[]{BoltState.JUDGED_OK, BoltState.PENDING})
            .build();

        assertThat(ctx.allBoltsCompleted()).isFalse();
    }

    @Test
    @DisplayName("可变字段 setter 正常工作")
    void mutableFieldsShouldBeSettable() {
        MissionContext ctx = minimalContext();
        ctx.setCurrentStage(Stage.OPERATION);
        ctx.setCurrentSubState(SubState.JUDGING);
        ctx.setCurrentBoltIndex(2);
        ctx.setInterruptRequested(true);

        assertThat(ctx.getCurrentStage()).isEqualTo(Stage.OPERATION);
        assertThat(ctx.getCurrentSubState()).isEqualTo(SubState.JUDGING);
        assertThat(ctx.getCurrentBoltIndex()).isEqualTo(2);
        assertThat(ctx.isInterruptRequested()).isTrue();
    }

    private static ProductMission missionWithId(long id) {
        ProductMission pm = new ProductMission();
        pm.setId(id);
        return pm;
    }

    private static MissionContext minimalContext() {
        return MissionContext.builder()
            .productMissionId(1L)
            .missionData(missionWithId(1L))
            .boltConfigs(List.of())
            .deviceRegistry(Map.of())
            .shouldSelfLoop(false)
            .build();
    }
}
