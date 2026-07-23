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
        original.setProductTaskId(100L);
        original.setRuleType(2);
        original.setPartNumber("PN-12345");
        original.setExpectedLength(20);
        original.setSegments("[{\"s\":3,\"e\":10,\"v\":\"ABC\"}]");
        original.setSeq(1);

        String json = mapper.writeValueAsString(original);
        BarCodeMatchingRule restored = mapper.readValue(json, BarCodeMatchingRule.class);

        assertThat(restored.getName()).isEqualTo("Barcode Rule A");
        assertThat(restored.getProductTaskId()).isEqualTo(100L);
        assertThat(restored.getRuleType()).isEqualTo(2);
        assertThat(restored.getPartNumber()).isEqualTo("PN-12345");
        assertThat(restored.getExpectedLength()).isEqualTo(20);
        assertThat(restored.getSegments()).isEqualTo("[{\"s\":3,\"e\":10,\"v\":\"ABC\"}]");
        assertThat(restored.getSeq()).isEqualTo(1);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new BarCodeMatchingRule());
        BarCodeMatchingRule restored = mapper.readValue(json, BarCodeMatchingRule.class);
        assertThat(restored).isNotNull();
    }
}
