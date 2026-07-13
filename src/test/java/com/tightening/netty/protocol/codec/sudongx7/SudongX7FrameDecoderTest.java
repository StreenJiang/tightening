package com.tightening.netty.protocol.codec.sudongx7;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SudongX7FrameDecoder")
class SudongX7FrameDecoderTest {

    @Test
    @DisplayName("valid tightening data frame → decoded")
    void validFrame() {
        byte[] frame = SudongX7Frame.buildFrame(0x2781, new byte[]{0x00, 0x01, 0x02});
        EmbeddedChannel ch = new EmbeddedChannel(new SudongX7FrameDecoder());

        ch.writeInbound(Unpooled.wrappedBuffer(frame));
        ByteBuf out = ch.readInbound();

        assertThat(out).isNotNull();
        assertThat(out.readableBytes()).isEqualTo(5);
        assertThat(out.readShort()).isEqualTo((short) 0x2781);
        ch.finishAndReleaseAll();
    }

    @Test
    @DisplayName("CRC mismatch → frame discarded")
    void crcMismatch() {
        byte[] frame = SudongX7Frame.buildFrame(0x2781, new byte[]{0x01});
        frame[frame.length - 4] ^= 0x01;

        EmbeddedChannel ch = new EmbeddedChannel(new SudongX7FrameDecoder());
        ch.writeInbound(Unpooled.wrappedBuffer(frame));
        ByteBuf out = ch.readInbound();
        assertThat(out).isNull();
        ch.finishAndReleaseAll();
    }

    @Test
    @DisplayName("tail mismatch → frame discarded")
    void tailMismatch() {
        byte[] frame = SudongX7Frame.buildFrame(0x2781, new byte[]{0x01});
        frame[frame.length - 1] = 0x00;

        EmbeddedChannel ch = new EmbeddedChannel(new SudongX7FrameDecoder());
        ch.writeInbound(Unpooled.wrappedBuffer(frame));
        ByteBuf out = ch.readInbound();
        assertThat(out).isNull();
        ch.finishAndReleaseAll();
    }

    @Test
    @DisplayName("incomplete frame → no output (waiting)")
    void incomplete() {
        byte[] frame = SudongX7Frame.buildFrame(0x2781, new byte[]{0x01});
        ByteBuf partial = Unpooled.wrappedBuffer(frame, 0, 4);

        EmbeddedChannel ch = new EmbeddedChannel(new SudongX7FrameDecoder());
        ch.writeInbound(partial);
        ByteBuf out = ch.readInbound();
        assertThat(out).isNull();
        ch.finishAndReleaseAll();
    }

    @Test
    @DisplayName("sticky: two frames back-to-back")
    void stickyFrames() {
        byte[] f1 = SudongX7Frame.buildFrame(0x2781, new byte[]{0x01});
        byte[] f2 = SudongX7Frame.buildFrame(0x2781, new byte[]{0x02});
        byte[] combined = new byte[f1.length + f2.length];
        System.arraycopy(f1, 0, combined, 0, f1.length);
        System.arraycopy(f2, 0, combined, f1.length, f2.length);

        EmbeddedChannel ch = new EmbeddedChannel(new SudongX7FrameDecoder());
        ch.writeInbound(Unpooled.wrappedBuffer(combined));
        ByteBuf out1 = ch.readInbound();
        assertThat(out1).isNotNull();
        out1.release();
        ch.finishAndReleaseAll();
    }
}
