package com.tightening.netty.protocol.util.fit;

import com.tightening.dto.TighteningDataDTO;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FitTighteningDataParserTest {

    @Test
    void parse_shouldExtractKeyFields() {
        // 按协议格式构造数据：tighteningId(4B) + status(1B) + programNumber(1B)
        // + barcodeLength(1B) + barcode(NB) + torque(4B) + angle(4B) + timestamp(7B)
        String barcode = "VIN123";
        byte[] barcodeBytes = barcode.getBytes(java.nio.charset.Charset.forName("GBK"));
        int dataLength = 4 + 1 + 1 + 1 + barcodeBytes.length + 4 + 4 + 7;
        ByteBuffer buf = ByteBuffer.allocate(dataLength).order(ByteOrder.LITTLE_ENDIAN);

        // tighteningId = 42
        buf.putInt(42);
        // status = 1 (OK)
        buf.put((byte) 1);
        // programNumber = 3
        buf.put((byte) 3);
        // barcodeLength
        buf.put((byte) barcodeBytes.length);
        // barcode
        buf.put(barcodeBytes);
        // torque = 12.5f
        buf.putFloat(12.5f);
        // angle = 180.0f
        buf.putFloat(180.0f);
        // timestamp BCD: 2025-06-15 10:30:00
        buf.put((byte) 0x20);
        buf.put((byte) 0x25);
        buf.put((byte) 0x06);
        buf.put((byte) 0x15);
        buf.put((byte) 0x10);
        buf.put((byte) 0x30);
        buf.put((byte) 0x00);

        TighteningDataDTO dto = FitTighteningDataParser.parse(buf.array());

        assertThat(dto.getTighteningStatus()).isEqualTo(1);
        assertThat(dto.getParameterSet()).isEqualTo(3);
        assertThat(dto.getTorque()).isCloseTo(12.5, within(0.01));
        assertThat(dto.getAngle()).isEqualTo(180);
        assertThat(dto.getTimestamp()).isEqualTo("2025-06-15 10:30:00");
    }
}
