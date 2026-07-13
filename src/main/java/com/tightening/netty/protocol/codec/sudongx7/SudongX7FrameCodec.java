package com.tightening.netty.protocol.codec.sudongx7;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

public class SudongX7FrameCodec extends MessageToMessageCodec<ByteBuf, SudongX7Frame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, SudongX7Frame frame, List<Object> out) {
        byte[] data = frame.getData() != null ? frame.getData() : new byte[0];
        byte[] frameBytes = SudongX7Frame.buildFrame(frame.getCmd(), data);
        out.add(Unpooled.wrappedBuffer(frameBytes));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 2) return;
        int cmd = in.readShort() & 0xFFFF;
        byte[] data = new byte[in.readableBytes()];
        in.readBytes(data);
        out.add(new SudongX7Frame(cmd, data));
    }
}
