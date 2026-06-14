package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_shouldPreserveFields() throws Exception {
        BaseEntity original = new BaseEntity();
        original.setId(42L);

        String json = mapper.writeValueAsString(original);
        BaseEntity restored = mapper.readValue(json, BaseEntity.class);

        assertThat(restored.getId()).isEqualTo(42L);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new BaseEntity());
        BaseEntity restored = mapper.readValue(json, BaseEntity.class);
        assertThat(restored).isNotNull();
    }
}
