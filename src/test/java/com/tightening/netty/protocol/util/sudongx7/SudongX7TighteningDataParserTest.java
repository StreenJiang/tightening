package com.tightening.netty.protocol.util.sudongx7;

import com.tightening.constant.TighteningResultType;
import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("SudongX7TighteningDataParser")
class SudongX7TighteningDataParserTest {

    private static final int CMD = 0x2781;

    @Test
    @DisplayName("wrong cmd → throws")
    void wrongCmdThrows() {
        assertThatThrownBy(() -> SudongX7TighteningDataParser.parse(0x9999, new byte[35]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parses torque with kgf.cm divisor (unitCode=0)")
    void torqueKgCm() {
        byte[] data = baseData();
        data[2] = 0;                               // unitCode=0 → /100
        writeShortLE(data, 3, (short) 150);        // torque=150 → 1.50
        TighteningDataDTO dto = SudongX7TighteningDataParser.parse(CMD, data);
        assertThat(dto.getTorque()).isCloseTo(1.50, within(0.001));
    }

    @Test
    @DisplayName("parses torque with N.m divisor (unitCode=1)")
    void torqueNm() {
        byte[] data = baseData();
        data[2] = 1;                               // unitCode=1 → /1000
        writeShortLE(data, 3, (short) 1500);       // torque=1500 → 1.500
        TighteningDataDTO dto = SudongX7TighteningDataParser.parse(CMD, data);
        assertThat(dto.getTorque()).isCloseTo(1.500, within(0.001));
    }

    @Test
    @DisplayName("direction CW → TIGHTENING")
    void directionCw() {
        byte[] data = baseData();
        data[13] = 0;
        TighteningDataDTO dto = SudongX7TighteningDataParser.parse(CMD, data);
        assertThat(dto.getResultType()).isEqualTo(TighteningResultType.TIGHTENING.getCode());
    }

    @Test
    @DisplayName("direction CCW → LOOSENING")
    void directionCcw() {
        byte[] data = baseData();
        data[13] = 1;
        TighteningDataDTO dto = SudongX7TighteningDataParser.parse(CMD, data);
        assertThat(dto.getResultType()).isEqualTo(TighteningResultType.LOOSENING.getCode());
    }

    @Test
    @DisplayName("status 01 → OK")
    void statusOk() {
        byte[] data = baseData();
        data[17] = 1;
        TighteningDataDTO dto = SudongX7TighteningDataParser.parse(CMD, data);
        assertThat(dto.getTighteningStatus()).isEqualTo(TighteningStatus.OK.getCode());
    }

    @Test
    @DisplayName("status other → NG")
    void statusNg() {
        byte[] data = baseData();
        data[17] = 5;
        TighteningDataDTO dto = SudongX7TighteningDataParser.parse(CMD, data);
        assertThat(dto.getTighteningStatus()).isEqualTo(TighteningStatus.NG.getCode());
    }

    @Test
    @DisplayName("limit fields parsed correctly")
    void limits() {
        byte[] data = baseData();
        data[2] = 0;                                // unitCode=0 → /100
        writeShortLE(data, 3, (short) 500);         // torque
        writeShortLE(data, 9, (short) 200);         // angle
        data[17] = 1;                               // status=OK
        writeShortLE(data, 20, (short) 800);        // torqueMax → 8.00
        writeShortLE(data, 22, (short) 200);        // torqueMin → 2.00
        writeShortLE(data, 24, (short) 300);        // angleMax
        writeShortLE(data, 26, (short) 10);         // angleMin

        TighteningDataDTO dto = SudongX7TighteningDataParser.parse(CMD, data);
        assertThat(dto.getTorque()).isCloseTo(5.0, within(0.01));
        assertThat(dto.getAngle()).isEqualTo(200.0);
        assertThat(dto.getTorqueMaxLimit()).isCloseTo(8.00, within(0.01));
        assertThat(dto.getTorqueMinLimit()).isCloseTo(2.00, within(0.01));
        assertThat(dto.getAngleMaxLimit()).isEqualTo(300.0);
    }

    @Test
    @DisplayName("tighteningId default is 0")
    void tighteningIdDefault() {
        byte[] data = baseData();
        data[17] = 1;
        TighteningDataDTO dto = SudongX7TighteningDataParser.parse(CMD, data);
        assertThat(dto.getTighteningId()).isEqualTo(0L);
    }

    private static byte[] baseData() {
        return new byte[35];
    }

    private static void writeShortLE(byte[] buf, int offset, short value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
