package com.tightening.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolConstantsTest {
    @Test
    void cmdTimeoutMs_shouldBe5000() {
        assertThat(ToolConstants.CMD_TIMEOUT_MS).isEqualTo(5000L);
    }
}
