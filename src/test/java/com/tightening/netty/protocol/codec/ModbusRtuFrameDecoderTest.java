package com.tightening.netty.protocol.codec;

import com.tightening.util.Crc16Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModbusRtuFrameDecoderTest {

    @Test
    @DisplayName("03 响应帧 -> 完整解码")
    void decodeReadHoldingRegistersResponse() {
        byte[] payload = hexToBytes("09030400010002");
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[payload.length + 2];
        System.arraycopy(payload, 0, frame, 0, payload.length);
        frame[payload.length] = (byte) (crc & 0xFF);
        frame[payload.length + 1] = (byte) ((crc >> 8) & 0xFF);

        EmbeddedChannel channel = new EmbeddedChannel(new ModbusRtuFrameDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(frame));

        ByteBuf out = channel.readInbound();
        assertThat(out).isNotNull();
        assertThat(out.readableBytes()).isEqualTo(frame.length);
        out.release();
    }

    @Test
    @DisplayName("06 写响应帧 -> 固定 8 字节")
    void decodeWriteSingleRegisterResponse() {
        byte[] payload = hexToBytes("090600000080");
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[payload.length + 2];
        System.arraycopy(payload, 0, frame, 0, payload.length);
        frame[payload.length] = (byte) (crc & 0xFF);
        frame[payload.length + 1] = (byte) ((crc >> 8) & 0xFF);

        EmbeddedChannel channel = new EmbeddedChannel(new ModbusRtuFrameDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(frame));

        ByteBuf out = channel.readInbound();
        assertThat(out).isNotNull();
        assertThat(out.readableBytes()).isEqualTo(8);
        out.release();
    }

    @Test
    @DisplayName("异常响应帧 -> 固定 5 字节")
    void decodeErrorResponse() {
        byte[] payload = hexToBytes("098302");
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[payload.length + 2];
        System.arraycopy(payload, 0, frame, 0, payload.length);
        frame[payload.length] = (byte) (crc & 0xFF);
        frame[payload.length + 1] = (byte) ((crc >> 8) & 0xFF);

        EmbeddedChannel channel = new EmbeddedChannel(new ModbusRtuFrameDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(frame));

        ByteBuf out = channel.readInbound();
        assertThat(out).isNotNull();
        assertThat(out.readableBytes()).isEqualTo(5);
        out.release();
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }
}
