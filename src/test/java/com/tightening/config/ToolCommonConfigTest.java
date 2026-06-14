package com.tightening.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolCommonConfigTest {

    @Test
    void shouldCreateToolCommonConfigWithDefault() {
        ToolCommonConfig config = new ToolCommonConfig();
        assertThat(config.getEnableDisableCooldownMs()).isZero();
    }

    @Test
    void shouldSetAndGetEnableDisableCooldownMs() {
        ToolCommonConfig config = new ToolCommonConfig();
        config.setEnableDisableCooldownMs(5000);
        assertThat(config.getEnableDisableCooldownMs()).isEqualTo(5000);
    }
}
