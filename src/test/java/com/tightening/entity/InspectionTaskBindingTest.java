package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionTaskBindingTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        InspectionTaskBinding original = new InspectionTaskBinding();
        original.setId(1L);
        original.setInspectionTaskId(100L);
        original.setBoundTaskId(200L);

        String json = mapper.writeValueAsString(original);
        InspectionTaskBinding restored = mapper.readValue(json, InspectionTaskBinding.class);

        assertThat(restored.getInspectionTaskId()).isEqualTo(100L);
        assertThat(restored.getBoundTaskId()).isEqualTo(200L);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new InspectionTaskBinding());
        InspectionTaskBinding restored = mapper.readValue(json, InspectionTaskBinding.class);
        assertThat(restored).isNotNull();
    }
}
