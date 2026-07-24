package com.tightening.util;

public final class ModbusUtils {

    private ModbusUtils() {}

    public static byte[] buildReadFrame(int slaveAddr, int register, int count) {
        byte[] payload = new byte[6];
        payload[0] = (byte) slaveAddr;
        payload[1] = 0x03;
        payload[2] = (byte) ((register >> 8) & 0xFF);
        payload[3] = (byte) (register & 0xFF);
        payload[4] = (byte) ((count >> 8) & 0xFF);
        payload[5] = (byte) (count & 0xFF);
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[8];
        System.arraycopy(payload, 0, frame, 0, 6);
        frame[6] = (byte) (crc & 0xFF);
        frame[7] = (byte) ((crc >> 8) & 0xFF);
        return frame;
    }

    public static byte[] buildWriteFrame(int slaveAddr, int register, int value) {
        byte[] payload = new byte[6];
        payload[0] = (byte) slaveAddr;
        payload[1] = 0x06;
        payload[2] = (byte) ((register >> 8) & 0xFF);
        payload[3] = (byte) (register & 0xFF);
        payload[4] = (byte) ((value >> 8) & 0xFF);
        payload[5] = (byte) (value & 0xFF);
        int crc = Crc16Utils.compute(payload);
        byte[] frame = new byte[8];
        System.arraycopy(payload, 0, frame, 0, 6);
        frame[6] = (byte) (crc & 0xFF);
        frame[7] = (byte) ((crc >> 8) & 0xFF);
        return frame;
    }
}
