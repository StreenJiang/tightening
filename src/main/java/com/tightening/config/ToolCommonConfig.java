package com.tightening.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "tool-control.common")
@Component
@Data
public class ToolCommonConfig {
    private long enableDisableCooldownMs;
}
