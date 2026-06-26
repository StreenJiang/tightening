package com.tightening.config;

import com.tightening.constant.DeviceType;
import com.tightening.judgment.AtlasJudgment;
import com.tightening.judgment.FitJudgment;
import com.tightening.judgment.JudgmentStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class JudgmentConfig {

    @Bean
    public Map<DeviceType, JudgmentStrategy> judgmentStrategies() {
        return createStrategies();
    }

    static Map<DeviceType, JudgmentStrategy> createStrategies() {
        return Map.of(
                DeviceType.ATLAS_PF4000, new AtlasJudgment(),
                DeviceType.ATLAS_PF6000_OP, new AtlasJudgment(),
                DeviceType.FIT_FTC6, new FitJudgment()
        );
    }
}
