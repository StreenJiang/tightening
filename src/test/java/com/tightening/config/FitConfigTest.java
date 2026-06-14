package com.tightening.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FitConfigTest {

    @Test
    void shouldCreateFitConfigWithDefaults() {
        FitConfig config = new FitConfig();
        assertThat(config.getHeartBeatIntervalMs()).isZero();
        assertThat(config.getHeartBeatRetryMax()).isZero();
    }

    @Test
    void shouldSetAndGetHeartBeatIntervalMs() {
        FitConfig config = new FitConfig();
        config.setHeartBeatIntervalMs(30000);
        assertThat(config.getHeartBeatIntervalMs()).isEqualTo(30000);
    }

    @Test
    void shouldSetAndGetHeartBeatRetryMax() {
        FitConfig config = new FitConfig();
        config.setHeartBeatRetryMax(3);
        assertThat(config.getHeartBeatRetryMax()).isEqualTo(3);
    }

    @Test
    void shouldSetAndGetAllFields() {
        FitConfig config = new FitConfig();
        config.setHeartBeatIntervalMs(15000);
        config.setHeartBeatRetryMax(5);

        assertThat(config)
                .extracting(FitConfig::getHeartBeatIntervalMs, FitConfig::getHeartBeatRetryMax)
                .containsExactly(15000L, 5);
    }
}
