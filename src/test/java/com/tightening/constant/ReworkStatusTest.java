package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReworkStatusTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(ReworkStatus.fromCode(0)).contains(ReworkStatus.NORMAL);
        assertThat(ReworkStatus.fromCode(1)).contains(ReworkStatus.REWORK);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(ReworkStatus.fromCode(-1)).isEmpty();
        assertThat(ReworkStatus.fromCode(2)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(ReworkStatus.values())
                .map(ReworkStatus::getCode)
                .distinct()
                .count();
        assertThat(codes).isEqualTo(ReworkStatus.values().length);
    }
}
