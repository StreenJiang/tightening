package com.tightening.netty.protocol.codec.atlas;

import com.tightening.constant.atlas.AtlasCommandType;
import com.tightening.constant.atlas.AtlasConstants;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AtlasFrameTest {

    @Test
    void construct_minimal() {
        AtlasFrame frame = new AtlasFrame(AtlasCommandType.ENABLE.getMid());
        assertThat(frame.getMid()).isEqualTo(43);
        assertThat(frame.getRevision()).isEqualTo(1);
        assertThat(frame.getEnd()).isEqualTo('\0');
        assertThat(frame.getLength()).isEqualTo(AtlasConstants.HEADER_LENGTH);
    }

    @Test
    void construct_withData() {
        byte[] data = "test".getBytes(StandardCharsets.US_ASCII);
        AtlasFrame frame = new AtlasFrame(99, data);
        assertThat(frame.getMid()).isEqualTo(99);
        assertThat(frame.getRevision()).isEqualTo(1);
        assertThat(frame.getData()).isEqualTo(data);
        assertThat(frame.getLength()).isEqualTo(AtlasConstants.HEADER_LENGTH + 4);
    }

    @Test
    void construct_withRevisionAndData() {
        byte[] data = "test".getBytes(StandardCharsets.US_ASCII);
        AtlasFrame frame = new AtlasFrame(99, 5, data);
        assertThat(frame.getMid()).isEqualTo(99);
        assertThat(frame.getRevision()).isEqualTo(5);
        assertThat(frame.getData()).isEqualTo(data);
        assertThat(frame.getLength()).isEqualTo(AtlasConstants.HEADER_LENGTH + 4);
    }

    @Test
    void connectTool() {
        AtlasFrame frame = AtlasFrame.connectTool();
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.CONNECT.getMid());
        assertThat(frame.getRevision()).isEqualTo(3);
    }

    @Test
    void subscribeTighteningData() {
        AtlasFrame frame = AtlasFrame.subscribeTighteningData();
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.SUBSCRIBE_DATA.getMid());
        assertThat(frame.getNoAckFlag()).isEqualTo(1);
    }

    @Test
    void enableTool() {
        AtlasFrame frame = AtlasFrame.enableTool();
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.ENABLE.getMid());
    }

    @Test
    void disableTool() {
        AtlasFrame frame = AtlasFrame.disableTool();
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.DISABLE.getMid());
    }

    @Test
    void sendPSet() {
        AtlasFrame frame = AtlasFrame.sendPSet(5);
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.PARAMETER_SET.getMid());
        assertThat(frame.getData()).isEqualTo("005".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void sendHeartBeat() {
        AtlasFrame frame = AtlasFrame.sendHeartBeat();
        assertThat(frame.getMid()).isEqualTo(AtlasCommandType.HEARTBEAT.getMid());
        assertThat(frame.getRevision()).isEqualTo(1);
    }

    @Test
    void chainSetters() {
        AtlasFrame frame = new AtlasFrame(1)
                .setStationId(5)
                .setSpindleId(2)
                .setSequenceNumber(100);
        assertThat(frame.getStationId()).isEqualTo(5);
        assertThat(frame.getSpindleId()).isEqualTo(2);
        assertThat(frame.getSequenceNumber()).isEqualTo(100);
    }

    @Test
    void toString_containsMeaningfulText() {
        AtlasFrame frame = AtlasFrame.sendPSet(5);
        String str = frame.toString();
        assertThat(str)
                .contains("AtlasFrame")
                .contains("mid")
                .contains("revision");
    }
}
