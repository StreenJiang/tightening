package com.tightening.netty.protocol.codec.fit;

import com.tightening.constant.fit.FitCommandType;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FitCurveDataReassemblerTest {

    @Test
    void nonCurveFrame_passesThrough() {
        EmbeddedChannel channel = new EmbeddedChannel(new FitCurveDataReassembler());
        try {
            FitFrame input = FitFrame.unlockTool();

            channel.writeInbound(input);
            FitFrame output = channel.readInbound();

            assertNotNull(output);
            assertThat(output.getCmdType()).isEqualTo(FitCommandType.ENABLE_DISABLE.getCode());
            assertThat(output.getData()).containsExactly(input.getData());
        } finally {
            channel.finish();
        }
    }

    @Test
    void normalArrival_reassembles() {
        EmbeddedChannel channel = new EmbeddedChannel(new FitCurveDataReassembler());
        try {
            long tighteningId = 12345L;
            short totalPackets = 3;
            byte[] point1 = createPointData(1);
            byte[] point2 = createPointData(2);
            byte[] point3 = createPointData(3);

            // Write packet 1
            channel.writeInbound(createCurvePacket(tighteningId, totalPackets, (short) 1, point1));
            assertNull(channel.readInbound());

            // Write packet 2
            channel.writeInbound(createCurvePacket(tighteningId, totalPackets, (short) 2, point2));
            assertNull(channel.readInbound());

            // Write packet 3 — triggers reassembly
            channel.writeInbound(createCurvePacket(tighteningId, totalPackets, (short) 3, point3));
            FitFrame output = channel.readInbound();

            assertNotNull(output);
            assertThat(output.getCmdType()).isEqualTo(FitCommandType.CURVE.getCode());

            // Verify tighteningId in result data (first 4 bytes, LE)
            ByteBuffer resultBuf = ByteBuffer.wrap(output.getData()).order(ByteOrder.LITTLE_ENDIAN);
            assertThat(Integer.toUnsignedLong(resultBuf.getInt())).isEqualTo(tighteningId);

            // Verify all 3 points are present in order
            byte[] pointsData = Arrays.copyOfRange(output.getData(), 4, output.getData().length);
            assertThat(pointsData.length).isEqualTo(36);

            byte[] expected = new byte[36];
            System.arraycopy(point1, 0, expected, 0, 12);
            System.arraycopy(point2, 0, expected, 12, 12);
            System.arraycopy(point3, 0, expected, 24, 12);
            assertThat(pointsData).containsExactly(expected);

            // No more frames
            assertNull(channel.readInbound());
        } finally {
            channel.finish();
        }
    }

    @Test
    void outOfOrder_reassembles() {
        EmbeddedChannel channel = new EmbeddedChannel(new FitCurveDataReassembler());
        try {
            long tighteningId = 67890L;
            short totalPackets = 3;
            byte[] point1 = createPointData(1);
            byte[] point2 = createPointData(2);
            byte[] point3 = createPointData(3);

            // Write packets out of order: 1, 3, 2
            channel.writeInbound(createCurvePacket(tighteningId, totalPackets, (short) 1, point1));
            assertNull(channel.readInbound());

            channel.writeInbound(createCurvePacket(tighteningId, totalPackets, (short) 3, point3));
            assertNull(channel.readInbound());

            channel.writeInbound(createCurvePacket(tighteningId, totalPackets, (short) 2, point2));
            FitFrame output = channel.readInbound();

            assertNotNull(output);
            assertThat(output.getCmdType()).isEqualTo(FitCommandType.CURVE.getCode());

            // Points must be in sequential order 1, 2, 3 (not 1, 3, 2)
            byte[] pointsData = Arrays.copyOfRange(output.getData(), 4, output.getData().length);
            assertThat(pointsData.length).isEqualTo(36);

            byte[] expected = new byte[36];
            System.arraycopy(point1, 0, expected, 0, 12);
            System.arraycopy(point2, 0, expected, 12, 12);
            System.arraycopy(point3, 0, expected, 24, 12);
            assertThat(pointsData).containsExactly(expected);

            assertNull(channel.readInbound());
        } finally {
            channel.finish();
        }
    }

    @Test
    void duplicatePacket_ignored() {
        EmbeddedChannel channel = new EmbeddedChannel(new FitCurveDataReassembler());
        try {
            long tighteningId = 11111L;
            short totalPackets = 3;
            byte[] point1 = createPointData(1);
            byte[] point1Alternate = createPointData(99); // different data for duplicate
            byte[] point2 = createPointData(2);
            byte[] point3 = createPointData(3);

            // Send packet 1
            channel.writeInbound(createCurvePacket(tighteningId, totalPackets, (short) 1, point1));
            assertNull(channel.readInbound());

            // Send duplicate of packet 1 with different data — should be ignored
            channel.writeInbound(createCurvePacket(tighteningId, totalPackets, (short) 1, point1Alternate));
            assertNull(channel.readInbound());

            // Send remaining packets
            channel.writeInbound(createCurvePacket(tighteningId, totalPackets, (short) 2, point2));
            assertNull(channel.readInbound());

            channel.writeInbound(createCurvePacket(tighteningId, totalPackets, (short) 3, point3));
            FitFrame output = channel.readInbound();

            assertNotNull(output);
            assertThat(output.getCmdType()).isEqualTo(FitCommandType.CURVE.getCode());

            // First point should be original point1, NOT point1Alternate
            byte[] pointsData = Arrays.copyOfRange(output.getData(), 4, output.getData().length);
            assertThat(pointsData.length).isEqualTo(36);

            byte[] firstPoint = Arrays.copyOfRange(pointsData, 0, 12);
            assertThat(firstPoint).containsExactly(point1);

            assertNull(channel.readInbound());
        } finally {
            channel.finish();
        }
    }

    // ---- helpers ----

    private static FitFrame createCurvePacket(long tighteningId, short totalPackets, short currentPacket, byte[] pointsData) {
        ByteBuffer buf = ByteBuffer.allocate(8 + pointsData.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt((int) tighteningId);
        buf.putShort(totalPackets);
        buf.putShort(currentPacket);
        buf.put(pointsData);
        return new FitFrame(FitCommandType.CURVE.getCode(), buf.array());
    }

    private static byte[] createPointData(int pointIndex) {
        byte[] data = new byte[12];
        Arrays.fill(data, (byte) pointIndex);
        return data;
    }
}
