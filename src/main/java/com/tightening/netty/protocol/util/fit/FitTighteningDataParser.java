package com.tightening.netty.protocol.util.fit;

import com.tightening.constant.FitAngleStatus;
import com.tightening.constant.FitTorqueStatus;
import com.tightening.constant.TighteningResultType;
import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * FIT 协议拧紧数据解析器。
 * 数据区格式：拧紧ID(4B) + 状态(1B) + 程序号(1B) + 条码长度(1B) + 条码(NB) + 扭矩(4B) + 角度(4B) + 时间戳(7B)
 */
@Slf4j
public final class FitTighteningDataParser {

    private FitTighteningDataParser() {}

    public static TighteningDataDTO parse(byte[] data) {
        int offset = 0;

        TighteningDataDTO tighteningData = new TighteningDataDTO();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int tighteningId = buffer.getInt(offset);
        log.debug("tighteningId=" + tighteningId);
        offset += 4;

        if (data[offset] == 1) {
            tighteningData.setTighteningStatus(TighteningStatus.OK.getCode());
            tighteningData.setTorqueStatus(FitTorqueStatus.OK.getCode());
            tighteningData.setAngleStatus(FitAngleStatus.OK.getCode());
        } else {
            tighteningData.setTighteningStatus(TighteningStatus.NG.getCode());
            tighteningData.setTorqueStatus(FitTorqueStatus.NG.getCode());
            tighteningData.setAngleStatus(FitAngleStatus.NG.getCode());
        }
        log.debug("tightening_status=" + tighteningData.getTighteningStatus());
        offset += 1;

        tighteningData.setParameterSet(data[offset] & 0xFF);
        log.debug("parameter_set=" + tighteningData.getParameterSet());
        offset += 1;

        int barcodeLength = data[offset] & 0xFF;
        log.debug("barcode length=" + barcodeLength);
        offset += 1;

        if (barcodeLength == 0) {
            offset += 1;
        } else {
            byte[] barcodeBytes = new byte[barcodeLength];
            System.arraycopy(data, offset, barcodeBytes, 0, barcodeLength);
            try {
                String barcode = new String(barcodeBytes, "GBK");
                log.debug("barcode=" + barcode);
            } catch (Exception e) {
                log.warn("Failed to decode barcode", e);
            }
            offset += barcodeLength;
        }

        float torque = buffer.getFloat(offset);
        tighteningData.setTorque(torque);
        log.debug("torque=" + torque);
        offset += 4;

        float angleFloat = buffer.getFloat(offset);
        int angle = (int) angleFloat;
        tighteningData.setAngle(angle);
        log.debug("angle=" + angle);
        offset += 4;

        if (angle >= 0) {
            tighteningData.setResultType(TighteningResultType.TIGHTENING.getCode());
        } else {
            tighteningData.setResultType(TighteningResultType.LOOSENING.getCode());
        }

        String timestamp = FitDataUtils.parseBcdTimestamp(data, offset);
        tighteningData.setTimestamp(timestamp);
        log.debug("timestamp=" + timestamp);

        return tighteningData;
    }
}
