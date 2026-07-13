package com.tightening.util;

import com.tightening.entity.BarCodeMatchingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    @DisplayName("仅 expectedLength — 长度符合则通过，不符合则拒绝")
    void expectedLengthOnly() {
        var rule = new BarCodeMatchingRule().setExpectedLength(6);
        assertThat(BarcodeMatcher.matches(rule, "ABC123")).isTrue();
        assertThat(BarcodeMatcher.matches(rule, "ABC")).isFalse();
    }

    @Test
    @DisplayName("无任何约束（null segments + null expectedLength）→ 任意匹配")
    void noConstraints() {
        assertThat(BarcodeMatcher.matches(new BarCodeMatchingRule(), "ANYTHING")).isTrue();
    }

    // ---- JSON segments matching ----

    @Nested
    @DisplayName("multi-segment via segments JSON")
    class MultiSegment {

        @Test
        @DisplayName("single segment matches")
        void singleSegmentMatch() {
            var rule = new BarCodeMatchingRule()
                    .setSegments("[{\"s\":0,\"e\":3,\"v\":\"ABC\"}]");
            assertThat(BarcodeMatcher.matches(rule, "ABC123")).isTrue();
        }

        @Test
        @DisplayName("single segment no match")
        void singleSegmentNoMatch() {
            var rule = new BarCodeMatchingRule()
                    .setSegments("[{\"s\":0,\"e\":3,\"v\":\"XYZ\"}]");
            assertThat(BarcodeMatcher.matches(rule, "ABC123")).isFalse();
        }

        @Test
        @DisplayName("two segments both match → true (AND semantics)")
        void twoSegmentsAllMatch() {
            var rule = new BarCodeMatchingRule()
                    .setSegments("[{\"s\":0,\"e\":3,\"v\":\"ABC\"},{\"s\":3,\"e\":6,\"v\":\"123\"}]");
            assertThat(BarcodeMatcher.matches(rule, "ABC123")).isTrue();
        }

        @Test
        @DisplayName("two segments, first fails → false")
        void twoSegmentsFirstFails() {
            var rule = new BarCodeMatchingRule()
                    .setSegments("[{\"s\":0,\"e\":3,\"v\":\"XXX\"},{\"s\":3,\"e\":6,\"v\":\"123\"}]");
            assertThat(BarcodeMatcher.matches(rule, "ABC123")).isFalse();
        }

        @Test
        @DisplayName("two segments, second fails → false")
        void twoSegmentsSecondFails() {
            var rule = new BarCodeMatchingRule()
                    .setSegments("[{\"s\":0,\"e\":3,\"v\":\"ABC\"},{\"s\":3,\"e\":6,\"v\":\"999\"}]");
            assertThat(BarcodeMatcher.matches(rule, "ABC123")).isFalse();
        }

        @Test
        @DisplayName("discontiguous ranges (e.g. 1-4 + 7-8)")
        void discontiguousRanges() {
            var rule = new BarCodeMatchingRule()
                    .setSegments("[{\"s\":1,\"e\":4,\"v\":\"BC1\"},{\"s\":7,\"e\":8,\"v\":\"9\"}]");
            assertThat(BarcodeMatcher.matches(rule, "ABC12389X")).isTrue();
            assertThat(BarcodeMatcher.matches(rule, "ABC12388X")).isFalse();
        }

        @Test
        @DisplayName("empty segments array → no position constraint")
        void emptySegments() {
            var rule = new BarCodeMatchingRule().setSegments("[]");
            assertThat(BarcodeMatcher.matches(rule, "ANYTHING")).isTrue();
        }

        @Test
        @DisplayName("segments + expectedLength both enforced")
        void segmentsWithExpectedLength() {
            var rule = new BarCodeMatchingRule()
                    .setSegments("[{\"s\":0,\"e\":3,\"v\":\"ABC\"}]")
                    .setExpectedLength(6);
            assertThat(BarcodeMatcher.matches(rule, "ABC123")).isTrue();
            assertThat(BarcodeMatcher.matches(rule, "ABC")).isFalse(); // length mismatch
        }

        @Test
        @DisplayName("bad JSON → parse failure returns false")
        void badJsonReturnsFalse() {
            var rule = new BarCodeMatchingRule().setSegments("{not valid json");
            assertThat(BarcodeMatcher.matches(rule, "ANYTHING")).isFalse();
        }

    }
}
