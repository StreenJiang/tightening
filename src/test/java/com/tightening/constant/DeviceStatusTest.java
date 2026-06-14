package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceStatusTest {

    @Test
    void valueOf_shouldReturnAllValues() {
        assertThat(DeviceStatus.valueOf("CONNECTING")).isNotNull();
        assertThat(DeviceStatus.valueOf("CONNECTED")).isNotNull();
        assertThat(DeviceStatus.valueOf("DISCONNECTED")).isNotNull();
        assertThat(DeviceStatus.valueOf("NONE")).isNotNull();
    }

    @Test
    void ordinals_shouldBeUnique() {
        var ordinals = java.util.Arrays.stream(DeviceStatus.values())
                .map(DeviceStatus::ordinal)
                .distinct()
                .count();
        assertThat(ordinals).isEqualTo(DeviceStatus.values().length);
    }
}
