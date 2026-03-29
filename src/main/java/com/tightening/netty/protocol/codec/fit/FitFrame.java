package com.tightening.netty.protocol.codec.fit;

import com.tightening.constant.fit.FitCommandType;
import com.tightening.constant.fit.FitConstants;
import com.tightening.netty.protocol.util.FitDataUtils;
import lombok.Data;

import java.util.Arrays;

@Data
public class FitFrame {
    private short head;
    private byte cmdType;
    private short dataLength;
    private byte[] data;
    private short tail;

    public FitFrame(byte cmdType, byte[] data) {
        this.head = FitConstants.HEAD;
        this.cmdType = cmdType;
        this.data = data != null ? data.clone() : new byte[0];
        this.dataLength = (short) this.data.length;
        this.tail = FitConstants.TAIL;
    }

    @Override
    public String toString() {
        return "FitFrame{" +
                "head=" + head +
                ", cmdType=" + FitCommandType.fromCode(cmdType) +
                ", dataLength=" + dataLength +
                ", data=" + Arrays.toString(data) +
                ", tail=" + tail +
                '}';
    }

    public static FitFrame enableTool() {
        return new FitFrame(FitCommandType.ENABLE_DISABLE.getCode(), new byte[] { 0x01 });
    }

    public static FitFrame disableTool() {
        return new FitFrame(FitCommandType.ENABLE_DISABLE.getCode(), new byte[] { 0x00 });
    }

    public static FitFrame sendPSet(int pSet) {
        return new FitFrame(FitCommandType.PARAMETER_SET.getCode(), new byte[] { (byte) pSet });
    }

    public static FitFrame sendHeartBeat() {
        byte[] bytes = FitDataUtils.getCurrentTimestampBytes();
        return new FitFrame(FitCommandType.HEARTBEAT_REQ.getCode(), bytes);
    }
}
