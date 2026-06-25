package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionMissionBindingTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        InspectionMissionBinding original = new InspectionMissionBinding();
        original.setId(1L);
        original.setInspectionMissionId(100L);
        original.setBoundMissionId(200L);

        String json = mapper.writeValueAsString(original);
        InspectionMissionBinding restored = mapper.readValue(json, InspectionMissionBinding.class);

        assertThat(restored.getInspectionMissionId()).isEqualTo(100L);
        assertThat(restored.getBoundMissionId()).isEqualTo(200L);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new InspectionMissionBinding());
        InspectionMissionBinding restored = mapper.readValue(json, InspectionMissionBinding.class);
        assertThat(restored).isNotNull();
    }
}
