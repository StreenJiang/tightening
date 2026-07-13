package com.tightening.service;

import com.tightening.entity.BarCodeMatchingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class MissionConfigValidatorTest {

    private final MissionConfigValidator validator = new MissionConfigValidator(null, null);

    @Test
    @DisplayName("segments: single char passes")
    void singleCharPasses() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setSegments("[{\"s\":3,\"e\":4,\"v\":\"A\"}]");
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }

    @Test
    @DisplayName("segments: range with matching length passes")
    void rangeMatchingLengthPasses() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setSegments("[{\"s\":2,\"e\":6,\"v\":\"ABCD\"}]");
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }

    @Test
    @DisplayName("segments: length mismatch throws")
    void mismatchThrows() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setSegments("[{\"s\":2,\"e\":6,\"v\":\"ABC\"}]");
        assertThatThrownBy(() -> validator.validateKeyCharLength(rule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不匹配");
    }

    @Test
    @DisplayName("null segments is no-op")
    void nullSegmentsNoOp() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule();
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }

    @Test
    @DisplayName("empty segments is no-op")
    void emptySegmentsNoOp() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule().setSegments("");
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }
}
