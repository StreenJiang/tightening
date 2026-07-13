package com.tightening.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Crc16Utils — Modbus CRC16 (polynomial 0xA001)")
class Crc16UtilsTest {

    @Test
    @DisplayName("known test vector: Modbus CRC of '123456789' → 0x4B37")
    void modbusKnownVector() {
        byte[] data = "123456789".getBytes(StandardCharsets.US_ASCII);
        assertThat(Crc16Utils.compute(data)).isEqualTo(0x4B37);
    }

    @Test
    @DisplayName("CRC of empty input → 0xFFFF")
    void emptyInput() {
        assertThat(Crc16Utils.compute(new byte[0])).isEqualTo(0xFFFF);
    }

    @Test
    @DisplayName("same input → same CRC (deterministic)")
    void deterministic() {
        byte[] data = {0x01, 0x00, 0x00, 0x02, 0x00};
        assertThat(Crc16Utils.compute(data)).isEqualTo(Crc16Utils.compute(data));
    }

    @Test
    @DisplayName("different input → different CRC")
    void differentInputDifferentCrc() {
        byte[] a = {0x01, 0x00, 0x00, 0x02, 0x00};
        byte[] b = {0x01, 0x00, 0x00, 0x00, 0x00};
        assertThat(Crc16Utils.compute(a)).isNotEqualTo(Crc16Utils.compute(b));
    }

    @Test
    @DisplayName("output fits in 16 bits")
    void fits16Bit() {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) data[i] = (byte) i;
        int crc = Crc16Utils.compute(data);
        assertThat(crc).isBetween(0, 0xFFFF);
    }
}
