package com.tightening.netty.protocol.codec;

import com.tightening.constant.fit.FitConstants;
import com.tightening.netty.protocol.fit.FitFrame;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class FitFrameCodec extends MessageToMessageCodec<ByteBuf, FitFrame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, FitFrame msg, List<Object> out) throws Exception {
        log.info("Encoding msg: {}", msg);

        ByteBuf buf = ctx.alloc().buffer();
        buf.writeShort(msg.getHead());
        buf.writeByte(msg.getCmdType());
        buf.writeShortLE(msg.getDataLength());
        buf.writeBytes(msg.getData());
        buf.writeShort(msg.getTail());

        log.info("Encoding msg done, buffer: {}", ByteBufUtil.hexDump(buf));
        out.add(buf);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        log.info("Decoding msg buffer: {}", ByteBufUtil.hexDump(msg));

        short head = msg.readShort();
        if (head == FitConstants.HEAD) {
            byte cmdType = msg.readByte();
            short dataLength = msg.readShortLE();
            byte[] data = new byte[dataLength];

            short tail = -1;
            if (dataLength > 0 && msg.readableBytes() >= dataLength + 2) {
                msg.readBytes(data);
                tail = msg.readShort();
            }

            if (tail == FitConstants.TAIL) {
                FitFrame fitFrame = new FitFrame(cmdType, data);
                log.info("Decoding done for msg: {}", fitFrame);

                out.add(fitFrame);
            } else {
                log.warn("TAIL validation failed...");
            }
        } else {
            log.warn("HEAD validation failed...");
        }
    }
}
