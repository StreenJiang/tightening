package com.tightening.device.type;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArrangerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        Arranger original = new Arranger();
        original.setSwitchBarCode("BAR-001");
        original.setSwitchPosition("POS-LEFT");
        original.setChannelCount(8);
        original.setReverseFirstFour(true);

        String json = mapper.writeValueAsString(original);
        Arranger restored = mapper.readValue(json, Arranger.class);

        assertThat(restored.getSwitchBarCode()).isEqualTo("BAR-001");
        assertThat(restored.getSwitchPosition()).isEqualTo("POS-LEFT");
        assertThat(restored.getChannelCount()).isEqualTo(8);
        assertThat(restored.getReverseFirstFour()).isEqualTo(true);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new Arranger());
        Arranger restored = mapper.readValue(json, Arranger.class);
        assertThat(restored).isNotNull();
    }

    @Test
    void jsonProperty_shouldUseSnakeCaseKeys() throws Exception {
        Arranger original = new Arranger();
        original.setSwitchBarCode("BAR-002");
        String json = mapper.writeValueAsString(original);
        assertThat(json).contains("switch_bar_code");
    }

    @Test
    void defaultChannelCount_shouldBe8() {
        Arranger arranger = new Arranger();
        assertThat(arranger.getChannelCount()).isEqualTo(8);
    }
}
