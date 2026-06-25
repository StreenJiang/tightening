package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMissionDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        ProductMissionDTO dto = new ProductMissionDTO();
        dto.setId(1L);
        dto.setName("EngineAssembly");
        dto.setMaxNgCount(5);
        dto.setEnabled(1);

        String json = mapper.writeValueAsString(dto);
        ProductMissionDTO restored = mapper.readValue(json, ProductMissionDTO.class);

        assertThat(restored.getId()).isEqualTo(1L);
        assertThat(restored.getName()).isEqualTo("EngineAssembly");
        assertThat(restored.getMaxNgCount()).isEqualTo(5);
        assertThat(restored.getEnabled()).isEqualTo(1);
    }

    @Test
    void emptyDto_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductMissionDTO());
        assertThat(mapper.readValue(json, ProductMissionDTO.class)).isNotNull();
    }
}
