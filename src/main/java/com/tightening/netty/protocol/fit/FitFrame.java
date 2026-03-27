package com.tightening.netty.protocol.fit;

import com.tightening.constant.fit.FitCommandType;
import com.tightening.constant.fit.FitConstants;
import com.tightening.netty.protocol.CommandFrame;
import lombok.Data;

import java.util.Arrays;

@Data
public class FitFrame extends CommandFrame {
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
}
