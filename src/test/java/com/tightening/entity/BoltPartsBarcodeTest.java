package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoltPartsBarcodeTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        BoltPartsBarcode original = new BoltPartsBarcode();
        original.setId(1L);
        original.setProductBoltId(100L);
        original.setBarCodeMatchingRuleId(200L);

        String json = mapper.writeValueAsString(original);
        BoltPartsBarcode restored = mapper.readValue(json, BoltPartsBarcode.class);

        assertThat(restored.getProductBoltId()).isEqualTo(100L);
        assertThat(restored.getBarCodeMatchingRuleId()).isEqualTo(200L);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new BoltPartsBarcode());
        BoltPartsBarcode restored = mapper.readValue(json, BoltPartsBarcode.class);
        assertThat(restored).isNotNull();
    }
}
