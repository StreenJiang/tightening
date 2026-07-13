package com.tightening.constant.sudongx7;

public final class SudongX7Constants {

    private SudongX7Constants() {}

    public static final byte FRAME_HEADER_HIGH = (byte) 0x55;
    public static final byte FRAME_HEADER_LOW = (byte) 0xAA;
    public static final byte FRAME_TAIL_HIGH = 0x0D;
    public static final byte FRAME_TAIL_LOW = 0x0A;

    /** 2-byte command words as they appear on wire (big-endian). */
    public static final int CMD_LOCK_UNLOCK = 0x0100;
    public static final int CMD_PSET = 0x0205;
    public static final int CMD_TOOL_RUNNING = 0x8500;
    public static final int CMD_ERROR = 0xCFFC;
    public static final int CMD_TIGHTENING_DATA = 0x2781;
    public static final int CMD_PSET_RESPONSE = 0x8205;
}
