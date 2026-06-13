package com.tightening.netty.protocol.util.atlas;

import com.tightening.constant.AtlasAngleStatus;
import com.tightening.constant.AtlasTorqueStatus;
import com.tightening.constant.atlas.AtlasExtraDataKeys;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public final class AtlasTighteningDataParser {

    private AtlasTighteningDataParser() {}

    public static TighteningDataDTO parse(byte[] data, int revision) {
        TighteningDataDTO result = switch (revision) {
            case 1 -> parseRev1(data);
            case 2 -> parseRev2(data);
            case 3 -> parseRev3(data);
            case 4 -> parseRev4(data);
            case 5 -> parseRev5(data);
            case 6 -> parseRev6(data);
            case 7 -> parseRev7(data);
            case 998 -> parseRev998(data);
            case 999 -> parseRev999(data);
            default -> throw new IllegalArgumentException("Unsupported revision: " + revision);
        };
        result.setRevision(revision);
        return result;
    }

    // ==================== Rev 1 ====================

    private static TighteningDataDTO parseRev1(byte[] data) {
        var d = new TighteningDataDTO();
        d.setCellId(parseInt(data, 23, 4));
        d.setChannelId(parseInt(data, 29, 2));
        d.setControllerName(parseString(data, 33, 25));
        d.setVin(parseString(data, 60, 25));
        d.setJobId(parseInt(data, 87, 2));
        d.setParameterSet(parseInt(data, 91, 3));
        d.setBatchSize(parseInt(data, 96, 4));
        d.setBatchCounter(parseInt(data, 102, 4));
        d.setTighteningStatus(parseInt(data, 108, 1));
        d.setTorqueStatus(parseTorqueStatus(data, 111));
        d.setAngleStatus(parseAngleStatus(data, 114));
        d.setTorqueMinLimit(parseTorque(data, 117));
        d.setTorqueMaxLimit(parseTorque(data, 125));
        d.setTorqueFinalTarget(parseTorque(data, 133));
        d.setTorque(parseTorque(data, 141));
        d.setAngleMinLimit(parseInt(data, 149, 5));
        d.setAngleMaxLimit(parseInt(data, 156, 5));
        d.setAngleFinalTarget(parseInt(data, 163, 5));
        d.setAngle(parseInt(data, 170, 5));
        d.setTimestamp(parseString(data, 177, 19));
        d.setBatchStatus(parseInt(data, 219, 1));
        d.setTighteningId(parseLong(data, 222, 10));
        return d;
    }

    // ==================== Rev 2+ shared helpers ====================

    private static void fillRev2Structured(TighteningDataDTO d, byte[] data) {
        d.setCellId(parseInt(data, 23, 4));
        d.setChannelId(parseInt(data, 29, 2));
        d.setControllerName(parseString(data, 33, 25));
        d.setVin(parseString(data, 60, 25));
        d.setJobId(parseInt(data, 87, 2));
        d.setParameterSet(parseInt(data, 91, 3));
        d.setBatchSize(parseInt(data, 109, 4));
        d.setBatchCounter(parseInt(data, 115, 4));
        d.setTighteningStatus(parseInt(data, 121, 1));
        d.setBatchStatus(parseInt(data, 124, 1));
        d.setTorqueStatus(parseTorqueStatus(data, 127));
        d.setAngleStatus(parseAngleStatus(data, 130));
        d.setRundownAngleStatus(parseInt(data, 133, 1));
        d.setTorqueMinLimit(parseTorque(data, 160));
        d.setTorqueMaxLimit(parseTorque(data, 168));
        d.setTorqueFinalTarget(parseTorque(data, 176));
        d.setTorque(parseTorque(data, 184));
        d.setAngleMinLimit(parseInt(data, 192, 5));
        d.setAngleMaxLimit(parseInt(data, 199, 5));
        d.setAngleFinalTarget(parseInt(data, 206, 5));
        d.setAngle(parseInt(data, 213, 5));
        d.setRundownAngleMinLimit(parseInt(data, 220, 5));
        d.setRundownAngleMaxLimit(parseInt(data, 227, 5));
        d.setRundownAngle(parseInt(data, 234, 5));
        d.setTimestamp(parseString(data, 346, 19));
        d.setTighteningId(parseLong(data, 304, 10));
    }

    private static Map<String, Object> buildRev2Extra(byte[] data) {
        Map<String, Object> extra = new HashMap<>();
        extra.put(AtlasExtraDataKeys.STRATEGY, parseInt(data, 96, 2));
        extra.put(AtlasExtraDataKeys.STRATEGY_OPTIONS, parseString(data, 102, 5));
        extra.put(AtlasExtraDataKeys.CURRENT_MONITORING_STATUS, parseInt(data, 136, 1));
        extra.put(AtlasExtraDataKeys.SELF_TAP_STATUS, parseInt(data, 139, 1));
        extra.put(AtlasExtraDataKeys.PV_MONITOR_STATUS, parseInt(data, 142, 1));
        extra.put(AtlasExtraDataKeys.PV_COMPENSATE_STATUS, parseInt(data, 145, 1));
        extra.put(AtlasExtraDataKeys.TIGHTENING_ERROR_STATUS, parseString(data, 148, 10));
        extra.put(AtlasExtraDataKeys.CURRENT_MONITORING_MIN, parseInt(data, 241, 3));
        extra.put(AtlasExtraDataKeys.CURRENT_MONITORING_MAX, parseInt(data, 246, 3));
        extra.put(AtlasExtraDataKeys.CURRENT_MONITORING_VALUE, parseInt(data, 251, 3));
        extra.put(AtlasExtraDataKeys.SELF_TAP_MIN, parseTorque(data, 256));
        extra.put(AtlasExtraDataKeys.SELF_TAP_MAX, parseTorque(data, 264));
        extra.put(AtlasExtraDataKeys.SELF_TAP_TORQUE, parseTorque(data, 272));
        extra.put(AtlasExtraDataKeys.PV_MONITOR_MIN, parseTorque(data, 280));
        extra.put(AtlasExtraDataKeys.PV_MONITOR_MAX, parseTorque(data, 288));
        extra.put(AtlasExtraDataKeys.PREVAIL_TORQUE, parseTorque(data, 296));
        extra.put(AtlasExtraDataKeys.JOB_SEQUENCE_NUMBER, parseInt(data, 316, 5));
        extra.put(AtlasExtraDataKeys.SYNC_TIGHTENING_ID, parseInt(data, 323, 5));
        extra.put(AtlasExtraDataKeys.TOOL_SERIAL_NUMBER, parseString(data, 330, 14));
        return extra;
    }

    // ==================== Rev 2-7 ====================

    private static TighteningDataDTO parseRev2(byte[] data) {
        var d = new TighteningDataDTO();
        fillRev2Structured(d, data);
        d.setExtraData(toJson(buildRev2Extra(data)));
        return d;
    }

    private static TighteningDataDTO parseRev3(byte[] data) {
        var d = new TighteningDataDTO();
        fillRev2Structured(d, data);
        Map<String, Object> extra = buildRev2Extra(data);
        d.setParameterSetName(parseString(data, 388, 25));
        d.setTorqueValuesUnit(parseInt(data, 415, 1));
        d.setResultType(parseInt(data, 418, 2));
        d.setExtraData(toJson(extra));
        return d;
    }

    private static TighteningDataDTO parseRev4(byte[] data) {
        var d = parseRev3(data);
        Map<String, Object> extra = fromJson(d.getExtraData());
        extra.put(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_2, parseString(data, 422, 25));
        extra.put(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_3, parseString(data, 449, 25));
        extra.put(AtlasExtraDataKeys.IDENTIFIER_RESULT_PART_4, parseString(data, 476, 25));
        d.setExtraData(toJson(extra));
        return d;
    }

    private static TighteningDataDTO parseRev5(byte[] data) {
        var d = parseRev4(data);
        Map<String, Object> extra = fromJson(d.getExtraData());
        extra.put(AtlasExtraDataKeys.CUSTOMER_TIGHTENING_ERROR_CODE, parseString(data, 503, 4));
        d.setExtraData(toJson(extra));
        return d;
    }

    private static TighteningDataDTO parseRev6(byte[] data) {
        var d = parseRev5(data);
        Map<String, Object> extra = fromJson(d.getExtraData());
        extra.put(AtlasExtraDataKeys.PV_COMPENSATE_VALUE, parseTorque(data, 509));
        extra.put(AtlasExtraDataKeys.TIGHTENING_ERROR_STATUS_2, parseString(data, 517, 10));
        d.setExtraData(toJson(extra));
        return d;
    }

    private static TighteningDataDTO parseRev7(byte[] data) {
        var d = parseRev6(data);
        Map<String, Object> extra = fromJson(d.getExtraData());
        extra.put(AtlasExtraDataKeys.COMPENSATED_ANGLE, parseInt(data, 529, 7));
        extra.put(AtlasExtraDataKeys.FINAL_ANGLE_DECIMAL, parseInt(data, 538, 7));
        d.setExtraData(toJson(extra));
        return d;
    }

    // ==================== Rev 998 / 999 ====================

    private static TighteningDataDTO parseRev998(byte[] data) {
        var d = parseRev6(data);
        Map<String, Object> extra = fromJson(d.getExtraData());
        int totalStages = parseInt(data, 529, 2);
        int completedStages = parseInt(data, 533, 2);
        extra.put(AtlasExtraDataKeys.TOTAL_STAGES, totalStages);
        extra.put(AtlasExtraDataKeys.COMPLETED_STAGES, completedStages);

        List<Map<String, Object>> stages = new ArrayList<>();
        int offset = 537;
        for (int i = 0; i < completedStages; i++) {
            Map<String, Object> stage = new HashMap<>();
            stage.put("torque", parseTorque(data, offset));
            stage.put("angle", parseInt(data, offset + 6, 5));
            stages.add(stage);
            offset += 11;
        }
        extra.put(AtlasExtraDataKeys.STAGE_RESULTS, stages);
        d.setExtraData(toJson(extra));
        return d;
    }

    private static TighteningDataDTO parseRev999(byte[] data) {
        var d = new TighteningDataDTO();
        d.setVin(parseString(data, 21, 25));
        d.setJobId(parseInt(data, 46, 2));
        d.setParameterSet(parseInt(data, 48, 3));
        d.setBatchSize(parseInt(data, 51, 4));
        d.setBatchCounter(parseInt(data, 55, 4));
        d.setBatchStatus(parseInt(data, 59, 1));
        d.setTighteningStatus(parseInt(data, 60, 1));
        d.setTorqueStatus(parseTorqueStatus(data, 61));
        d.setAngleStatus(parseAngleStatus(data, 62));
        d.setTorque(parseTorque(data, 63));
        d.setAngle(parseInt(data, 69, 5));
        d.setTimestamp(parseString(data, 74, 19));
        d.setTighteningId(parseLong(data, 112, 10));
        return d;
    }

    // ==================== Utility methods ====================

    private static int parseInt(byte[] data, int protocolByte, int length) {
        int offset = protocolByte - 21;
        if (offset + length > data.length) {
            log.warn("Data too short at protocol byte {}: need {}, have {}",
                    protocolByte, length, data.length - offset);
            return 0;
        }
        String raw = new String(data, offset, length, StandardCharsets.US_ASCII).trim();
        if (raw.isEmpty()) return 0;
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException e) {
            log.warn("Failed to parse int at protocol byte {}: '{}'", protocolByte, raw);
            return 0;
        }
    }

    private static long parseLong(byte[] data, int protocolByte, int length) {
        int offset = protocolByte - 21;
        if (offset + length > data.length) {
            log.warn("Data too short at protocol byte {}: need {}, have {}",
                    protocolByte, length, data.length - offset);
            return 0L;
        }
        String raw = new String(data, offset, length, StandardCharsets.US_ASCII).trim();
        if (raw.isEmpty()) return 0L;
        try { return Long.parseLong(raw); }
        catch (NumberFormatException e) {
            log.warn("Failed to parse long at protocol byte {}: '{}'", protocolByte, raw);
            return 0L;
        }
    }

    private static String parseString(byte[] data, int protocolByte, int length) {
        int offset = protocolByte - 21;
        if (offset + length > data.length) {
            log.warn("Data too short at protocol byte {}: need {}, have {}",
                    protocolByte, length, data.length - offset);
            return "";
        }
        return new String(data, offset, length, StandardCharsets.US_ASCII).trim();
    }

    private static double parseTorque(byte[] data, int protocolByte) {
        return parseInt(data, protocolByte, 6) / 100.0;
    }

    private static int parseTorqueStatus(byte[] data, int protocolByte) {
        return AtlasTorqueStatus.fromCode(parseInt(data, protocolByte, 1))
                .orElse(AtlasTorqueStatus.LOW).getCode();
    }

    private static int parseAngleStatus(byte[] data, int protocolByte) {
        return AtlasAngleStatus.fromCode(parseInt(data, protocolByte, 1))
                .orElse(AtlasAngleStatus.LOW).getCode();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try { return JsonUtils.OBJECT_MAPPER.readValue(json, Map.class); }
        catch (Exception e) { return new HashMap<>(); }
    }

    private static String toJson(Map<String, Object> map) {
        if (map.isEmpty()) return null;
        try { return JsonUtils.OBJECT_MAPPER.writeValueAsString(map); }
        catch (Exception e) { return null; }
    }
}
