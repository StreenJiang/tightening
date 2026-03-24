package com.tightening.netty.protocol.codec;

import com.tightening.netty.protocol.fit.FitFrame;
import com.tightening.util.fit.FitUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;

public class FitFrameCodec extends ByteToMessageCodec<FitFrame> {
    @Override
    protected void encode(ChannelHandlerContext ctx, FitFrame msg, ByteBuf out) throws Exception {
        out.writeShort(msg.getHead());
        out.writeByte(msg.getCmdType());
        out.writeShortLE(msg.getDataLength());
        out.writeBytes(msg.getData());
        out.writeShort(msg.getTail());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        short head = in.readShort();
        if (head == FitUtils.HEAD) {
            byte cmdType = in.readByte();
            short dataLength = in.readShortLE();
            byte[] data = new byte[dataLength];
            in.readBytes(data);
            short tail = in.readShort();

            out.add(new FitFrame(cmdType, data));
        }
    }
}
