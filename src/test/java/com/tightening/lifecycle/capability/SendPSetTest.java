package com.tightening.lifecycle.capability;

import com.tightening.device.contract.ITool;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendPSet Capability")
class SendPSetTest {

    @Mock private ITool tool;
    private SendPSet cap;

    @BeforeEach
    void setUp() {
        cap = new SendPSet();
    }

    @Test
    @DisplayName("precondition 不满足时返回 false（bolt 为 null）")
    void preconditionShouldReturnFalseWhenBoltNull() {
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("precondition 不满足时返回 false（parameterSetId 为 null）")
    void preconditionShouldReturnFalseWhenNoPSet() {
        ProductBolt bolt = new ProductBolt().setBoltSerialNum(1);
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of(bolt)).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
        assertThat(cap.precondition(ctx)).isFalse();
    }
}
