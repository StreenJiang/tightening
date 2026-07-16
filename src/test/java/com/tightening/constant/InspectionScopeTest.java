package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionScopeTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(InspectionScope.fromCode(1)).isEqualTo(InspectionScope.ALL);
        assertThat(InspectionScope.fromCode(2)).isEqualTo(InspectionScope.CHOSEN);
    }

    @Test
    void fromCode_shouldThrowForInvalidCode() {
        assertThatThrownBy(() -> InspectionScope.fromCode(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InspectionScope.fromCode(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InspectionScope.fromCode(3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(InspectionScope.values())
                .map(InspectionScope::getCode).distinct().count();
        assertThat(codes).isEqualTo(InspectionScope.values().length);
    }
}
