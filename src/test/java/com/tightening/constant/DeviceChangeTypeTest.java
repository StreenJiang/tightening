package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceChangeTypeTest {

    @Test
    void valueOf_shouldReturnAllValues() {
        assertThat(DeviceChangeType.valueOf("ADD")).isNotNull();
        assertThat(DeviceChangeType.valueOf("UPDATE")).isNotNull();
        assertThat(DeviceChangeType.valueOf("DELETE")).isNotNull();
    }

    @Test
    void ordinals_shouldBeUnique() {
        var ordinals = java.util.Arrays.stream(DeviceChangeType.values())
                .map(DeviceChangeType::ordinal)
                .distinct()
                .count();
        assertThat(ordinals).isEqualTo(DeviceChangeType.values().length);
    }
}
