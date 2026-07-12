package com.tightening.netty.protocol.codec.fit;

import com.tightening.constant.fit.FitCommandType;
import com.tightening.constant.fit.FitConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FitFrameTest {

    @Test
    void construct() {
        FitFrame frame = new FitFrame(FitCommandType.ENABLE_DISABLE.getCode(), new byte[]{0x01});
        assertThat(frame.getHead()).isEqualTo(FitConstants.HEAD);
        assertThat(frame.getTail()).isEqualTo(FitConstants.TAIL);
        assertThat(frame.getCmdType()).isEqualTo(FitCommandType.ENABLE_DISABLE.getCode());
        assertThat(frame.getDataLength()).isEqualTo((short) 1);
        assertThat(frame.getData()).containsExactly(0x01);
    }

    @Test
    void construct_nullData() {
        FitFrame frame = new FitFrame(FitCommandType.ENABLE_DISABLE.getCode(), null);
        assertThat(frame.getData()).isEmpty();
        assertThat(frame.getDataLength()).isZero();
    }

    @Test
    void unlockTool() {
        FitFrame frame = FitFrame.unlockTool();
        assertThat(frame.getCmdType()).isEqualTo(FitCommandType.ENABLE_DISABLE.getCode());
        assertThat(frame.getData()).containsExactly(0x01);
    }

    @Test
    void lockTool() {
        FitFrame frame = FitFrame.lockTool();
        assertThat(frame.getCmdType()).isEqualTo(FitCommandType.ENABLE_DISABLE.getCode());
        assertThat(frame.getData()).containsExactly(0x00);
    }

    @Test
    void sendPSet() {
        FitFrame frame = FitFrame.sendPSet(7);
        assertThat(frame.getCmdType()).isEqualTo(FitCommandType.PARAMETER_SET.getCode());
        assertThat(frame.getData()).containsExactly((byte) 7);
    }

    @Test
    void sendHeartBeat() {
        FitFrame frame = FitFrame.sendHeartBeat();
        assertThat(frame.getCmdType()).isEqualTo(FitCommandType.HEARTBEAT_REQ.getCode());
        assertThat(frame.getData()).isNotEmpty();
    }

    @Test
    void toString_containsEnumName() {
        FitFrame frame = FitFrame.unlockTool();
        String str = frame.toString();
        assertThat(str)
                .contains("FitFrame")
                .contains("cmdType");
    }
}
