package com.tightening.netty.protocol.util.fit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FitDataUtilsTest {

    // ---- bcdToInt ----

    @Test
    void bcdToInt_59() {
        assertThat(FitDataUtils.bcdToInt((byte) 0x59)).isEqualTo(59);
    }

    @Test
    void bcdToInt_zero() {
        assertThat(FitDataUtils.bcdToInt((byte) 0x00)).isZero();
    }

    // ---- parseAlarmData ----

    @Test
    void parseAlarmData_null() {
        assertThatThrownBy(() -> FitDataUtils.parseAlarmData(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseAlarmData_tooShort() {
        byte[] data = new byte[10];
        assertThatThrownBy(() -> FitDataUtils.parseAlarmData(data))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseAlarmData_valid() {
        // 18 bytes: alarmCode(2) + level(1) + infoLength(1) + info(0) + BCD timestamp(7) + padding(7)
        byte[] data = new byte[18];
        data[0] = 0x00; // alarmCode high byte
        data[1] = 0x01; // alarmCode low byte  → alarm code 1
        data[2] = 0x02; // level = 2  → "Error"
        data[3] = 0x00; // infoLength = 0
        // BCD timestamp (7 bytes) at offset 4:  2024-06-14 10:30:00
        data[4] = 0x20; // year high (bcd: 20)
        data[5] = 0x24; // year low  (bcd: 24) → 2024
        data[6] = 0x06; // month (bcd: 6)
        data[7] = 0x14; // day   (bcd: 14)
        data[8] = 0x10; // hour  (bcd: 10)
        data[9] = 0x30; // minute (bcd: 30)
        data[10] = 0x00; // second (bcd: 0)

        String result = FitDataUtils.parseAlarmData(data);
        assertThat(result).isEqualTo("报警码:0x0001, 级别:Error, 信息:, 时间:2024-06-14 10:30:00");
    }

    // ---- getCurrentTimestampBytes ----

    @Test
    void getCurrentTimestampBytes_returns4Bytes() {
        assertThat(FitDataUtils.getCurrentTimestampBytes()).hasSize(4);
    }

    // ---- getTimestampBytes + bytesToTimestamp round-trip ----

    @Test
    void timestampRoundTrip() {
        long original = 1234567890L;
        byte[] encoded = FitDataUtils.getTimestampBytes(original);
        long decoded = FitDataUtils.bytesToTimestamp(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    // ---- bytesToTimestamp known value ----

    @Test
    void bytesToTimestamp_knownLittleEndianBytes() {
        byte[] bytes = {(byte) 0xD2, 0x02, (byte) 0x96, 0x49};
        assertThat(FitDataUtils.bytesToTimestamp(bytes)).isEqualTo(1234567890L);
    }

    // ---- getDateStr ----

    @Test
    void getDateStr_knownTimestamp() {
        byte[] bytes = {(byte) 0xD2, 0x02, (byte) 0x96, 0x49};
        String dateStr = FitDataUtils.getDateStr(bytes);
        assertThat(dateStr).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }
}
