package com.tightening.lifecycle;

import com.tightening.constant.LockReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissionContextLockReasonsTest {

    @Test
    @DisplayName("lockReasons 初始应为空集合")
    void shouldStartWithEmptyLockReasons() {
        var ctx = MissionContext.builder()
            .productMissionId(1L)
            .shouldSelfLoop(false)
            .boltConfigs(java.util.List.of())
            .deviceRegistry(java.util.Map.of())
            .build();

        assertThat(ctx.getLockReasons()).isEmpty();
    }

    @Test
    @DisplayName("boltUnlockOverride 初始应为 false")
    void shouldStartWithBoltUnlockOverrideFalse() {
        var ctx = MissionContext.builder()
            .productMissionId(1L)
            .shouldSelfLoop(false)
            .boltConfigs(java.util.List.of())
            .deviceRegistry(java.util.Map.of())
            .build();

        assertThat(ctx.isBoltUnlockOverride()).isFalse();
    }

    @Test
    @DisplayName("lockReasons 应可增删")
    void shouldAddAndRemoveLockReasons() {
        var ctx = MissionContext.builder()
            .productMissionId(1L)
            .shouldSelfLoop(false)
            .boltConfigs(java.util.List.of())
            .deviceRegistry(java.util.Map.of())
            .build();

        ctx.getLockReasons().add(LockReason.PSET_SENDING);
        assertThat(ctx.getLockReasons()).hasSize(1);
        assertThat(ctx.getLockReasons()).contains(LockReason.PSET_SENDING);

        ctx.getLockReasons().remove(LockReason.PSET_SENDING);
        assertThat(ctx.getLockReasons()).isEmpty();
    }

    @Test
    @DisplayName("boltUnlockOverride 应可设置")
    void shouldSetBoltUnlockOverride() {
        var ctx = MissionContext.builder()
            .productMissionId(1L)
            .shouldSelfLoop(false)
            .boltConfigs(java.util.List.of())
            .deviceRegistry(java.util.Map.of())
            .build();

        ctx.setBoltUnlockOverride(true);
        assertThat(ctx.isBoltUnlockOverride()).isTrue();

        ctx.setBoltUnlockOverride(false);
        assertThat(ctx.isBoltUnlockOverride()).isFalse();
    }
}
