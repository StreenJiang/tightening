package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FitAngleStatusTest {

    @Test
    void valueOf_shouldReturnAllValues() {
        assertThat(FitAngleStatus.valueOf("OK")).isNotNull();
        assertThat(FitAngleStatus.valueOf("NG")).isNotNull();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(FitAngleStatus.values())
                .map(FitAngleStatus::getCode)
                .distinct()
                .count();
        assertThat(codes).isEqualTo(FitAngleStatus.values().length);
    }
}
