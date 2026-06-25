package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissionPrerequisiteTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        MissionPrerequisite original = new MissionPrerequisite();
        original.setId(1L);
        original.setMissionId(100L);
        original.setPrerequisiteMissionId(200L);
        original.setPrerequisiteType(1);

        String json = mapper.writeValueAsString(original);
        MissionPrerequisite restored = mapper.readValue(json, MissionPrerequisite.class);

        assertThat(restored.getMissionId()).isEqualTo(100L);
        assertThat(restored.getPrerequisiteMissionId()).isEqualTo(200L);
        assertThat(restored.getPrerequisiteType()).isEqualTo(1);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new MissionPrerequisite());
        MissionPrerequisite restored = mapper.readValue(json, MissionPrerequisite.class);
        assertThat(restored).isNotNull();
    }
}
