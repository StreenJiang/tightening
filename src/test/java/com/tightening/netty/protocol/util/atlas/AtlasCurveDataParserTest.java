package com.tightening.netty.protocol.util.atlas;

import com.tightening.dto.CurveDataDTO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AtlasCurveDataParserTest {

    private static void writeAt(byte[] header, int arrayOffset, String value) {
        if (value == null) return;
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, arrayOffset, bytes.length);
    }

    @Test
    void parseTorqueTrace_shouldExtractAllFields() {
        byte[] header = new byte[200];
        Arrays.fill(header, (byte) ' ');

        // Fixed fields at protocol bytes 21, 31, 50 → array offsets 0, 10, 29
        writeAt(header, 0, "0000000123");
        writeAt(header, 10, "2024-06-25:10:30:00");
        writeAt(header, 29, "001");

        // PID data starts at array offset 33 (protocol byte 54 - 21)
        // PID field layout: PID(5) + skip(5) + Length(3) + StepNo(4) + Value(Length)
        // Length read at offset+10, Value starts at offset+17
        int offset = 33;
        writeAt(header, offset, "02214");
        writeAt(header, offset + 10, "009");
        writeAt(header, offset + 17, "000002.50");
        offset += 17 + 9;

        // Trace Type (2) + Transducer Type (2) + Unit (3)
        writeAt(header, offset, "02");
        offset += 7;

        // numParamFields = 000
        writeAt(header, offset, "000");
        offset += 3;

        // numResFields = 000
        writeAt(header, offset, "000");
        offset += 3;

        // numSamples = 1
        writeAt(header, offset, "00001");
        offset += 5;

        // NUL
        header[offset] = 0;

        // 0x03E8 big-endian = 1000 → physical = 1000 * 2.5 = 2500.00
        byte[] samples = new byte[]{(byte) 0x03, (byte) 0xE8};

        CurveDataDTO dto = AtlasCurveDataParser.parse(header, samples, 1);

        assertThat(dto.getTighteningId()).isEqualTo(123);
        assertThat(dto.getTimestamp()).isEqualTo("2024-06-25:10:30:00");
        assertThat(dto.getDataType()).isEqualTo(2);
        assertThat(dto.getDataSamples()).isEqualTo("2500.00");
    }

    @Test
    void parseNegativeSamples_shouldHandleTwosComplement() {
        byte[] header = new byte[200];
        Arrays.fill(header, (byte) ' ');

        writeAt(header, 0, "0000000999");
        writeAt(header, 10, "2024-06-25:10:30:00");
        writeAt(header, 29, "001");

        int offset = 33;
        writeAt(header, offset, "02214");
        writeAt(header, offset + 10, "003");
        writeAt(header, offset + 17, "1.0");
        offset += 17 + 3;

        writeAt(header, offset, "01"); // traceType=1 (angle)
        offset += 7;
        writeAt(header, offset, "000");
        offset += 3; // numParamFields
        writeAt(header, offset, "000");
        offset += 3; // numResFields
        writeAt(header, offset, "00002");
        offset += 5; // numSamples=2
        header[offset] = 0;

        byte[] samples = new byte[]{
                (byte) 0xFF, (byte) 0xFE,  // -2
                (byte) 0x00, (byte) 0x05   // 5
        };

        CurveDataDTO dto = AtlasCurveDataParser.parse(header, samples, 1);

        assertThat(dto.getDataType()).isEqualTo(1);
        assertThat(dto.getDataSamples()).isEqualTo("-2,5");
    }

    @Test
    void parseWith02213Coefficient_shouldUseDivision() {
        byte[] header = new byte[200];
        Arrays.fill(header, (byte) ' ');

        writeAt(header, 0, "0000000001");
        writeAt(header, 10, "2024-06-25:10:30:00");
        writeAt(header, 29, "001");

        int offset = 33;
        writeAt(header, offset, "02213");
        writeAt(header, offset + 10, "003");
        writeAt(header, offset + 17, "2.0");
        offset += 17 + 3;

        writeAt(header, offset, "02");
        offset += 7;
        writeAt(header, offset, "000");
        offset += 3;
        writeAt(header, offset, "000");
        offset += 3;
        writeAt(header, offset, "00001");
        offset += 5;
        header[offset] = 0;

        byte[] samples = new byte[]{(byte) 0x00, (byte) 0x64}; // 100

        CurveDataDTO dto = AtlasCurveDataParser.parse(header, samples, 1);
        assertThat(dto.getDataSamples()).isEqualTo("50.00"); // 100 / (1/2.0) = wait... coefficient = 1.0/2.0 = 0.5, 100*0.5=50
    }

    @Test
    void parseEmptySamples_shouldReturnNullDataSamples() {
        byte[] header = new byte[200];
        Arrays.fill(header, (byte) ' ');

        writeAt(header, 0, "0000000000");
        writeAt(header, 10, "2024-06-25:10:30:00");
        writeAt(header, 29, "000");

        int offset = 33;
        writeAt(header, offset, "00");
        offset += 7;
        writeAt(header, offset, "000");
        offset += 3;
        writeAt(header, offset, "000");
        offset += 3;
        writeAt(header, offset, "00000");
        offset += 5;
        header[offset] = 0;

        CurveDataDTO dto = AtlasCurveDataParser.parse(header, new byte[0], 1);
        assertThat(dto.getDataSamples()).isNull();
    }
}
