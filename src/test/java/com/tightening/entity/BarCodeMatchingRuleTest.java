package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BarCodeMatchingRuleTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        BarCodeMatchingRule original = new BarCodeMatchingRule();
        original.setId(1L);
        original.setName("Barcode Rule A");
        original.setProductMissionId(100L);
        original.setRuleType(2);
        original.setPartNumber("PN-12345");
        original.setExpectedLength(20);
        original.setKeyStartPosition(3);
        original.setKeyEndPosition(10);
        original.setKeyChar("ABC");

        String json = mapper.writeValueAsString(original);
        BarCodeMatchingRule restored = mapper.readValue(json, BarCodeMatchingRule.class);

        assertThat(restored.getName()).isEqualTo("Barcode Rule A");
        assertThat(restored.getProductMissionId()).isEqualTo(100L);
        assertThat(restored.getRuleType()).isEqualTo(2);
        assertThat(restored.getPartNumber()).isEqualTo("PN-12345");
        assertThat(restored.getExpectedLength()).isEqualTo(20);
        assertThat(restored.getKeyStartPosition()).isEqualTo(3);
        assertThat(restored.getKeyEndPosition()).isEqualTo(10);
        assertThat(restored.getKeyChar()).isEqualTo("ABC");
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new BarCodeMatchingRule());
        BarCodeMatchingRule restored = mapper.readValue(json, BarCodeMatchingRule.class);
        assertThat(restored).isNotNull();
    }
}
