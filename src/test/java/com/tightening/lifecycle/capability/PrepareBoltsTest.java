package com.tightening.lifecycle.capability;

import com.tightening.constant.BoltState;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PrepareBolts Capability")
class PrepareBoltsTest {

    private final PrepareBolts cap = new PrepareBolts();

    @Test
    @DisplayName("有螺栓时初始化 BoltState[] 全部 PENDING")
    void shouldInitializeBoltStates() {
        MissionContext ctx = ctxWithBolts(3);
        CapabilityResult result = cap.execute(ctx);

        assertThat(result).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getBoltStates()).hasSize(3);
        assertThat(ctx.getBoltStates()).allMatch(s -> s == BoltState.PENDING);
        assertThat(ctx.getCurrentBoltIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("零螺栓返回 Fail")
    void shouldFailWhenNoBolts() {
        MissionContext ctx = ctxWithBolts(0);
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    @Test
    @DisplayName("boltConfigs 为 null 返回 Fail")
    void shouldFailWhenBoltConfigsNull() {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(null).deviceRegistry(Map.of())
            .build();
        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Fail);
    }

    private static MissionContext ctxWithBolts(int count) {
        List<ProductBolt> bolts = IntStream.range(0, count)
            .mapToObj(i -> new ProductBolt().setBoltSerialNum(i + 1))
            .toList();
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(bolts).deviceRegistry(Map.of())
            .build();
    }
}
