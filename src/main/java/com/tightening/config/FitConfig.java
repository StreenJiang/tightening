package com.tightening.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "tool-control.fit")
@Component
@Data
public class FitConfig {
    private long heartBeatIntervalMs;
    private int heartBeatRetryMax;
    private long reassemblyTimeoutMs;
    private long heartBeatTimeoutMs;
}
