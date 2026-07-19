package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarCodeRuleTypeTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(BarCodeRuleType.fromCode(1)).isEqualTo(BarCodeRuleType.PRODUCT_TRACE);
        assertThat(BarCodeRuleType.fromCode(2)).isEqualTo(BarCodeRuleType.MATERIAL_BARCODE);
    }

    @Test
    void fromCode_shouldThrowForInvalidCode() {
        assertThatThrownBy(() -> BarCodeRuleType.fromCode(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BarCodeRuleType.fromCode(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BarCodeRuleType.fromCode(3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(BarCodeRuleType.values())
                .map(BarCodeRuleType::getCode).distinct().count();
        assertThat(codes).isEqualTo(BarCodeRuleType.values().length);
    }
}
