package com.tightening.device.handler.impl.sudong;

import com.tightening.netty.protocol.codec.sudongx7.SudongX7Frame;
import com.tightening.netty.protocol.codec.sudongx7.SudongX7FrameCodec;
import com.tightening.netty.protocol.codec.sudongx7.SudongX7FrameDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SudongX7FrameCodec")
class SudongX7FrameCodecTest {

    private EmbeddedChannel encoder;
    private EmbeddedChannel pipeline;

    @BeforeEach
    void setUp() {
        encoder = new EmbeddedChannel(new SudongX7FrameCodec());
        pipeline = new EmbeddedChannel(new SudongX7FrameDecoder(), new SudongX7FrameCodec());
    }

    @AfterEach
    void cleanUp() {
        encoder.finishAndReleaseAll();
        pipeline.finishAndReleaseAll();
    }

    @Test
    @DisplayName("encode: SudongX7Frame -> bytes with header 0x55AA")
    void encode() {
        byte[] data = {0x10, 0x20, 0x30};
        SudongX7Frame frame = new SudongX7Frame(0x2781, data);

        encoder.writeOutbound(frame);
        ByteBuf out = encoder.readOutbound();

        assertThat(out).isNotNull();
        assertThat(out.readByte()).isEqualTo((byte) 0x55);
        assertThat(out.readByte()).isEqualTo((byte) 0xAA);
        out.release();
    }

    @Test
    @DisplayName("decode: full frame bytes -> SudongX7Frame via decoder+codec")
    void decode() {
        byte[] lockFrame = SudongX7Frame.lock();

        pipeline.writeInbound(Unpooled.wrappedBuffer(lockFrame));
        SudongX7Frame frame = pipeline.readInbound();

        assertThat(frame).isNotNull();
        assertThat(frame.getCmd()).isEqualTo(0x0100);
    }

    @Test
    @DisplayName("round-trip: encode then decode preserves cmd and data")
    void roundTrip() {
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        SudongX7Frame original = new SudongX7Frame(0x2781, data);

        encoder.writeOutbound(original);
        ByteBuf encoded = encoder.readOutbound();
        assertThat(encoded).isNotNull();

        pipeline.writeInbound(encoded);
        SudongX7Frame decoded = pipeline.readInbound();

        assertThat(decoded).isNotNull();
        assertThat(decoded.getCmd()).isEqualTo(original.getCmd());
        assertThat(decoded.getData()).containsExactly(original.getData());
    }

    @Test
    @DisplayName("encode frame with null data still produces header")
    void encodeWithNullData() {
        SudongX7Frame frame = new SudongX7Frame(0x8500, null);

        encoder.writeOutbound(frame);
        ByteBuf out = encoder.readOutbound();

        assertThat(out).isNotNull();
        assertThat(out.readByte()).isEqualTo((byte) 0x55);
        assertThat(out.readByte()).isEqualTo((byte) 0xAA);
        out.release();
    }

    @Test
    @DisplayName("PSET frame decode via full pipeline")
    void roundTripPSet() {
        byte[] psetFrame = SudongX7Frame.sendPSet(5);

        pipeline.writeInbound(Unpooled.wrappedBuffer(psetFrame));
        SudongX7Frame frame = pipeline.readInbound();

        assertThat(frame).isNotNull();
        assertThat(frame.getCmd()).isEqualTo(0x0205);
    }

    @Test
    @DisplayName("encode-then-decode via separate channels (heartbeat)")
    void encodeAndPseudoDecode() {
        byte[] hbFrame = SudongX7Frame.sendHeartbeat();

        pipeline.writeInbound(Unpooled.wrappedBuffer(hbFrame));
        SudongX7Frame frame = pipeline.readInbound();

        // Heartbeat frame has empty cmd+data payload, so the codec
        // cannot produce a SudongX7Frame from zero-length payload bytes
        assertThat(frame).isNull();
    }
}
