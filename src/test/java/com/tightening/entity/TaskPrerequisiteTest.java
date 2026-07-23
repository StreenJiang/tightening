package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskPrerequisiteTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        TaskPrerequisite original = new TaskPrerequisite();
        original.setId(1L);
        original.setTaskId(100L);
        original.setPrerequisiteTaskId(200L);
        original.setPrerequisiteType(1);

        String json = mapper.writeValueAsString(original);
        TaskPrerequisite restored = mapper.readValue(json, TaskPrerequisite.class);

        assertThat(restored.getTaskId()).isEqualTo(100L);
        assertThat(restored.getPrerequisiteTaskId()).isEqualTo(200L);
        assertThat(restored.getPrerequisiteType()).isEqualTo(1);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new TaskPrerequisite());
        TaskPrerequisite restored = mapper.readValue(json, TaskPrerequisite.class);
        assertThat(restored).isNotNull();
    }
}
