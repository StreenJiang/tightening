package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtlasAngleStatusTest {

    @Test
    void valueOf_shouldReturnAllValues() {
        assertThat(AtlasAngleStatus.valueOf("LOW")).isNotNull();
        assertThat(AtlasAngleStatus.valueOf("OK")).isNotNull();
        assertThat(AtlasAngleStatus.valueOf("HIGH")).isNotNull();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(AtlasAngleStatus.values())
                .map(AtlasAngleStatus::getCode)
                .distinct()
                .count();
        assertThat(codes).isEqualTo(AtlasAngleStatus.values().length);
    }
}
