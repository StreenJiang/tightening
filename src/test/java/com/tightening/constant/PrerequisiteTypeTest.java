package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrerequisiteTypeTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(PrerequisiteType.fromCode(1)).isEqualTo(PrerequisiteType.SAME_TRACE);
        assertThat(PrerequisiteType.fromCode(2)).isEqualTo(PrerequisiteType.MATERIAL_TRACE);
        assertThat(PrerequisiteType.fromCode(3)).isEqualTo(PrerequisiteType.INSPECTION_CHAIN);
    }

    @Test
    void fromCode_shouldThrowForInvalidCode() {
        assertThatThrownBy(() -> PrerequisiteType.fromCode(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrerequisiteType.fromCode(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrerequisiteType.fromCode(4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(PrerequisiteType.values())
                .map(PrerequisiteType::getCode).distinct().count();
        assertThat(codes).isEqualTo(PrerequisiteType.values().length);
    }
}
