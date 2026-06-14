package com.tightening.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseDTOTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        BaseDTO original = new BaseDTO();
        original.setId(42L);
        original.setDeleted(0);
        original.setCreatorId(100L);
        original.setModifierId(200L);

        String json = mapper.writeValueAsString(original);
        BaseDTO restored = mapper.readValue(json, BaseDTO.class);

        assertThat(restored.getId()).isEqualTo(42L);
        assertThat(restored.getDeleted()).isZero();
        assertThat(restored.getCreatorId()).isEqualTo(100L);
        assertThat(restored.getModifierId()).isEqualTo(200L);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new BaseDTO());
        BaseDTO restored = mapper.readValue(json, BaseDTO.class);
        assertThat(restored).isNotNull();
        assertThat(restored.getId()).isNull();
    }
}
