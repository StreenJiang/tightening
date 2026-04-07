package com.tightening.netty.protocol.codec.atlas;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class AtlasLengthDecoder extends LengthFieldBasedFrameDecoder {
    public AtlasLengthDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
                              int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected long getUnadjustedFrameLength(ByteBuf buf, int offset, int length, ByteOrder order) {
        byte[] lenBytes = new byte[length];
        buf.getBytes(offset, lenBytes);
        String lenStr = new String(lenBytes, StandardCharsets.US_ASCII);
        try {
            return Long.parseLong(lenStr);
        } catch (NumberFormatException e) {
            throw new CorruptedFrameException("Invalid length field: " + lenStr);
        }
    }
}
