package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtlasTorqueStatusTest {

    @Test
    void valueOf_shouldReturnAllValues() {
        assertThat(AtlasTorqueStatus.valueOf("LOW")).isNotNull();
        assertThat(AtlasTorqueStatus.valueOf("OK")).isNotNull();
        assertThat(AtlasTorqueStatus.valueOf("HIGH")).isNotNull();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(AtlasTorqueStatus.values())
                .map(AtlasTorqueStatus::getCode)
                .distinct()
                .count();
        assertThat(codes).isEqualTo(AtlasTorqueStatus.values().length);
    }
}
