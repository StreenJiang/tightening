package com.tightening.constant.fit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FitConstantsTest {
    @Test
    void constants_shouldHaveExpectedValues() {
        assertThat(FitConstants.HEAD).isEqualTo((short) 0xAA55);
        assertThat(FitConstants.TAIL).isEqualTo((short) 0x55AA);
        assertThat(FitConstants.COMMAND_OK).isEqualTo((byte) 0x00);
        assertThat(FitConstants.LENGTH_FIELD_OFFSET).isEqualTo(3);
        assertThat(FitConstants.LENGTH_FIELD_LENGTH).isEqualTo(2);
        assertThat(FitConstants.LENGTH_ADJUSTMENT).isEqualTo(2);
        assertThat(FitConstants.INIT_BYTES_TO_STRIP).isEqualTo(0);
    }
}
