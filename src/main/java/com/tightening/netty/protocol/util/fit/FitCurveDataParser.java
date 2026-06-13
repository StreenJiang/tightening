package com.tightening.netty.protocol.util.fit;

import com.tightening.constant.DeviceType;
import com.tightening.dto.CurveDataDTO;
import com.tightening.netty.protocol.codec.fit.CurveDataSamples;
import com.tightening.util.Converter;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * FIT 协议曲线数据解析器。
 * 数据格式：[tighteningId(4B)][allCurvePointsData]，每点 12B：时间(4B) + 扭矩(4B) + 角度(4B)
 */
@Slf4j
public final class FitCurveDataParser {

    private FitCurveDataParser() {}

    public static CurveDataDTO parse(byte[] data) {
        if (data == null || data.length < 4) {
            throw new IllegalArgumentException("数据区长度不足，最小需要4字节（拧紧ID）");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int offset = 0;

        int tighteningId = buffer.getInt(offset);
        offset += 4;

        int remainingBytes = data.length - offset;
        int totalPoints = remainingBytes / 12;

        if (totalPoints == 0) {
            throw new IllegalArgumentException("没有曲线点数据");
        }

        CurveDataSamples samples = new CurveDataSamples(tighteningId);

        for (int i = 0; i < totalPoints; i++) {
            float time = buffer.getFloat(offset);
            offset += 4;

            float torque = buffer.getFloat(offset);
            offset += 4;

            float angle = buffer.getFloat(offset);
            offset += 4;

            samples.addPoint(time, torque, angle);

            if (i < 3 || i >= totalPoints - 1) {
                log.debug("Point {}: time={}s, torque={}Nm, angle={}°",
                          i + 1,
                          String.format("%.4f", time),
                          String.format("%.2f", torque),
                          String.format("%.2f", angle));
            }
        }

        CurveDataDTO curveData = new CurveDataDTO();
        curveData.setTighteningId(tighteningId);
        curveData.setDataType(DeviceType.FIT_FTC6.getId());
        curveData.setDataSamples(Converter.fromList(samples.getPoints()));
        curveData.setTimestamp(FitDataUtils.getCurrentTimestampStr());

        log.info("解析完成：tighteningId={}, 总点数={}", tighteningId, totalPoints);

        return curveData;
    }
}
