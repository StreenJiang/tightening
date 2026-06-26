package com.tightening.config;

import com.tightening.constant.DeviceType;
import com.tightening.judgment.AtlasJudgment;
import com.tightening.judgment.FitJudgment;
import com.tightening.judgment.JudgmentStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JudgmentConfigTest.TestConfig.class)
@DisplayName("JudgmentConfig 策略注册")
class JudgmentConfigTest {

    @Configuration
    static class TestConfig {
        @Bean
        public Map<DeviceType, JudgmentStrategy> judgmentStrategies() {
            return JudgmentConfig.createStrategies();
        }
    }

    @Autowired
    private Map<DeviceType, JudgmentStrategy> strategies;

    @Test
    @DisplayName("ATLAS_PF4000 对应 AtlasJudgment")
    void atlasPf4000() {
        assertThat(strategies.get(DeviceType.ATLAS_PF4000))
                .isInstanceOf(AtlasJudgment.class);
    }

    @Test
    @DisplayName("ATLAS_PF6000_OP 对应 AtlasJudgment")
    void atlasPf6000() {
        assertThat(strategies.get(DeviceType.ATLAS_PF6000_OP))
                .isInstanceOf(AtlasJudgment.class);
    }

    @Test
    @DisplayName("FIT_FTC6 对应 FitJudgment")
    void fitFtc6() {
        assertThat(strategies.get(DeviceType.FIT_FTC6))
                .isInstanceOf(FitJudgment.class);
    }
}
