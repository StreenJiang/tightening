package com.tightening.netty.protocol.util.atlas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tightening.constant.atlas.AtlasExtraDataKeys;
import com.tightening.dto.TighteningDataDTO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AtlasTighteningDataParserTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ==================== Helper ====================

    private static void writeAt(byte[] data, int protocolByte, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, data, protocolByte - 21, bytes.length);
    }

    // ==================== Rev 1 ====================

    @Test
    void parseRev1_shouldExtractAllFields() {
        byte[] data = new byte[250];
        Arrays.fill(data, (byte) ' ');

        writeAt(data, 23, "1");
        writeAt(data, 29, "1");
        writeAt(data, 33, "CONTROLLER-01");
        writeAt(data, 60, "VIN1234567890");
        writeAt(data, 87, "1");
        writeAt(data, 91, "5");
        writeAt(data, 96, "10");
        writeAt(data, 102, "3");
        writeAt(data, 108, "1");
        writeAt(data, 111, "0");
        writeAt(data, 114, "1");
        writeAt(data, 117, "001000");
        writeAt(data, 125, "002000");
        writeAt(data, 133, "001500");
        writeAt(data, 141, "001234");
        writeAt(data, 149, "00010");
        writeAt(data, 156, "00100");
        writeAt(data, 163, "00050");
        writeAt(data, 170, "00045");
        writeAt(data, 177, "2024-01-15:10:30:00");
        writeAt(data, 219, "1");
        writeAt(data, 222, "1234567890");

        TighteningDataDTO d = AtlasTighteningDataParser.parse(data, 1);

        assertThat(d.getRevision()).isEqualTo(1);
        assertThat(d.getCellId()).isEqualTo(1);
        assertThat(d.getChannelId()).isEqualTo(1);
        assertThat(d.getControllerName()).isEqualTo("CONTROLLER-01");
        assertThat(d.getVin()).isEqualTo("VIN1234567890");
        assertThat(d.getJobId()).isEqualTo(1);
        assertThat(d.getParameterSet()).isEqualTo(5);
        assertThat(d.getBatchSize()).isEqualTo(10);
        assertThat(d.getBatchCounter()).isEqualTo(3);
        assertThat(d.getTighteningStatus()).isEqualTo(1);
        assertThat(d.getTorqueStatus()).isZero();
        assertThat(d.getAngleStatus()).isEqualTo(1);
        assertThat(d.getTorqueMinLimit()).isCloseTo(10.0, within(0.01));
        assertThat(d.getTorqueMaxLimit()).isCloseTo(20.0, within(0.01));
        assertThat(d.getTorqueFinalTarget()).isCloseTo(15.0, within(0.01));
        assertThat(d.getTorque()).isCloseTo(12.34, within(0.01));
        assertThat(d.getAngleMinLimit()).isCloseTo(10.0, within(0.01));
        assertThat(d.getAngleMaxLimit()).isCloseTo(100.0, within(0.01));
        assertThat(d.getAngleFinalTarget()).isCloseTo(50.0, within(0.01));
        assertThat(d.getAngle()).isCloseTo(45.0, within(0.01));
        assertThat(d.getTimestamp()).isEqualTo("2024-01-15:10:30:00");
        assertThat(d.getBatchStatus()).isEqualTo(1);
        assertThat(d.getTighteningId()).isEqualTo(1234567890L);
    }

    // ==================== Unsupported revision ====================

    @Test
    void parse_unsupportedRevision_shouldThrow() {
        byte[] data = new byte[10];
        assertThatThrownBy(() -> AtlasTighteningDataParser.parse(data, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0");
        assertThatThrownBy(() -> AtlasTighteningDataParser.parse(data, 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8");
        assertThatThrownBy(() -> AtlasTighteningDataParser.parse(data, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Data too short ====================

    @Test
    void parse_dataTooShort_shouldNotThrow() {
        byte[] data = new byte[10];
        TighteningDataDTO d = AtlasTighteningDataParser.parse(data, 1);
        assertThat(d).isNotNull();
        assertThat(d.getCellId()).isZero();
        assertThat(d.getRevision()).isEqualTo(1);
    }

    // ==================== Rev 2 ====================

    @Test
    void parseRev2_shouldIncludeRev2ExtraFields() throws Exception {
        byte[] data = new byte[400];
        Arrays.fill(data, (byte) ' ');

        // fillRev2Structured fields
        writeAt(data, 23, "2");
        writeAt(data, 29, "3");
        writeAt(data, 33, "CTRL-ATLAS-02");
        writeAt(data, 60, "VIN-REV2-001");
        writeAt(data, 87, "2");
        writeAt(data, 91, "8");
        writeAt(data, 109, "20");
        writeAt(data, 115, "7");
        writeAt(data, 121, "0");
        writeAt(data, 124, "1");
        writeAt(data, 127, "0");
        writeAt(data, 130, "1");
        writeAt(data, 133, "0");
        writeAt(data, 160, "001000");
        writeAt(data, 168, "002000");
        writeAt(data, 176, "001500");
        writeAt(data, 184, "001234");
        writeAt(data, 192, "00010");
        writeAt(data, 199, "00100");
        writeAt(data, 206, "00050");
        writeAt(data, 213, "00045");
        writeAt(data, 220, "00005");
        writeAt(data, 227, "00050");
        writeAt(data, 234, "00025");
        writeAt(data, 346, "2024-06-09:12:00:00");
        writeAt(data, 304, "9876543210");

        // buildRev2Extra fields
        writeAt(data, 96, "01");
        writeAt(data, 102, "OPTA ");
        writeAt(data, 136, "0");
        writeAt(data, 139, "1");
        writeAt(data, 142, "0");
        writeAt(data, 145, "1");
        writeAt(data, 148, "OK");
        writeAt(data, 241, "050");
        writeAt(data, 246, "100");
        writeAt(data, 251, "075");
        writeAt(data, 256, "000500");
        writeAt(data, 264, "001000");
        writeAt(data, 272, "000750");
        writeAt(data, 280, "000200");
        writeAt(data, 288, "000800");
        writeAt(data, 296, "000100");
        writeAt(data, 316, "00003");
        writeAt(data, 323, "00001");
        writeAt(data, 330, "SN-ATLAS-001");

        TighteningDataDTO d = AtlasTighteningDataParser.parse(data, 2);

        // Verify structured fields
        assertThat(d.getCellId()).isEqualTo(2);
        assertThat(d.getChannelId()).isEqualTo(3);
        assertThat(d.getControllerName()).isEqualTo("CTRL-ATLAS-02");
        assertThat(d.getVin()).isEqualTo("VIN-REV2-001");
        assertThat(d.getJobId()).isEqualTo(2);
        assertThat(d.getParameterSet()).isEqualTo(8);
        assertThat(d.getBatchSize()).isEqualTo(20);
        assertThat(d.getBatchCounter()).isEqualTo(7);
        assertThat(d.getTighteningStatus()).isZero();
        assertThat(d.getBatchStatus()).isEqualTo(1);
        assertThat(d.getTorqueStatus()).isZero();
        assertThat(d.getAngleStatus()).isEqualTo(1);
        assertThat(d.getRundownAngleStatus()).isZero();
        assertThat(d.getTorqueMinLimit()).isCloseTo(10.0, within(0.01));
        assertThat(d.getTorqueMaxLimit()).isCloseTo(20.0, within(0.01));
        assertThat(d.getTorqueFinalTarget()).isCloseTo(15.0, within(0.01));
        assertThat(d.getTorque()).isCloseTo(12.34, within(0.01));
        assertThat(d.getAngleMinLimit()).isCloseTo(10.0, within(0.01));
        assertThat(d.getAngleMaxLimit()).isCloseTo(100.0, within(0.01));
        assertThat(d.getAngleFinalTarget()).isCloseTo(50.0, within(0.01));
        assertThat(d.getAngle()).isCloseTo(45.0, within(0.01));
        assertThat(d.getRundownAngleMinLimit()).isCloseTo(5.0, within(0.01));
        assertThat(d.getRundownAngleMaxLimit()).isCloseTo(50.0, within(0.01));
        assertThat(d.getRundownAngle()).isCloseTo(25.0, within(0.01));
        assertThat(d.getTimestamp()).isEqualTo("2024-06-09:12:00:00");
        assertThat(d.getTighteningId()).isEqualTo(9876543210L);

        // Verify extra data
        Map<String, Object> extra = OBJECT_MAPPER.readValue(d.getExtraData(),
                new TypeReference<Map<String, Object>>() {});
        assertThat(extra).containsEntry(AtlasExtraDataKeys.STRATEGY, 1);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.STRATEGY_OPTIONS, "OPTA");
        assertThat(extra).containsEntry(AtlasExtraDataKeys.SELF_TAP_STATUS, 1);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.TIGHTENING_ERROR_STATUS, "OK");
        assertThat(extra).containsEntry(AtlasExtraDataKeys.CURRENT_MONITORING_MIN, 50);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.CURRENT_MONITORING_MAX, 100);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.CURRENT_MONITORING_VALUE, 75);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.SELF_TAP_MIN, 5.0);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.SELF_TAP_TORQUE, 7.5);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.PV_MONITOR_MIN, 2.0);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.PREVAIL_TORQUE, 1.0);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.JOB_SEQUENCE_NUMBER, 3);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.SYNC_TIGHTENING_ID, 1);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.TOOL_SERIAL_NUMBER, "SN-ATLAS-001");
    }

    // ==================== Rev 3 ====================

    @Test
    void parseRev3_shouldIncludeParameterSetName() throws Exception {
        byte[] data = new byte[450];
        Arrays.fill(data, (byte) ' ');

        // Fill rev2 structured + extra fields (same as rev2 test)
        writeAt(data, 23, "2");
        writeAt(data, 29, "3");
        writeAt(data, 33, "CTRL-ATLAS-03");
        writeAt(data, 60, "VIN-REV3-001");
        writeAt(data, 87, "3");
        writeAt(data, 91, "6");
        writeAt(data, 109, "15");
        writeAt(data, 115, "4");
        writeAt(data, 121, "1");
        writeAt(data, 124, "0");
        writeAt(data, 127, "1");
        writeAt(data, 130, "0");
        writeAt(data, 133, "1");
        writeAt(data, 160, "000500");
        writeAt(data, 168, "001500");
        writeAt(data, 176, "001000");
        writeAt(data, 184, "000800");
        writeAt(data, 192, "00020");
        writeAt(data, 199, "00080");
        writeAt(data, 206, "00060");
        writeAt(data, 213, "00035");
        writeAt(data, 220, "00010");
        writeAt(data, 227, "00040");
        writeAt(data, 234, "00020");
        writeAt(data, 346, "2024-07-01:09:15:30");
        writeAt(data, 304, "5555555555");
        writeAt(data, 96, "02");
        writeAt(data, 102, "OPTB ");
        writeAt(data, 136, "1");
        writeAt(data, 139, "0");
        writeAt(data, 142, "1");
        writeAt(data, 145, "0");
        writeAt(data, 148, "NOK");
        writeAt(data, 241, "030");
        writeAt(data, 246, "080");
        writeAt(data, 251, "055");
        writeAt(data, 256, "000300");
        writeAt(data, 264, "000800");
        writeAt(data, 272, "000500");
        writeAt(data, 280, "000100");
        writeAt(data, 288, "000600");
        writeAt(data, 296, "000050");
        writeAt(data, 316, "00005");
        writeAt(data, 323, "00002");
        writeAt(data, 330, "SN-ATLAS-002");

        // Rev 3 specific fields
        writeAt(data, 388, "PARAM-SET-005");
        writeAt(data, 415, "1");
        writeAt(data, 418, "01");

        TighteningDataDTO d = AtlasTighteningDataParser.parse(data, 3);

        assertThat(d.getParameterSetName()).isEqualTo("PARAM-SET-005");
        assertThat(d.getTorqueValuesUnit()).isEqualTo(1);
        assertThat(d.getResultType()).isEqualTo(1);
        assertThat(d.getRevision()).isEqualTo(3);

        // Verify extra data still present
        Map<String, Object> extra = OBJECT_MAPPER.readValue(d.getExtraData(),
                new TypeReference<Map<String, Object>>() {});
        assertThat(extra).containsEntry(AtlasExtraDataKeys.STRATEGY, 2);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.TIGHTENING_ERROR_STATUS, "NOK");
    }

    // ==================== Rev 7 ====================

    @Test
    void parseRev7_shouldIncludeCompensatedAngle() throws Exception {
        byte[] data = new byte[550];
        Arrays.fill(data, (byte) ' ');

        // Fill rev2 structured + extra
        writeAt(data, 23, "7");
        writeAt(data, 29, "1");
        writeAt(data, 33, "CTRL-ATLAS-07");
        writeAt(data, 60, "VIN-REV7-001");
        writeAt(data, 87, "7");
        writeAt(data, 91, "9");
        writeAt(data, 109, "30");
        writeAt(data, 115, "12");
        writeAt(data, 121, "1");
        writeAt(data, 124, "1");
        writeAt(data, 127, "1");
        writeAt(data, 130, "0");
        writeAt(data, 133, "1");
        writeAt(data, 160, "001000");
        writeAt(data, 168, "003000");
        writeAt(data, 176, "002000");
        writeAt(data, 184, "001800");
        writeAt(data, 192, "00015");
        writeAt(data, 199, "00120");
        writeAt(data, 206, "00090");
        writeAt(data, 213, "00075");
        writeAt(data, 220, "00005");
        writeAt(data, 227, "00060");
        writeAt(data, 234, "00030");
        writeAt(data, 346, "2025-01-10:14:22:00");
        writeAt(data, 304, "1111111111");
        writeAt(data, 96, "03");
        writeAt(data, 102, "OPTC ");
        writeAt(data, 136, "0");
        writeAt(data, 139, "1");
        writeAt(data, 142, "0");
        writeAt(data, 145, "1");
        writeAt(data, 148, "OK");
        writeAt(data, 241, "040");
        writeAt(data, 246, "090");
        writeAt(data, 251, "060");
        writeAt(data, 256, "000400");
        writeAt(data, 264, "000900");
        writeAt(data, 272, "000600");
        writeAt(data, 280, "000150");
        writeAt(data, 288, "000700");
        writeAt(data, 296, "000080");
        writeAt(data, 316, "00007");
        writeAt(data, 323, "00003");
        writeAt(data, 330, "SN-ATLAS-003");

        // Rev 3 fields
        writeAt(data, 388, "PARAM-SET-007");
        writeAt(data, 415, "1");
        writeAt(data, 418, "01");

        // Rev 4 fields
        writeAt(data, 422, "ID-PART2");
        writeAt(data, 449, "ID-PART3");
        writeAt(data, 476, "ID-PART4");

        // Rev 5 fields
        writeAt(data, 503, "E001");

        // Rev 6 fields
        writeAt(data, 509, "000100");
        writeAt(data, 517, "STATUS-2");

        // Rev 7 fields
        writeAt(data, 529, "0000123");
        writeAt(data, 538, "0000456");

        TighteningDataDTO d = AtlasTighteningDataParser.parse(data, 7);

        assertThat(d.getRevision()).isEqualTo(7);

        Map<String, Object> extra = OBJECT_MAPPER.readValue(d.getExtraData(),
                new TypeReference<Map<String, Object>>() {});
        assertThat(extra).containsEntry(AtlasExtraDataKeys.COMPENSATED_ANGLE, 123);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.FINAL_ANGLE_DECIMAL, 456);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_2, "ID-PART2");
        assertThat(extra).containsEntry(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_3, "ID-PART3");
        assertThat(extra).containsEntry(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_4, "ID-PART4");
        assertThat(extra).containsEntry(AtlasExtraDataKeys.CUSTOMER_TIGHTENING_ERROR_CODE, "E001");
        assertThat(extra).containsEntry(AtlasExtraDataKeys.PV_COMPENSATE_VALUE, 1.0);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.TIGHTENING_ERROR_STATUS_2, "STATUS-2");
    }

    // ==================== Rev 998 ====================

    @Test
    void parseRev998_shouldParseStageResults() throws Exception {
        byte[] data = new byte[550];
        Arrays.fill(data, (byte) ' ');

        // Fill rev2 structured + extra (minimal)
        writeAt(data, 23, "1");
        writeAt(data, 29, "1");
        writeAt(data, 33, "CTRL-998");
        writeAt(data, 60, "VIN-998");
        writeAt(data, 87, "1");
        writeAt(data, 91, "1");
        writeAt(data, 109, "10");
        writeAt(data, 115, "1");
        writeAt(data, 121, "1");
        writeAt(data, 124, "1");
        writeAt(data, 127, "1");
        writeAt(data, 130, "1");
        writeAt(data, 133, "1");
        writeAt(data, 160, "001000");
        writeAt(data, 168, "002000");
        writeAt(data, 176, "001500");
        writeAt(data, 184, "001200");
        writeAt(data, 192, "00010");
        writeAt(data, 199, "00100");
        writeAt(data, 206, "00050");
        writeAt(data, 213, "00040");
        writeAt(data, 220, "00005");
        writeAt(data, 227, "00050");
        writeAt(data, 234, "00020");
        writeAt(data, 346, "2025-06-01:08:00:00");
        writeAt(data, 304, "9999999999");
        writeAt(data, 96, "01");
        writeAt(data, 102, "OPTA ");
        writeAt(data, 136, "0");
        writeAt(data, 139, "0");
        writeAt(data, 142, "0");
        writeAt(data, 145, "0");
        writeAt(data, 148, "OK");
        writeAt(data, 241, "050");
        writeAt(data, 246, "100");
        writeAt(data, 251, "075");
        writeAt(data, 256, "000500");
        writeAt(data, 264, "001000");
        writeAt(data, 272, "000750");
        writeAt(data, 280, "000200");
        writeAt(data, 288, "000800");
        writeAt(data, 296, "000100");
        writeAt(data, 316, "00001");
        writeAt(data, 323, "00001");
        writeAt(data, 330, "SN-998");

        // Rev 3 fields
        writeAt(data, 388, "PARAM-998");
        writeAt(data, 415, "1");
        writeAt(data, 418, "01");

        // Rev 4 fields
        writeAt(data, 422, "ID2");
        writeAt(data, 449, "ID3");
        writeAt(data, 476, "ID4");

        // Rev 5 fields
        writeAt(data, 503, "E998");

        // Rev 6 fields
        writeAt(data, 509, "000050");
        writeAt(data, 517, "ST2");

        // Rev 998 specific fields
        writeAt(data, 529, "03");    // totalStages = 3
        writeAt(data, 533, "02");    // completedStages = 2
        // Stage 0: torque at 537, angle at 543
        writeAt(data, 537, "001000"); // torque = 10.0
        writeAt(data, 543, "00045");  // angle = 45
        // Stage 1: torque at 548, angle at 554
        writeAt(data, 548, "001500"); // torque = 15.0
        writeAt(data, 554, "00090");  // angle = 90

        TighteningDataDTO d = AtlasTighteningDataParser.parse(data, 998);

        assertThat(d.getRevision()).isEqualTo(998);

        Map<String, Object> extra = OBJECT_MAPPER.readValue(d.getExtraData(),
                new TypeReference<Map<String, Object>>() {});
        assertThat(extra).containsEntry(AtlasExtraDataKeys.TOTAL_STAGES, 3);
        assertThat(extra).containsEntry(AtlasExtraDataKeys.COMPLETED_STAGES, 2);

        List<Map<String, Object>> stages = (List<Map<String, Object>>) extra.get(AtlasExtraDataKeys.STAGE_RESULTS);
        assertThat(stages).hasSize(2);

        assertThat(stages.get(0)).containsEntry("torque", 10.0);
        assertThat(stages.get(0)).containsEntry("angle", 45);
        assertThat(stages.get(1)).containsEntry("torque", 15.0);
        assertThat(stages.get(1)).containsEntry("angle", 90);
    }

    // ==================== Rev 999 ====================

    @Test
    void parseRev999_shouldParseLightFormat() {
        byte[] data = new byte[130];
        Arrays.fill(data, (byte) ' ');

        writeAt(data, 21, "VIN-REV999-001");
        writeAt(data, 46, "3");
        writeAt(data, 48, "12");
        writeAt(data, 51, "50");
        writeAt(data, 55, "25");
        writeAt(data, 59, "1");
        writeAt(data, 60, "0");
        writeAt(data, 61, "1");
        writeAt(data, 62, "0");
        writeAt(data, 63, "002500");
        writeAt(data, 69, "00180");
        writeAt(data, 74, "2025-12-01:08:15:30");
        writeAt(data, 112, "5555555555");

        TighteningDataDTO d = AtlasTighteningDataParser.parse(data, 999);

        assertThat(d.getRevision()).isEqualTo(999);
        assertThat(d.getVin()).isEqualTo("VIN-REV999-001");
        assertThat(d.getJobId()).isEqualTo(3);
        assertThat(d.getParameterSet()).isEqualTo(12);
        assertThat(d.getBatchSize()).isEqualTo(50);
        assertThat(d.getBatchCounter()).isEqualTo(25);
        assertThat(d.getBatchStatus()).isEqualTo(1);
        assertThat(d.getTighteningStatus()).isZero();
        assertThat(d.getTorqueStatus()).isEqualTo(1);
        assertThat(d.getAngleStatus()).isZero();
        assertThat(d.getTorque()).isCloseTo(25.0, within(0.01));
        assertThat(d.getAngle()).isCloseTo(180.0, within(0.01));
        assertThat(d.getTimestamp()).isEqualTo("2025-12-01:08:15:30");
        assertThat(d.getTighteningId()).isEqualTo(5555555555L);
    }
}
