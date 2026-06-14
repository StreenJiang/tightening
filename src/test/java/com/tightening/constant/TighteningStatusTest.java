package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TighteningStatusTest {

    @Test
    void valueOf_shouldReturnAllValues() {
        assertThat(TighteningStatus.valueOf("NG")).isNotNull();
        assertThat(TighteningStatus.valueOf("OK")).isNotNull();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(TighteningStatus.values())
                .map(TighteningStatus::getCode)
                .distinct()
                .count();
        assertThat(codes).isEqualTo(TighteningStatus.values().length);
    }
}
