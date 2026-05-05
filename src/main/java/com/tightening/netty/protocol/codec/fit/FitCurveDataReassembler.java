package com.tightening.netty.protocol.codec.fit;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.tightening.constant.fit.FitCommandType;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class FitCurveDataReassembler extends MessageToMessageDecoder<FitFrame> {

    private final Map<Long, CurveReassemblyState> reassemblyCache = new ConcurrentHashMap<>();
    private static final long REASSEMBLY_TIMEOUT_MS = 10000;

    @Override
    protected void decode(ChannelHandlerContext ctx, FitFrame msg, List<Object> out) throws Exception {
        // 非 CURVE 命令，直接传递
        if (msg.getCmdType() != FitCommandType.CURVE.getCode()) {
            out.add(msg);
            return;
        }

        try {
            FitFrame reassembledFrame = handleCurvePacket(ctx, msg);
            if (reassembledFrame != null) {
                out.add(reassembledFrame);
            }
        } catch (Exception e) {
            log.error("Curve packet reassembly error", e);
            ctx.fireExceptionCaught(e);
        }
    }

    private FitFrame handleCurvePacket(ChannelHandlerContext ctx, FitFrame currentFrame) {
        byte[] data = currentFrame.getData();

        // 解析包头：拧紧ID(4) + 总包数(2) + 当前包号(2) = 8字节
        if (data.length < 8) {
            log.warn("Curve packet data too short: {}", data.length);
            return null;
        }

        ByteBuffer headerBuffer = ByteBuffer.wrap(data, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
        long tighteningId = Integer.toUnsignedLong(headerBuffer.getInt(0));
        short totalPackets = headerBuffer.getShort(4);
        short currentPacket = headerBuffer.getShort(6);

        // 提取当前包的曲线点数据（去掉8字节包头）
        // 注意：FitFrame.data 已经不包含 tail（在 FitFrameCodec 中已处理）
        byte[] curvePointsData = Arrays.copyOfRange(data, 8, data.length);

        // 获取或创建重组状态
        CurveReassemblyState state = reassemblyCache.computeIfAbsent(
                tighteningId,
                id -> new CurveReassemblyState(tighteningId, totalPackets)
        );

        // 添加当前包的曲线点数据
        state.addPacket(currentPacket, curvePointsData);

        log.debug("Received curve packet {}/{}, points data length: {}",
                  currentPacket, totalPackets, curvePointsData.length);

        // 检查是否收齐
        if (state.isComplete()) {
            log.info("Curve packet reassembly complete: tighteningId={}, totalPackets={}",
                     tighteningId, totalPackets);

            // 重组完成，生成完整的 FitFrame
            byte[] completeData = state.buildCompleteData();
            reassemblyCache.remove(tighteningId);

            // 创建完整的 FitFrame
            // 完整数据结构：tighteningId(4) + allCurvePointsData
            return new FitFrame(FitCommandType.CURVE.getCode(), completeData);
        } else {
            // 启动超时检查（如果是第一个包）
            if (currentPacket == 1) {
                scheduleTimeoutCheck(ctx, tighteningId);
            }
            return null; // 等待更多包
        }
    }

    private void scheduleTimeoutCheck(ChannelHandlerContext ctx, long tighteningId) {
        // 这里的 executor 不需要 释放资源，因为这里释放掉会导致 event loop 无法正常工作
        ctx.executor().schedule(() -> {
            CurveReassemblyState state = reassemblyCache.remove(tighteningId);
            if (state != null && !state.isComplete()) {
                String errorMsg = String.format(
                        "Curve packet reassembly timeout: ID=%d, received=%d/%d",
                        tighteningId, state.getReceivedCount(), state.getTotalPackets()
                );
                log.warn(errorMsg);
                ctx.fireExceptionCaught(new TimeoutException(errorMsg));
            }
        }, REASSEMBLY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static class CurveReassemblyState {
        private final long tighteningId;
        private final Map<Short, byte[]> packets; // key: packetNum, value: curvePointsData
        @Getter
        private final short totalPackets;
        @Getter
        private short receivedCount;

        public CurveReassemblyState(long tighteningId, short totalPackets) {
            this.tighteningId = tighteningId;
            this.totalPackets = totalPackets;
            this.packets = new ConcurrentHashMap<>();
            this.receivedCount = 0;
        }

        public void addPacket(short packetNum, byte[] curvePointsData) {
            if (!packets.containsKey(packetNum)) {
                packets.put(packetNum, curvePointsData);
                receivedCount++;
                log.debug("Added packet {}, total received: {}", packetNum, receivedCount);
            } else {
                log.warn("Duplicate packet received: {}", packetNum);
            }
        }

        public boolean isComplete() {
            return receivedCount >= totalPackets;
        }

        /**
         * 构建完整的数据区：
         * [tighteningId(4)][所有曲线点数据...]
         */
        public byte[] buildCompleteData() {
            // 计算所有曲线点数据的总长度
            int totalPointsDataLength = 0;
            for (short i = 1; i <= totalPackets; i++) {
                byte[] packetData = packets.get(i);
                if (packetData != null) {
                    totalPointsDataLength += packetData.length;
                }
            }

            // 完整数据结构：tighteningId(4) + allCurvePointsData
            byte[] completeData = new byte[4 + totalPointsDataLength];

            ByteBuffer buffer = ByteBuffer.wrap(completeData).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt((int) tighteningId);

            // 按包号顺序拼接所有曲线点数据
            int offset = 4;
            for (short i = 1; i <= totalPackets; i++) {
                byte[] packetData = packets.get(i);
                if (packetData != null) {
                    System.arraycopy(packetData, 0, completeData, offset, packetData.length);
                    offset += packetData.length;
                }
            }

            int totalPoints = totalPointsDataLength / 12; // 每点12字节
            log.debug("Built complete data: tighteningId={}, totalPoints={}, dataLength={}",
                      tighteningId, totalPoints, completeData.length);

            return completeData;
        }
    }
}
