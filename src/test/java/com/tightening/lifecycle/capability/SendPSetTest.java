package com.tightening.lifecycle.capability;

import com.tightening.constant.LockReason;
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
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("execute 应在发送前添加 PSET_SENDING 到 lockReasons，完成后移除")
    void shouldAddAndRemovePsetSwitchingLockReason() {
        ProductBolt bolt = new ProductBolt().setBoltSerialNum(1).setParameterSetId(2L);
        MissionContext ctx = MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of(bolt)).deviceRegistry(Map.of(1L, tool))
            .shouldSelfLoop(false).build();
        // 通过 Answer 验证 sendPSet 被调用时 lockReasons 中已包含 PSET_SENDING
        when(tool.sendPSet(2)).thenAnswer(invocation -> {
            assertThat(ctx.getLockReasons()).contains(LockReason.PSET_SENDING);
            return CompletableFuture.completedFuture(true);
        });

        cap.execute(ctx);

        // execute 返回后 lockReasons 中不应再有 PSET_SENDING（已移除）
        assertThat(ctx.getLockReasons()).doesNotContain(LockReason.PSET_SENDING);
        verify(tool, times(1)).sendPSet(2);
    }
}
