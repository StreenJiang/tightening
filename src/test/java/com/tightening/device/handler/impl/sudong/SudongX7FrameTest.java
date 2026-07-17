package com.tightening.device.handler.impl.sudong;

import com.tightening.netty.protocol.codec.sudongx7.SudongX7Frame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SudongX7Frame")
class SudongX7FrameTest {

    @Test
    @DisplayName("empty constructor creates frame with default values")
    void emptyConstructor() {
        SudongX7Frame frame = new SudongX7Frame();
        assertThat(frame.getCmd()).isZero();
        assertThat(frame.getData()).isNull();
    }

    @Test
    @DisplayName("parameterized constructor sets cmd and data")
    void parameterizedConstructor() {
        byte[] data = {0x01, 0x02, 0x03};
        SudongX7Frame frame = new SudongX7Frame(0x2781, data);
        assertThat(frame.getCmd()).isEqualTo(0x2781);
        assertThat(frame.getData()).containsExactly(data);
    }

    @Test
    @DisplayName("setter updates cmd")
    void setCmd() {
        SudongX7Frame frame = new SudongX7Frame();
        frame.setCmd(0x8500);
        assertThat(frame.getCmd()).isEqualTo(0x8500);
    }

    @Test
    @DisplayName("setter updates data")
    void setData() {
        SudongX7Frame frame = new SudongX7Frame();
        byte[] data = {0x10, 0x20};
        frame.setData(data);
        assertThat(frame.getData()).containsExactly(data);
    }

    @Test
    @DisplayName("chain setters")
    void chainSetters() {
        byte[] data = {0x01};
        SudongX7Frame frame = new SudongX7Frame()
                .setCmd(0x2781)
                .setData(data);
        assertThat(frame.getCmd()).isEqualTo(0x2781);
        assertThat(frame.getData()).containsExactly(data);
    }

    @Test
    @DisplayName("isTighteningData returns true for CMD_TIGHTENING_DATA (0x2781)")
    void isTighteningData() {
        assertThat(SudongX7Frame.isTighteningData(0x2781)).isTrue();
        assertThat(SudongX7Frame.isTighteningData(0x0000)).isFalse();
    }

    @Test
    @DisplayName("isPsetResponse returns true for CMD_PSET_RESPONSE (0x8205)")
    void isPsetResponse() {
        assertThat(SudongX7Frame.isPsetResponse(0x8205)).isTrue();
        assertThat(SudongX7Frame.isPsetResponse(0x0000)).isFalse();
    }

    @Test
    @DisplayName("isToolRunning returns true for CMD_TOOL_RUNNING (0x8500)")
    void isToolRunning() {
        assertThat(SudongX7Frame.isToolRunning(0x8500)).isTrue();
        assertThat(SudongX7Frame.isToolRunning(0x0000)).isFalse();
    }

    @Test
    @DisplayName("isError returns true for CMD_ERROR (0xCFFC)")
    void isError() {
        assertThat(SudongX7Frame.isError(0xCFFC)).isTrue();
        assertThat(SudongX7Frame.isError(0x0000)).isFalse();
    }
}
