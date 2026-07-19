package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrerequisiteTypeTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(PrerequisiteType.fromCode(1)).contains(PrerequisiteType.SAME_TRACE);
        assertThat(PrerequisiteType.fromCode(2)).contains(PrerequisiteType.MATERIAL_TRACE);
        assertThat(PrerequisiteType.fromCode(3)).contains(PrerequisiteType.INSPECTION_CHAIN);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(PrerequisiteType.fromCode(-1)).isEmpty();
        assertThat(PrerequisiteType.fromCode(0)).isEmpty();
        assertThat(PrerequisiteType.fromCode(4)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(PrerequisiteType.values())
                .map(PrerequisiteType::getCode).distinct().count();
        assertThat(codes).isEqualTo(PrerequisiteType.values().length);
    }
}
