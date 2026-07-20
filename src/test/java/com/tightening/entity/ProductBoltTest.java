package com.tightening.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductBoltTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip_fullFields_shouldPreserveAllValues() throws Exception {
        ProductBolt original = new ProductBolt();
        original.setId(1L);
        original.setProductSideId(100L);
        original.setSerialNum(1);
        original.setName("Main Bolt A");
        original.setParameterSetId(500L);
        original.setTorqueMin(10.5);
        original.setTorqueMax(50.0);
        original.setAngleMin(90.0);
        original.setAngleMax(180.0);
        original.setArmLocation("Arm-01");
        original.setLocationXPercent(25.5);
        original.setLocationYPercent(75.3);
        original.setEnabled(1);

        String json = mapper.writeValueAsString(original);
        ProductBolt restored = mapper.readValue(json, ProductBolt.class);

        assertThat(restored.getProductSideId()).isEqualTo(100L);
        assertThat(restored.getSerialNum()).isEqualTo(1);
        assertThat(restored.getName()).isEqualTo("Main Bolt A");
        assertThat(restored.getParameterSetId()).isEqualTo(500L);
        assertThat(restored.getTorqueMin()).isEqualTo(10.5);
        assertThat(restored.getTorqueMax()).isEqualTo(50.0);
        assertThat(restored.getAngleMin()).isEqualTo(90.0);
        assertThat(restored.getAngleMax()).isEqualTo(180.0);
        assertThat(restored.getArmLocation()).isEqualTo("Arm-01");
        assertThat(restored.getLocationXPercent()).isEqualTo(25.5);
        assertThat(restored.getLocationYPercent()).isEqualTo(75.3);
        assertThat(restored.getEnabled()).isEqualTo(1);
    }

    @Test
    void jsonRoundTrip_emptyObject_shouldSurviveSerialization() throws Exception {
        String json = mapper.writeValueAsString(new ProductBolt());
        ProductBolt restored = mapper.readValue(json, ProductBolt.class);
        assertThat(restored).isNotNull();
    }
}
