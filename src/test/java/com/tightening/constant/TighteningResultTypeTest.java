package com.tightening.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TighteningResultTypeTest {

    @Test
    void valueOf_shouldReturnAllValues() {
        assertThat(TighteningResultType.valueOf("TIGHTENING")).isNotNull();
        assertThat(TighteningResultType.valueOf("LOOSENING")).isNotNull();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(TighteningResultType.values())
                .map(TighteningResultType::getCode)
                .distinct()
                .count();
        assertThat(codes).isEqualTo(TighteningResultType.values().length);
    }
}
