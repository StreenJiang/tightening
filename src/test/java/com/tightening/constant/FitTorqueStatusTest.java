package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FitTorqueStatusTest {

    @Test
    void valueOf_shouldReturnAllValues() {
        assertThat(FitTorqueStatus.valueOf("OK")).isNotNull();
        assertThat(FitTorqueStatus.valueOf("NG")).isNotNull();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(FitTorqueStatus.values())
                .map(FitTorqueStatus::getCode)
                .distinct()
                .count();
        assertThat(codes).isEqualTo(FitTorqueStatus.values().length);
    }
}
