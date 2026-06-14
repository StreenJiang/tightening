package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TighteningDataTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        TighteningData original = new TighteningData();
        original.setId(1L);
        original.setTighteningId(1234567890L);
        original.setVin("VIN1234567890");
        original.setTorqueStatus(1);
        original.setAngleStatus(1);
        original.setTighteningStatus(1);
        original.setTorque(12.34);
        original.setAngle(45.0);
        original.setTimestamp("2024-01-15 10:30:00");
        original.setParameterSetName("PSET-001");
        original.setCellId(1);
        original.setChannelId(2);

        String json = mapper.writeValueAsString(original);
        TighteningData restored = mapper.readValue(json, TighteningData.class);

        assertThat(restored.getTighteningId()).isEqualTo(1234567890L);
        assertThat(restored.getVin()).isEqualTo("VIN1234567890");
        assertThat(restored.getTorque()).isCloseTo(12.34, within(0.001));
        assertThat(restored.getAngle()).isCloseTo(45.0, within(0.001));
        assertThat(restored.getTimestamp()).isEqualTo("2024-01-15 10:30:00");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new TighteningData());
        TighteningData restored = mapper.readValue(json, TighteningData.class);
        assertThat(restored).isNotNull();
        assertThat(restored.getId()).isNull();
    }
}
