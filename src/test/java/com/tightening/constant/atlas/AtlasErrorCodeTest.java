package com.tightening.constant.atlas;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtlasErrorCodeTest {

    @Test
    void fromCode_known() {
        assertThat(AtlasErrorCode.fromCode(0)).contains(AtlasErrorCode.NO_ERROR);
        assertThat(AtlasErrorCode.fromCode(1)).contains(AtlasErrorCode.INVALID_DATA);
        assertThat(AtlasErrorCode.fromCode(99)).contains(AtlasErrorCode.UNKNOWN_MID);
    }

    @Test
    void fromCode_unknown() {
        assertThat(AtlasErrorCode.fromCode(-1)).isEmpty();
        assertThat(AtlasErrorCode.fromCode(1000)).isEmpty();
    }

    @Test
    void fromCodeOrThrow_known() {
        assertThat(AtlasErrorCode.fromCodeOrThrow(0)).isEqualTo(AtlasErrorCode.NO_ERROR);
    }

    @Test
    void fromCodeOrThrow_unknown() {
        assertThatThrownBy(() -> AtlasErrorCode.fromCodeOrThrow(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void toString_containsCodeAndDescription() {
        assertThat(AtlasErrorCode.NO_ERROR).hasToString("[00] No Error");
        assertThat(AtlasErrorCode.UNKNOWN_MID).hasToString("[99] Unknown MID");
    }

    @Test
    void getCode_getDescription() {
        assertThat(AtlasErrorCode.NO_ERROR.getCode()).isZero();
        assertThat(AtlasErrorCode.NO_ERROR.getDescription()).isEqualTo("No Error");
    }

    @Test
    void codes_unique() {
        long distinctCount = java.util.Arrays.stream(AtlasErrorCode.values())
                .mapToInt(AtlasErrorCode::getCode)
                .distinct()
                .count();
        assertThat(distinctCount).isEqualTo(AtlasErrorCode.values().length);
    }
}
