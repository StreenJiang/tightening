package com.tightening.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tool-control.gateway")
public class GatewayConfig {
    private long pollIntervalMs = 100;
    private long armReadTimeoutMs = 3000;
    private long reconnectIntervalMs = 5000;
    private int defaultPort = 4545;
    private int connectTimeoutMs = 5000;
    private long subDeviceOpTimeoutMs = 3000;
}
