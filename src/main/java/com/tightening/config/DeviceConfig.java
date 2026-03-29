package com.tightening.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "device-config")
@Component
@Data
public class DeviceConfig {
    private ConnectThread connectThread;
    private ScanThread scanThread;

    @Data
    public static class ConnectThread {
        private int corePoolSize;
        private int maxPoolSize;
        private long keepAliveTimeMs;
        private int capacity;
    }

    @Data
    public static class ScanThread {
        private long initDelayMs;
        private long delayMs;
    }
}
