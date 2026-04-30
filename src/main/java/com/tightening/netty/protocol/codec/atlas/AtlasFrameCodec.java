package com.tightening.netty.protocol.codec.atlas;

import com.tightening.constant.atlas.AtlasCommandType;
import com.tightening.constant.atlas.AtlasConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.tightening.netty.protocol.util.AtlasDataUtils.decodeCurveData;
import static com.tightening.netty.protocol.util.AtlasDataUtils.decodeData;
import static com.tightening.netty.protocol.util.AtlasDataUtils.formatAscii;
import static com.tightening.netty.protocol.util.AtlasDataUtils.parseAsciiInt;

@Slf4j
public class AtlasFrameCodec extends MessageToMessageCodec<ByteBuf, AtlasFrame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, AtlasFrame msg, List<Object> out) throws Exception {
        log.info("Encoding sending msg: {}", msg);

        ByteBuf buf = ctx.alloc().buffer();
        buf.writeBytes(formatAscii(msg.getLength(), 4))                     // 1-4: length
                .writeBytes(formatAscii(msg.getMid(), 4))                   // 5-8: mid
                .writeBytes(formatAscii(msg.getRevision(), 3))              // 9-11: revision
                .writeBytes(formatAscii(msg.getNoAckFlag(), 1))             // 12: noAckFlag
                .writeBytes(formatAscii(msg.getStationId(), 2))             // 13-14: stationId
                .writeBytes(formatAscii(msg.getSpindleId(), 2))             // 15-16: spindleId
                .writeBytes(formatAscii(msg.getSequenceNumber(), 2))        // 17-18: sequenceNumber
                .writeBytes(formatAscii(msg.getNumberOfMessageParts(), 1))  // 19: numberOfMessageParts
                .writeBytes(formatAscii(msg.getMessagePartsEnd(), 1));      // 20: messagePartsEnd

        if (msg.getData() != null) {
            buf.writeBytes(msg.getData());                                          // 21-length: data
        }
        buf.writeByte(0);                                                     // end: '\0'

        log.info("Encoding sending msg done, buf: {}", buf.toString(buf.readerIndex(), buf.readableBytes(), StandardCharsets.US_ASCII));
        out.add(buf);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        log.info("Decoding received msg, buf: {}", msg.toString(msg.readerIndex(), msg.readableBytes(), StandardCharsets.US_ASCII));

        if (msg.readableBytes() < AtlasConstants.HEADER_LENGTH) {
            return;
        }
        msg.markReaderIndex();

        // ========== 读取固定头部（20字节 ASCII 字段）==========
        Integer length = parseAsciiInt(msg, 4);                 // 1-4: length
        Integer mid = parseAsciiInt(msg, 4);                    // 5-8: mid
        Integer revision = parseAsciiInt(msg, 3);               // 9-11: revision
        Integer noAckFlag = parseAsciiInt(msg, 1);              // 12: noAckFlag
        Integer stationId = parseAsciiInt(msg, 2);              // 13-14: stationId
        Integer spindleId = parseAsciiInt(msg, 2);              // 15-16: spindleId
        Integer sequenceNumber = parseAsciiInt(msg, 2);         // 17-18: sequenceNumber
        Integer numberOfMessageParts = parseAsciiInt(msg, 1);   // 19: numberOfMessageParts
        Integer messagePartsEnd = parseAsciiInt(msg, 1);        // 20: messagePartsEnd

        if (length == null || mid == null) {
            log.warn("Critical field is null: length={}, mid={}", length, mid);
            msg.resetReaderIndex();
            return; // 数据不足或畸形，等待/丢弃
        }

        int remainingLength = length - AtlasConstants.HEADER_LENGTH;
        if (msg.readableBytes() < remainingLength) {
            msg.resetReaderIndex();
            return; // 数据不足
        }

        // ========== 根据 mid 选择解析策略 ==========
        byte[] data;
        byte[] attachedData = null;

        if (Objects.equals(mid, AtlasCommandType.CURVE_DATA.getMid())) {
            data = decodeData(msg, remainingLength);
            if (data == null || data.length < 5) {
                log.warn("Critical field is null: curve data length need 5 bytes but get ={}", data == null ? null : data.length);
                return;
            }
            int curveDataLength = parseAsciiInt(Arrays.copyOfRange(data, data.length - 5, data.length));
            curveDataLength *= 2; // 这里的长度指的是几个值，Curve data 每个值占两个 bytes
            attachedData = decodeCurveData(msg, curveDataLength);
        } else {
            data = decodeData(msg, remainingLength);
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

        if (Objects.equals(mid, AtlasCommandType.CURVE_DATA.getMid())) {
            frame.setAttachedData(attachedData);
        }

        log.info("Decoded AtlasFrame: {}", frame);
        out.add(frame);
    }

}
