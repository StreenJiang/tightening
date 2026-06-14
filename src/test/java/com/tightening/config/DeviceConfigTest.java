package com.tightening.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeviceConfigTest {

    @Test
    void shouldCreateDeviceConfigWithDefaults() {
        DeviceConfig config = new DeviceConfig();
        assertThat(config.getConnectThread()).isNull();
        assertThat(config.getScanThread()).isNull();
    }

    @Test
    void shouldSetAndGetConnectThread() {
        DeviceConfig.ConnectThread ct = new DeviceConfig.ConnectThread();
        ct.setCorePoolSize(2);
        ct.setMaxPoolSize(4);
        ct.setKeepAliveTimeMs(5000);
        ct.setCapacity(100);
        ct.setTerminationAwaitMs(30000);

        assertThat(ct.getCorePoolSize()).isEqualTo(2);
        assertThat(ct.getMaxPoolSize()).isEqualTo(4);
        assertThat(ct.getKeepAliveTimeMs()).isEqualTo(5000);
        assertThat(ct.getCapacity()).isEqualTo(100);
        assertThat(ct.getTerminationAwaitMs()).isEqualTo(30000);
    }

    @Test
    void shouldSetAndGetScanThread() {
        DeviceConfig.ScanThread st = new DeviceConfig.ScanThread();
        st.setInitDelayMs(1000);
        st.setDelayMs(5000);
        st.setTerminationAwaitMs(10000);

        assertThat(st.getInitDelayMs()).isEqualTo(1000);
        assertThat(st.getDelayMs()).isEqualTo(5000);
        assertThat(st.getTerminationAwaitMs()).isEqualTo(10000);
    }

    @Test
    void shouldSetConnectThreadOnDeviceConfig() {
        DeviceConfig config = new DeviceConfig();
        DeviceConfig.ConnectThread ct = new DeviceConfig.ConnectThread();
        ct.setCorePoolSize(3);
        config.setConnectThread(ct);

        assertThat(config.getConnectThread()).isSameAs(ct);
        assertThat(config.getConnectThread().getCorePoolSize()).isEqualTo(3);
    }

    @Test
    void shouldSetScanThreadOnDeviceConfig() {
        DeviceConfig config = new DeviceConfig();
        DeviceConfig.ScanThread st = new DeviceConfig.ScanThread();
        st.setDelayMs(8000);
        config.setScanThread(st);

        assertThat(config.getScanThread()).isSameAs(st);
        assertThat(config.getScanThread().getDelayMs()).isEqualTo(8000);
    }
}
