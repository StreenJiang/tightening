package com.tightening.netty.protocol.util.sudongx7;

import com.tightening.constant.TighteningResultType;
import com.tightening.constant.TighteningStatus;
import com.tightening.constant.sudongx7.SudongX7Constants;
import com.tightening.dto.TighteningDataDTO;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SudongX7TighteningDataParser {

    private SudongX7TighteningDataParser() {}

    public static TighteningDataDTO parse(int cmd, byte[] data) {
        if (cmd != SudongX7Constants.CMD_TIGHTENING_DATA) {
            throw new IllegalArgumentException("Unexpected cmd: 0x" + Integer.toHexString(cmd));
        }

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        TighteningDataDTO dto = new TighteningDataDTO();

        buf.getShort();                                // stationNumber
        int unitCode = buf.get();                      // 0=kgf.cm/100, 1=N.m/1000
        double divisor = (unitCode == 0) ? 100.0 : 1000.0;

        int torqueRaw = buf.getShort();
        dto.setTorque(torqueRaw / divisor);

        buf.getShort();                                // speed
        int rundownAngle = buf.getShort();
        dto.setRundownAngle((double) rundownAngle);

        int angle = buf.getShort();
        dto.setAngle((double) angle);

        buf.getShort();                                // runTime
        int direction = buf.get();                     // 0=CW, 1=CCW
        dto.setResultType(direction == 0 ? TighteningResultType.TIGHTENING.getCode()
                                        : TighteningResultType.LOOSENING.getCode());

        buf.getShort();                                // remaining
        buf.get();                                     // screwCount
        int status = buf.get();                        // 01=OK, 04=CCW, other=NG
        dto.setTighteningStatus(mapStatus(status));

        buf.get();                                     // errorReport (no DTO field)
        buf.get();                                     // temperature

        double torqueMax = buf.getShort() / divisor;
        dto.setTorqueMaxLimit(torqueMax);

        double torqueMin = buf.getShort() / divisor;
        dto.setTorqueMinLimit(torqueMin);

        double angleMax = buf.getShort();
        dto.setAngleMaxLimit(angleMax);

        double angleMin = buf.getShort();
        dto.setAngleMinLimit(angleMin);

        double rundownAngleMax = buf.getShort();
        dto.setRundownAngleMaxLimit(rundownAngleMax);

        double rundownAngleMin = buf.getShort();
        dto.setRundownAngleMinLimit(rundownAngleMin);

        buf.get();                                     // mode
        // statusFlag, seqNum — not read

        dto.setTighteningId(0L);
        return dto;
    }

    private static Integer mapStatus(int raw) {
        return switch (raw) {
            case 1 -> TighteningStatus.OK.getCode();
            case 4 -> TighteningStatus.NG.getCode();
            default -> TighteningStatus.NG.getCode();
        };
    }
}
