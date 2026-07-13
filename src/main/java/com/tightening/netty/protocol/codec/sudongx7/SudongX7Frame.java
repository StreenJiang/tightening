package com.tightening.netty.protocol.codec.sudongx7;

import com.tightening.constant.sudongx7.SudongX7Constants;
import com.tightening.util.Crc16Utils;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SudongX7Frame {

    private int cmd;
    private byte[] data;

    public SudongX7Frame() {}

    public SudongX7Frame(int cmd, byte[] data) {
        this.cmd = cmd;
        this.data = data;
    }

    // ---- static factory methods: return full frame bytes (header + length + cmd + data + CRC + tail) ----

    private static final byte[] LOCK_PAYLOAD   = {0x01, 0x00, 0x00, 0x02, 0x00};
    private static final byte[] UNLOCK_PAYLOAD = {0x01, 0x00, 0x00, 0x00, 0x00};

    public static byte[] lock() {
        return buildFrame(LOCK_PAYLOAD);
    }

    public static byte[] unlock() {
        return buildFrame(UNLOCK_PAYLOAD);
    }

    public static byte[] sendPSet(int psetId) {
        byte[] payload = new byte[5];
        payload[0] = 0x02;
        payload[1] = 0x05;
        payload[2] = (byte) (psetId & 0xFF);
        payload[3] = (byte) ((psetId >> 8) & 0xFF);
        payload[4] = 0x00;
        return buildFrame(payload);
    }

    public static byte[] sendHeartbeat() {
        return buildFrame(new byte[0]);
    }

    // ---- command classification ----

    public static boolean isTighteningData(int cmd) {
        return cmd == SudongX7Constants.CMD_TIGHTENING_DATA;
    }

    public static boolean isPsetResponse(int cmd) {
        return cmd == SudongX7Constants.CMD_PSET_RESPONSE;
    }

    public static boolean isToolRunning(int cmd) {
        return cmd == SudongX7Constants.CMD_TOOL_RUNNING;
    }

    public static boolean isError(int cmd) {
        return cmd == SudongX7Constants.CMD_ERROR;
    }

    // ---- package-private: shared by FrameCodec ----

    static byte[] buildFrame(byte[] payload) {
        int n = payload.length + 2; // cmd+data + crc(2)
        int crc = Crc16Utils.compute(payload);

        byte[] frame = new byte[2 + 1 + n + 2]; // header(2) + length(1) + n + tail(2)
        frame[0] = SudongX7Constants.FRAME_HEADER_HIGH;
        frame[1] = SudongX7Constants.FRAME_HEADER_LOW;
        frame[2] = (byte) n;
        System.arraycopy(payload, 0, frame, 3, payload.length);
        frame[3 + payload.length] = (byte) (crc & 0xFF);
        frame[4 + payload.length] = (byte) ((crc >> 8) & 0xFF);
        frame[frame.length - 2] = SudongX7Constants.FRAME_TAIL_HIGH;
        frame[frame.length - 1] = SudongX7Constants.FRAME_TAIL_LOW;
        return frame;
    }

    static byte[] buildFrame(int cmd, byte[] data) {
        byte[] payload = new byte[2 + data.length];
        payload[0] = (byte) ((cmd >> 8) & 0xFF);
        payload[1] = (byte) (cmd & 0xFF);
        System.arraycopy(data, 0, payload, 2, data.length);
        return buildFrame(payload);
    }
}
