package com.tightening.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolCommonConfigTest {

    @Test
    void shouldCreateToolCommonConfigWithDefault() {
        ToolCommonConfig config = new ToolCommonConfig();
        assertThat(config.getLockUnlockCooldownMs()).isZero();
    }

    @Test
    void shouldSetAndGetLockUnlockCooldownMs() {
        ToolCommonConfig config = new ToolCommonConfig();
        config.setLockUnlockCooldownMs(5000);
        assertThat(config.getLockUnlockCooldownMs()).isEqualTo(5000);
    }
}
