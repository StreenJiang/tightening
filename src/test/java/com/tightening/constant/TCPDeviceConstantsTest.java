package com.tightening.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TCPDeviceConstantsTest {
    @Test
    void reconnectIntervalMs_shouldBe3000() {
        assertThat(TCPDeviceConstants.RECONNECT_INTERVAL_MS).isEqualTo(3000);
    }

    @Test
    void constructor_exists() {
        assertThat(TCPDeviceConstants.class.getDeclaredConstructors()).hasSize(1);
    }
}
