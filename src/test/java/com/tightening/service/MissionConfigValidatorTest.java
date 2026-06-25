package com.tightening.service;

import com.tightening.entity.BarCodeMatchingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class MissionConfigValidatorTest {

    private final MissionConfigValidator validator = new MissionConfigValidator(null, null);

    @Test
    @DisplayName("validateKeyCharLength: single char passes")
    void singleCharPasses() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setKeyStartPosition(3)
                .setKeyChar("A");
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }

    @Test
    @DisplayName("validateKeyCharLength: range with matching length passes")
    void rangeMatchingLengthPasses() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setKeyStartPosition(2)
                .setKeyEndPosition(5)
                .setKeyChar("ABCD");
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }

    @Test
    @DisplayName("validateKeyCharLength: mismatch throws")
    void mismatchThrows() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setKeyStartPosition(2)
                .setKeyEndPosition(5)
                .setKeyChar("ABC");
        assertThatThrownBy(() -> validator.validateKeyCharLength(rule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key_char");
    }

    @Test
    @DisplayName("validateKeyCharLength: null keyChar is no-op")
    void nullKeyCharNoOp() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setKeyStartPosition(3)
                .setKeyChar(null);
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }

    @Test
    @DisplayName("validateKeyCharLength: null keyStartPosition is no-op")
    void nullStartPositionNoOp() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setKeyStartPosition(null)
                .setKeyChar("A");
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }
}
