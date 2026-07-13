package com.tightening.netty.protocol.codec.sudongx7;

import com.tightening.constant.sudongx7.SudongX7Constants;
import com.tightening.util.Crc16Utils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class SudongX7FrameDecoder extends ByteToMessageDecoder {

    private static final byte HDR_H = SudongX7Constants.FRAME_HEADER_HIGH;
    private static final byte HDR_L = SudongX7Constants.FRAME_HEADER_LOW;
    private static final byte TAIL_H = SudongX7Constants.FRAME_TAIL_HIGH;
    private static final byte TAIL_L = SudongX7Constants.FRAME_TAIL_LOW;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int scanStart = in.readerIndex();

        while (in.readableBytes() >= 5) {
            // scan for frame header without consuming bytes
            int headerIdx = -1;
            int limit = in.readerIndex() + in.readableBytes() - 1;
            for (int i = in.readerIndex(); i < limit; i++) {
                if (in.getByte(i) == HDR_H && in.getByte(i + 1) == HDR_L) {
                    headerIdx = i;
                    break;
                }
            }
            if (headerIdx < 0) {
                // no header found — advance past all but last byte (might be partial header)
                in.readerIndex(Math.max(scanStart, limit));
                return;
            }

            // skip junk bytes before header and the header itself
            in.readerIndex(headerIdx + 2);

            if (in.readableBytes() < 1) {
                in.readerIndex(headerIdx);
                return;
            }

            int n = in.readByte() & 0xFF;

            if (in.readableBytes() < n + 2) {
                in.readerIndex(headerIdx);
                return;
            }

            // read payload minus CRC (n-2 bytes) + CRC (2 bytes) + tail (2 bytes)
            byte[] frameData = new byte[n - 2];
            in.readBytes(frameData);
            int expectedCrc = in.readUnsignedShortLE();
            byte tailHigh = in.readByte();
            byte tailLow = in.readByte();

            if (tailHigh != TAIL_H || tailLow != TAIL_L) {
                in.readerIndex(headerIdx + 1);
                continue;
            }

            int actualCrc = Crc16Utils.compute(frameData);
            if (actualCrc != expectedCrc) {
                in.readerIndex(headerIdx + 1);
                continue;
            }

            out.add(ctx.alloc().buffer().writeBytes(frameData));
            return;
        }
    }
}
