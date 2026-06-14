package com.tightening.constant.fit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FitCommandTypeTest {

    @Test
    void fromCode_known() {
        assertThat(FitCommandType.fromCode((byte) 0x01)).isEqualTo(FitCommandType.PARAMETER_SET);
        assertThat(FitCommandType.fromCode((byte) 0x83)).isEqualTo(FitCommandType.CURVE);
        assertThat(FitCommandType.fromCode((byte) 0x86)).isEqualTo(FitCommandType.HEARTBEAT_ACK);
    }

    @Test
    void fromCode_unknown() {
        assertThat(FitCommandType.fromCode((byte) 0x00)).isNull();
        assertThat(FitCommandType.fromCode((byte) 0xFF)).isNull();
    }

    @Test
    void getCode_getName() {
        assertThat(FitCommandType.PARAMETER_SET.getCode()).isEqualTo((byte) 0x01);
        assertThat(FitCommandType.PARAMETER_SET.getName()).isEqualTo("程序号");
    }

    @Test
    void toString_containsCodeAndName() {
        assertThat(FitCommandType.PARAMETER_SET).hasToString("0x01 (程序号)");
        assertThat(FitCommandType.HEARTBEAT_ACK).hasToString("0x86 (心跳应答)");
    }

    @Test
    void codes_unique() {
        long distinctCount = java.util.Arrays.stream(FitCommandType.values())
                .map(FitCommandType::getCode)
                .distinct()
                .count();
        assertThat(distinctCount).isEqualTo(FitCommandType.values().length);
    }
}
