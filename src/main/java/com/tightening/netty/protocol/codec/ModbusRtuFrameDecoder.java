package com.tightening.netty.protocol.codec;

import com.tightening.util.Crc16Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class ModbusRtuFrameDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 4) return;

        in.markReaderIndex();
        int slaveAddr = in.readByte() & 0xFF;
        int funcCode = in.readByte() & 0xFF;

        int frameLength = calculateFrameLength(funcCode, in);
        if (frameLength < 4 || in.readableBytes() < frameLength - 2) {
            in.resetReaderIndex();
            return;
        }

        in.resetReaderIndex();
        byte[] frameData = new byte[frameLength];
        in.readBytes(frameData);

        byte[] payload = new byte[frameLength - 2];
        System.arraycopy(frameData, 0, payload, 0, frameLength - 2);
        int expectedCrc = ((frameData[frameLength - 1] & 0xFF) << 8) | (frameData[frameLength - 2] & 0xFF);
        int actualCrc = Crc16Utils.compute(payload);

        if (expectedCrc != actualCrc) {
            return;
        }

        out.add(Unpooled.wrappedBuffer(frameData));
    }

    private int calculateFrameLength(int funcCode, ByteBuf in) {
        if ((funcCode & 0x80) != 0) {
            return 5;
        }
        if (funcCode == 0x03) {
            if (in.readableBytes() < 1) return -1;
            int byteCount = in.getByte(in.readerIndex()) & 0xFF;
            return 3 + byteCount + 2;
        }
        if (funcCode == 0x06) {
            return 8;
        }
        return -1;
    }
}
