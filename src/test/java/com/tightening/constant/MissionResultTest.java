package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissionResultTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(MissionResult.fromCode(0)).contains(MissionResult.NG);
        assertThat(MissionResult.fromCode(1)).contains(MissionResult.OK);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(MissionResult.fromCode(-1)).isEmpty();
        assertThat(MissionResult.fromCode(2)).isEmpty();
        assertThat(MissionResult.fromCode(Integer.MAX_VALUE)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(MissionResult.values())
                .map(MissionResult::getCode)
                .distinct()
                .count();
        assertThat(codes).isEqualTo(MissionResult.values().length);
    }
}
