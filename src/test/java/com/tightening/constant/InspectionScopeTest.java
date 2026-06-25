package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InspectionScopeTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(InspectionScope.fromCode(1)).contains(InspectionScope.ALL);
        assertThat(InspectionScope.fromCode(2)).contains(InspectionScope.CHOSEN);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(InspectionScope.fromCode(-1)).isEmpty();
        assertThat(InspectionScope.fromCode(0)).isEmpty();
        assertThat(InspectionScope.fromCode(3)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(InspectionScope.values())
                .map(InspectionScope::getCode).distinct().count();
        assertThat(codes).isEqualTo(InspectionScope.values().length);
    }
}
