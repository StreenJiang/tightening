package com.tightening.util;

public final class Crc16Utils {

    private static final int POLYNOMIAL = 0xA001;
    private static final int INITIAL = 0xFFFF;

    private Crc16Utils() {}

    public static int compute(byte[] data) {
        int crc = INITIAL;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ POLYNOMIAL;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }
}
