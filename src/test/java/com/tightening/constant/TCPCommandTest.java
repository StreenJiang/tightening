package com.tightening.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TCPCommandTest {
    @Test
    void values_shouldHaveThreeCommands() {
        assertThat(TCPCommand.values()).hasSize(3);
    }

    @Test
    void valueOf_shouldReturnAllValues() {
        assertThat(TCPCommand.valueOf("TOOL_ENABLE")).isEqualTo(TCPCommand.TOOL_ENABLE);
        assertThat(TCPCommand.valueOf("TOOL_DISABLE")).isEqualTo(TCPCommand.TOOL_DISABLE);
        assertThat(TCPCommand.valueOf("TOOL_PARAMETER_SET")).isEqualTo(TCPCommand.TOOL_PARAMETER_SET);
    }
}
