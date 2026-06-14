package com.tightening.device.type;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TCPDeviceTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        TCPDevice original = new TCPDevice();
        original.setIp("192.168.1.100");
        original.setPort(5000);

        String json = mapper.writeValueAsString(original);
        TCPDevice restored = mapper.readValue(json, TCPDevice.class);

        assertThat(restored.getIp()).isEqualTo("192.168.1.100");
        assertThat(restored.getPort()).isEqualTo(5000);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new TCPDevice());
        TCPDevice restored = mapper.readValue(json, TCPDevice.class);
        assertThat(restored).isNotNull();
    }
}
