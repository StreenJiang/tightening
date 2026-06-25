package com.tightening.constant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BarCodeRuleTypeTest {

    @Test
    void fromCode_shouldReturnCorrectValue() {
        assertThat(BarCodeRuleType.fromCode(1)).contains(BarCodeRuleType.PRODUCT_TRACE);
        assertThat(BarCodeRuleType.fromCode(2)).contains(BarCodeRuleType.PARTS_BARCODE);
    }

    @Test
    void fromCode_shouldReturnEmptyForInvalidCode() {
        assertThat(BarCodeRuleType.fromCode(-1)).isEmpty();
        assertThat(BarCodeRuleType.fromCode(0)).isEmpty();
        assertThat(BarCodeRuleType.fromCode(3)).isEmpty();
    }

    @Test
    void codes_shouldBeUnique() {
        var codes = java.util.Arrays.stream(BarCodeRuleType.values())
                .map(BarCodeRuleType::getCode).distinct().count();
        assertThat(codes).isEqualTo(BarCodeRuleType.values().length);
    }
}
