package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        Device original = new Device();
        original.setId(1L);
        original.setName("TestDevice");
        original.setDescription("A test tightening device");
        original.setType(1);
        original.setDetail("192.168.1.100:5000");

        String json = mapper.writeValueAsString(original);
        Device restored = mapper.readValue(json, Device.class);

        assertThat(restored.getName()).isEqualTo("TestDevice");
        assertThat(restored.getDescription()).isEqualTo("A test tightening device");
        assertThat(restored.getType()).isEqualTo(1);
        assertThat(restored.getDetail()).isEqualTo("192.168.1.100:5000");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new Device());
        Device restored = mapper.readValue(json, Device.class);
        assertThat(restored).isNotNull();
    }
}
