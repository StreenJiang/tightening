package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IoDeviceTypeTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(IoDeviceType.fromCode(1)).contains(IoDeviceType.ARRANGER);
        assertThat(IoDeviceType.fromCode(2)).contains(IoDeviceType.SETTER_SELECTOR);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(IoDeviceType.fromCode(-1)).isEmpty();
        assertThat(IoDeviceType.fromCode(0)).isEmpty();
        assertThat(IoDeviceType.fromCode(3)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(IoDeviceType.values())
                .map(IoDeviceType::getCode).distinct().count();
        assertThat(codes).isEqualTo(IoDeviceType.values().length);
    }
}
