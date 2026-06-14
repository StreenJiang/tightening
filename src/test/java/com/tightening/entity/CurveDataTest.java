package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurveDataTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        CurveData original = new CurveData();
        original.setId(1L);
        original.setTighteningId(100);
        original.setDataSamples("[{\"torque\":1.0,\"angle\":10.0}]");
        original.setTimestamp("2024-01-15 10:30:00");

        String json = mapper.writeValueAsString(original);
        CurveData restored = mapper.readValue(json, CurveData.class);

        assertThat(restored.getTighteningId()).isEqualTo(100);
        assertThat(restored.getDataSamples()).isEqualTo("[{\"torque\":1.0,\"angle\":10.0}]");
        assertThat(restored.getTimestamp()).isEqualTo("2024-01-15 10:30:00");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new CurveData());
        CurveData restored = mapper.readValue(json, CurveData.class);
        assertThat(restored).isNotNull();
    }
}
