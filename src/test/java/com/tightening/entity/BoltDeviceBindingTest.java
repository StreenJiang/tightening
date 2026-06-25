package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoltDeviceBindingTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        BoltDeviceBinding original = new BoltDeviceBinding();
        original.setId(1L);
        original.setProductBoltId(100L);
        original.setDeviceId(200L);
        original.setDeviceRole(1);
        original.setDeviceSpec(12.5);
        original.setSortOrder(2);

        String json = mapper.writeValueAsString(original);
        BoltDeviceBinding restored = mapper.readValue(json, BoltDeviceBinding.class);

        assertThat(restored.getProductBoltId()).isEqualTo(100L);
        assertThat(restored.getDeviceId()).isEqualTo(200L);
        assertThat(restored.getDeviceRole()).isEqualTo(1);
        assertThat(restored.getDeviceSpec()).isEqualTo(12.5);
        assertThat(restored.getSortOrder()).isEqualTo(2);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new BoltDeviceBinding());
        BoltDeviceBinding restored = mapper.readValue(json, BoltDeviceBinding.class);
        assertThat(restored).isNotNull();
    }
}
