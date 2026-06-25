package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductBoltDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        ProductBoltDTO dto = new ProductBoltDTO();
        dto.setId(1L);
        dto.setBoltSerialNum(3);
        dto.setTorqueMin(10.5);
        dto.setTorqueMax(45.0);

        String json = mapper.writeValueAsString(dto);
        ProductBoltDTO restored = mapper.readValue(json, ProductBoltDTO.class);

        assertThat(restored.getId()).isEqualTo(1L);
        assertThat(restored.getBoltSerialNum()).isEqualTo(3);
        assertThat(restored.getTorqueMin()).isEqualTo(10.5);
        assertThat(restored.getTorqueMax()).isEqualTo(45.0);
    }

    @Test
    void emptyDto_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductBoltDTO());
        assertThat(mapper.readValue(json, ProductBoltDTO.class)).isNotNull();
    }
}
