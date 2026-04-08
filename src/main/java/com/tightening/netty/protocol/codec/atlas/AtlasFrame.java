package com.tightening.netty.protocol.codec.atlas;

import com.tightening.constant.atlas.AtlasCommandType;
import com.tightening.constant.atlas.AtlasConstants;
import com.tightening.constant.fit.FitCommandType;
import com.tightening.constant.fit.FitConstants;
import com.tightening.netty.protocol.util.AtlasDataUtils;
import com.tightening.netty.protocol.util.FitDataUtils;
import io.netty.buffer.ByteBufUtil;
import lombok.Data;
import lombok.experimental.Accessors;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Data
@Accessors(chain = true)
public class AtlasFrame {
    private Integer length;                 // 1-4
    private Integer mid;                    // 5-8
    private Integer revision;               // 9-11
    private Integer noAckFlag;              // 12
    private Integer stationId;              // 13-14
    private Integer spindleId;              // 15-16
    private Integer sequenceNumber;         // 17-18
    private Integer numberOfMessageParts;   // 19
    private Integer messagePartsEnd;        // 20
    private byte[] data;                    // 21-length
    private char end = '\0';                // end
    private byte[] attachedData;

    public AtlasFrame(int mid) {
        this(mid, 1, null);
    }

    public AtlasFrame(int mid, int revision) {
        this(mid, revision, null);
    }

    public AtlasFrame(int mid, byte[] data) {
        this(mid, 1, data);
    }

    public AtlasFrame(int mid, Integer revision, byte[] data) {
        this.mid = mid;
        this.revision = revision;
        this.data = data;
        this.length = AtlasConstants.HEADER_LENGTH + (data != null ? data.length : 0);
    }

    @Override
    public String toString() {
        return "AtlasFrame{" +
                "length=" + length +
                ", mid=" + AtlasCommandType.fromMid(mid) +
                ", revision=" + revision +
                ", noAckFlag=" + noAckFlag +
                ", stationId=" + stationId +
                ", spindleId=" + spindleId +
                ", sequenceNumber=" + sequenceNumber +
                ", numberOfMessageParts=" + numberOfMessageParts +
                ", messagePartsEnd=" + messagePartsEnd +
                ", data=" + new String(data, StandardCharsets.US_ASCII) +
                ", end=" + end +
                ", attachedData=" + ByteBufUtil.hexDump(attachedData) +
                '}';
    }

    public static AtlasFrame connectTool() {
        return new AtlasFrame(AtlasCommandType.CONNECT.getMid(), 3);
    }

    public static AtlasFrame subscribeTighteningData() {
        return new AtlasFrame(AtlasCommandType.SUBSCRIBE_DATA.getMid(), 3)
                .setNoAckFlag(1);
    }

    public static AtlasFrame enableTool() {
        return new AtlasFrame(AtlasCommandType.ENABLE.getMid());
    }

    public static AtlasFrame disableTool() {
        return new AtlasFrame(AtlasCommandType.DISABLE.getMid());
    }

    public static AtlasFrame sendPSet(int pSet) {
        return new AtlasFrame(AtlasCommandType.PARAMETER_SET.getMid(),
                              AtlasDataUtils.formatAscii(pSet, 3));
    }

    public static AtlasFrame sendHeartBeat() {
        return new AtlasFrame(AtlasCommandType.HEARTBEAT.getMid(), 1);
    }
}
