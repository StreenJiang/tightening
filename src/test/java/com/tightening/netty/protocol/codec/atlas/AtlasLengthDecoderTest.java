package com.tightening.netty.protocol.codec.atlas;

import com.tightening.constant.atlas.AtlasConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtlasLengthDecoderTest {

    private EmbeddedChannel newChannel(int maxFrameLength) {
        return new EmbeddedChannel(new AtlasLengthDecoder(
                maxFrameLength,
                AtlasConstants.LENGTH_FIELD_OFFSET,
                AtlasConstants.LENGTH_FIELD_LENGTH,
                AtlasConstants.LENGTH_ADJUSTMENT,
                AtlasConstants.INIT_BYTES_TO_STRIP));
    }

    @Test
    void decode_validAsciiLength_shouldExtractFrame() {
        EmbeddedChannel channel = newChannel(1024);

        // LENGTH_FIELD_OFFSET=0, LENGTH_FIELD_LENGTH=4, LENGTH_ADJUSTMENT=1
        // length field "0018" → unadjusted=18, adjusted=19
        // total frame = 0 + 4 + 19 = 23 bytes
        byte[] frame = "0018ABCDEFGHIJKLMNOPQR\0".getBytes(StandardCharsets.US_ASCII);
        assertThat(frame).hasSize(23);

        channel.writeInbound(Unpooled.copiedBuffer(frame));

        ByteBuf result = channel.readInbound();
        assertThat(result).isNotNull();
        assertThat(result.readableBytes()).isEqualTo(23);

        byte[] actual = new byte[result.readableBytes()];
        result.readBytes(actual);
        assertThat(actual).containsExactly(frame);
        result.release();
        channel.finish();
    }

    @Test
    void decode_nonNumericLengthField_shouldThrow() {
        EmbeddedChannel channel = newChannel(1024);
        byte[] frame = "ABCDSomeData\0".getBytes(StandardCharsets.US_ASCII);

        // The exception may occur during writeInbound (pipeline processes immediately)
        // or during finish (decoder processes remaining buffered data on channel close).
        // Handle both paths.
        try {
            channel.writeInbound(Unpooled.copiedBuffer(frame));
        } catch (CorruptedFrameException e) {
            assertThat(e).hasMessageContaining("Invalid length field");
            // The decoder still has buffered data; finish() will also throw
            assertThatThrownBy(channel::finish).isInstanceOf(CorruptedFrameException.class);
            return;
        }

        // If writeInbound didn't throw, finish() should throw
        assertThatThrownBy(channel::finish)
                .isInstanceOf(CorruptedFrameException.class)
                .hasMessageContaining("Invalid length field");
    }

    @Test
    void decode_zeroLength_shouldExtractFrame() {
        EmbeddedChannel channel = newChannel(1024);

        // Length field "0000" → unadjusted=0, adjusted=1
        // total frame = 0 + 4 + 1 = 5 bytes (just the length field + null terminator?)
        byte[] frame = "0000\0".getBytes(StandardCharsets.US_ASCII);

        channel.writeInbound(Unpooled.copiedBuffer(frame));

        ByteBuf result = channel.readInbound();
        assertThat(result).isNotNull();
        assertThat(result.readableBytes()).isEqualTo(5);
        result.release();
        channel.finish();
    }
}
