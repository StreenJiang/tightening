package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        DeviceDTO original = new DeviceDTO();
        original.setId(1L);
        original.setDeleted(0);
        original.setCreatorId(100L);
        original.setModifierId(200L);

        original.setName("Device-01");
        original.setDescription("Test device description");
        original.setType(1);
        original.setDetail("{\"ip\":\"192.168.1.100\",\"port\":7000}");

        String json = mapper.writeValueAsString(original);
        DeviceDTO restored = mapper.readValue(json, DeviceDTO.class);

        assertThat(restored.getName()).isEqualTo("Device-01");
        assertThat(restored.getDescription()).isEqualTo("Test device description");
        assertThat(restored.getType()).isEqualTo(1);
        assertThat(restored.getDetail()).isEqualTo("{\"ip\":\"192.168.1.100\",\"port\":7000}");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new DeviceDTO());
        DeviceDTO restored = mapper.readValue(json, DeviceDTO.class);
        assertThat(restored).isNotNull();
        assertThat(restored.getId()).isNull();
    }
}
