package com.tightening.constant.atlas;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AtlasConstantsTest {
    @Test
    void constants_shouldHaveExpectedValues() {
        assertThat(AtlasConstants.HEADER_LENGTH).isEqualTo(20);
        assertThat(AtlasConstants.LENGTH_FIELD_OFFSET).isEqualTo(0);
        assertThat(AtlasConstants.LENGTH_FIELD_LENGTH).isEqualTo(4);
        assertThat(AtlasConstants.LENGTH_ADJUSTMENT).isEqualTo(1);
        assertThat(AtlasConstants.INIT_BYTES_TO_STRIP).isEqualTo(0);
    }
}
