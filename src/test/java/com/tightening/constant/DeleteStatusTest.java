package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteStatusTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(DeleteStatus.fromCode(0)).contains(DeleteStatus.NORMAL);
        assertThat(DeleteStatus.fromCode(1)).contains(DeleteStatus.DELETED);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(DeleteStatus.fromCode(-1)).isEmpty();
        assertThat(DeleteStatus.fromCode(2)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(DeleteStatus.values())
                .map(DeleteStatus::getCode)
                .distinct()
                .count();
        assertThat(codes).isEqualTo(DeleteStatus.values().length);
    }
}
