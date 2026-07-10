package com.tightening.util;

import com.tightening.entity.BarCodeMatchingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BarcodeMatcher")
class BarcodeMatcherTest {

    @Test
    @DisplayName("code 为 null → false")
    void nullCode() {
        assertThat(BarcodeMatcher.matches(new BarCodeMatchingRule(), null)).isFalse();
    }

    @Test
    @DisplayName("位置匹配成功 → true")
    void positionMatch() {
        var rule = new BarCodeMatchingRule()
                .setKeyStartPosition(0).setKeyEndPosition(3).setKeyChar("ABC");
        assertThat(BarcodeMatcher.matches(rule, "ABC123")).isTrue();
    }

    @Test
    @DisplayName("位置不匹配 → false")
    void positionNoMatch() {
        var rule = new BarCodeMatchingRule()
                .setKeyStartPosition(0).setKeyEndPosition(3).setKeyChar("XYZ");
        assertThat(BarcodeMatcher.matches(rule, "ABC123")).isFalse();
    }

    @Test
    @DisplayName("仅 expectedLength — 长度符合则通过，不符合则拒绝")
    void expectedLengthOnly() {
        var rule = new BarCodeMatchingRule().setExpectedLength(6);
        assertThat(BarcodeMatcher.matches(rule, "ABC123")).isTrue();
        assertThat(BarcodeMatcher.matches(rule, "ABC")).isFalse();
    }

    @Test
    @DisplayName("无任何约束 → 任意匹配")
    void noConstraints() {
        assertThat(BarcodeMatcher.matches(new BarCodeMatchingRule(), "ANYTHING")).isTrue();
    }
}
