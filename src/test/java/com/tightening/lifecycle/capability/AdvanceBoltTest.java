package com.tightening.lifecycle.capability;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.MissionRecord;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.judgment.JudgmentResult;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.MissionRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdvanceBolt Capability")
class AdvanceBoltTest {

    @Mock private MissionRecordService missionRecordService;
    private AdvanceBolt cap;

    @BeforeEach
    void setUp() {
        cap = new AdvanceBolt(missionRecordService);
    }

    @Test
    @DisplayName("OK 判定后标记为 JUDGED_OK 并推进索引")
    void shouldMarkBoltJudgedOk() {
        MissionContext ctx = ctxWithBolts(2);
        ctx.setCurrentBoltIndex(0);
        ctx.setJudgeResult(new JudgmentResult(true, "OK"));

        cap.execute(ctx);

        assertThat(ctx.getBoltStates()[0]).isEqualTo(BoltState.JUDGED_OK);
        assertThat(ctx.getCurrentBoltIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("NG 判定后标记为 JUDGED_NG 并推进")
    void shouldMarkBoltJudgedNg() {
        MissionContext ctx = ctxWithBolts(2);
        ctx.setCurrentBoltIndex(0);
        ctx.setJudgeResult(new JudgmentResult(false, "NG"));

        cap.execute(ctx);

        assertThat(ctx.getBoltStates()[0]).isEqualTo(BoltState.JUDGED_NG);
        assertThat(ctx.getCurrentBoltIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("全部螺栓完成 → 进入 FINALIZATION")
    void shouldEnterFinalizationWhenAllDone() {
        MissionContext ctx = ctxWithBolts(1);
        ctx.setCurrentBoltIndex(0);

        cap.execute(ctx);

        assertThat(ctx.getCurrentStage()).isEqualTo(Stage.FINALIZATION);
        assertThat(ctx.getCurrentSubState()).isEqualTo(SubState.CLEANING_TASKS);
    }

    @Test
    @DisplayName("最后一个螺栓 OK 时调用 markAsOk")
    void shouldMarkMissionOkWhenAllJudgedOk() {
        MissionContext ctx = ctxWithBolts(1);
        ctx.setCurrentBoltIndex(0);
        ctx.setBoltStates(new BoltState[]{BoltState.JUDGED_OK});
        ctx.setJudgeResult(new JudgmentResult(true, "OK"));
        MissionRecord record = new MissionRecord();
        record.setId(42L);
        ctx.setMissionRecord(record);

        cap.execute(ctx);

        verify(missionRecordService).markAsOk(42L);
    }

    private static MissionContext ctxWithBolts(int count) {
        List<ProductBolt> bolts = IntStream.range(0, count)
            .mapToObj(i -> new ProductBolt().setSerialNum(i + 1))
            .toList();
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(bolts).deviceRegistry(Map.of())
            
            .boltStates(new BoltState[count])
            .build();
    }
}
