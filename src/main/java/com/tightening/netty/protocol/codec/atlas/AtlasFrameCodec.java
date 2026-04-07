package com.tightening.netty.protocol.codec.atlas;

import com.tightening.constant.atlas.AtlasCommandType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

import static com.tightening.netty.protocol.util.AtlasDataUtils.decodeCurveData;
import static com.tightening.netty.protocol.util.AtlasDataUtils.decodeStandard;
import static com.tightening.netty.protocol.util.AtlasDataUtils.formatAscii;
import static com.tightening.netty.protocol.util.AtlasDataUtils.parseAsciiInt;

@Slf4j
public class AtlasFrameCodec extends MessageToMessageCodec<ByteBuf, AtlasFrame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, AtlasFrame msg, List<Object> out) throws Exception {
        log.info("Encoding sending msg: {}", msg);

        ByteBuf buf = ctx.alloc().buffer();

        // 1-4: length (4字节 ASCII)
        buf.writeBytes(formatAscii(msg.getLength(), 4));

        // 5-8: mid (4字节 ASCII)
        buf.writeBytes(formatAscii(msg.getMid(), 4));

        // 9-11: revision (3字节 ASCII)
        buf.writeBytes(formatAscii(msg.getRevision(), 3));

        // 12: noAckFlag (1字节 ASCII)
        buf.writeBytes(formatAscii(msg.getNoAckFlag(), 1));

        // 13-14: stationId (2字节 ASCII)
        buf.writeBytes(formatAscii(msg.getStationId(), 2));

        // 15-16: spindleId (2字节 ASCII)
        buf.writeBytes(formatAscii(msg.getSpindleId(), 2));

        // 17-18: sequenceNumber (2字节 ASCII)
        buf.writeBytes(formatAscii(msg.getSequenceNumber(), 2));

        // 19: numberOfMessageParts (1字节 ASCII)
        buf.writeBytes(formatAscii(msg.getNumberOfMessageParts(), 1));

        // 20: messagePartsEnd (1字节 ASCII)
        buf.writeBytes(formatAscii(msg.getMessagePartsEnd(), 1));

        // 21-length: data (二进制数据，原样写入)
        if (msg.getData() != null) {
            buf.writeBytes(msg.getData());
        }

        // end: '\0' 结束符
        buf.writeByte(0);

        log.info("Encoding sending msg done, buf: {}", ByteBufUtil.hexDump(buf));
        out.add(buf);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        log.info("Decoding received msg, buf: {}", ByteBufUtil.hexDump(msg));

        if (msg.readableBytes() < 20) {
            return;
        }
        msg.markReaderIndex();

        // ========== 读取固定头部（20字节 ASCII 字段）==========
        Integer length = parseAsciiInt(msg, 4);
        Integer mid = parseAsciiInt(msg, 4);
        Integer revision = parseAsciiInt(msg, 3);
        Integer noAckFlag = parseAsciiInt(msg, 1);
        Integer stationId = parseAsciiInt(msg, 2);
        Integer spindleId = parseAsciiInt(msg, 2);
        Integer sequenceNumber = parseAsciiInt(msg, 2);
        Integer numberOfMessageParts = parseAsciiInt(msg, 1);
        Integer messagePartsEnd = parseAsciiInt(msg, 1);

        if (length == null || mid == null || revision == null) {
            log.warn("Critical field is null: length={}, mid={}, revision={}", length, mid, revision);
            msg.resetReaderIndex();
            return; // 数据不足或畸形，等待/丢弃
        }

        // ========== 根据 mid 选择解析策略 ==========
        byte[] data;

        if (Objects.equals(mid, AtlasCommandType.CURVE_DATA.getMid())) {
            // TODO: 这里弄错了，curve data 是 attachment 的形式，这里改一下
            data = decodeCurveData(ctx, msg, length);
        } else {
            data = decodeStandard(msg, length);
        }

        // 数据不足，等待累积
        if (data == null) {
            msg.resetReaderIndex();
            return;
        }

        // ========== 构建 AtlasFrame 对象 ==========
        AtlasFrame frame = new AtlasFrame(mid, revision, data)
                .setLength(length)
                .setNoAckFlag(noAckFlag)
                .setStationId(stationId)
                .setSpindleId(spindleId)
                .setSequenceNumber(sequenceNumber)
                .setNumberOfMessageParts(numberOfMessageParts)
                .setMessagePartsEnd(messagePartsEnd);

        log.info("Decoded AtlasFrame: {}", frame);
        out.add(frame);
    }

}
