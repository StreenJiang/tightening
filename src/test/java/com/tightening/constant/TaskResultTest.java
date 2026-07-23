package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskResultTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(TaskResult.fromCode(0)).contains(TaskResult.NG);
        assertThat(TaskResult.fromCode(1)).contains(TaskResult.OK);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(TaskResult.fromCode(-1)).isEmpty();
        assertThat(TaskResult.fromCode(2)).isEmpty();
        assertThat(TaskResult.fromCode(Integer.MAX_VALUE)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(TaskResult.values())
                .map(TaskResult::getCode)
                .distinct()
                .count();
        assertThat(codes).isEqualTo(TaskResult.values().length);
    }
}
