package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSideDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        ProductSideDTO dto = new ProductSideDTO();
        dto.setId(1L);
        dto.setName("LeftSide");
        dto.setProductMissionId(10L);

        String json = mapper.writeValueAsString(dto);
        ProductSideDTO restored = mapper.readValue(json, ProductSideDTO.class);

        assertThat(restored.getId()).isEqualTo(1L);
        assertThat(restored.getName()).isEqualTo("LeftSide");
        assertThat(restored.getProductMissionId()).isEqualTo(10L);
    }

    @Test
    void jsonRoundTrip_shouldNotContainBlobFields() throws Exception {
        ProductSideDTO dto = new ProductSideDTO();
        dto.setId(1L);
        dto.setName("RightSide");
        dto.setProductMissionId(20L);

        String json = mapper.writeValueAsString(dto);

        assertThat(json).doesNotContain("image");
        assertThat(json).doesNotContain("blob");
        assertThat(json).doesNotContain("photo");
        assertThat(json).doesNotContain("picture");
    }

    @Test
    void emptyDto_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductSideDTO());
        assertThat(mapper.readValue(json, ProductSideDTO.class)).isNotNull();
    }
}
