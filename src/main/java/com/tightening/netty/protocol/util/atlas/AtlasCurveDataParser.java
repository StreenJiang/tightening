package com.tightening.netty.protocol.util.atlas;

import com.tightening.dto.CurveDataDTO;
import lombok.extern.slf4j.Slf4j;

import static com.tightening.netty.protocol.util.atlas.AtlasDataUtils.parseIntAtOffset;
import static com.tightening.netty.protocol.util.atlas.AtlasDataUtils.parseIntAtProtocolByte;
import static com.tightening.netty.protocol.util.atlas.AtlasDataUtils.parseStringAtOffset;
import static com.tightening.netty.protocol.util.atlas.AtlasDataUtils.parseStringAtProtocolByte;

@Slf4j
public final class AtlasCurveDataParser {

    private static final String PID_COEFFICIENT_MULTIPLY = "02214";
    private static final String PID_COEFFICIENT_DIVIDE = "02213";

    private static final int PID_HEADER_BYTES = 17;
    private static final int RES_FIELD_HEADER_BYTES = 18;
    private static final int TRACE_FIXED_BYTES = 7; // Trace Type(2) + Transducer Type(2) + Unit(3)

    private static final int HEADER_BASE_BYTE = 21;
    private static final int PID_FIELDS_START_OFFSET = 33; // protocol byte 54 - HEADER_BASE_BYTE

    private static final int INT16_MAX = 32767;
    private static final int INT16_RANGE = 65536;

    private AtlasCurveDataParser() {}

    public static CurveDataDTO parse(byte[] headerData, byte[] sampleData, int revision) {
        if (revision != 1) {
            log.warn("Unsupported MID 0900 revision: {}, only revision 1 is implemented", revision);
        }

        int tighteningId = parseIntAtProtocolByte(headerData, 21, 10);
        String timestamp = parseStringAtProtocolByte(headerData, 31, 19);
        int numPids = parseIntAtProtocolByte(headerData, 50, 3);

        int offset = PID_FIELDS_START_OFFSET;
        double coefficient = Double.NaN;

        for (int i = 0; i < numPids; i++) {
            if (offset + PID_HEADER_BYTES > headerData.length) break;
            String pid = parseStringAtOffset(headerData, offset, 5);
            int fieldLen = parseIntAtOffset(headerData, offset + 10, 3);
            int valueOffset = offset + PID_HEADER_BYTES;

            if (PID_COEFFICIENT_MULTIPLY.equals(pid)) {
                coefficient = Double.parseDouble(parseStringAtOffset(headerData, valueOffset, fieldLen));
            } else if (PID_COEFFICIENT_DIVIDE.equals(pid) && Double.isNaN(coefficient)) {
                coefficient = 1.0 / Double.parseDouble(parseStringAtOffset(headerData, valueOffset, fieldLen));
            }

            offset = valueOffset + fieldLen;
        }

        if (Double.isNaN(coefficient)) {
            log.warn("Coefficient PID ({}/{}) not found, defaulting to 1.0",
                     PID_COEFFICIENT_MULTIPLY, PID_COEFFICIENT_DIVIDE);
            coefficient = 1.0;
        }

        int traceType = parseIntAtOffset(headerData, offset, 2);
        offset += TRACE_FIXED_BYTES;

        int numParamFields = parseIntAtOffset(headerData, offset, 3);
        offset += 3;
        for (int i = 0; i < numParamFields; i++) {
            if (offset + PID_HEADER_BYTES > headerData.length) break;
            int fieldLen = parseIntAtOffset(headerData, offset + 10, 3);
            offset += PID_HEADER_BYTES + fieldLen;
        }

        int numResFields = parseIntAtOffset(headerData, offset, 3);
        offset += 3;
        for (int i = 0; i < numResFields; i++) {
            if (offset + RES_FIELD_HEADER_BYTES > headerData.length) break;
            int timeLen = parseIntAtOffset(headerData, offset + 13, 3);
            offset += RES_FIELD_HEADER_BYTES + timeLen;
        }

        int numSamples = parseIntAtOffset(headerData, offset, 5);

        CurveDataDTO dto = new CurveDataDTO();
        dto.setTighteningId(tighteningId);
        dto.setTimestamp(timestamp);
        dto.setDataType(traceType);

        if (numSamples > 0 && sampleData != null && sampleData.length >= numSamples * 2) {
            dto.setDataSamples(parseSamples(sampleData, numSamples, coefficient, traceType));
        }

        log.debug("Parsed curve data: tighteningId={}, traceType={}, numSamples={}, coefficient={}",
                  tighteningId, traceType, numSamples, coefficient);
        return dto;
    }

    private static String parseSamples(byte[] sampleData, int numSamples, double coefficient, int traceType) {
        boolean isAngle = (traceType != 2);
        double multiplier = isAngle ? 1.0 : 100.0;

        StringBuilder sb = new StringBuilder(numSamples * 12);

        int raw = ((sampleData[0] & 0xFF) << 8) | (sampleData[1] & 0xFF);
        if (raw > INT16_MAX) raw -= INT16_RANGE;
        double value = Math.round(raw * coefficient * multiplier) / multiplier;
        sb.append(isAngle ? (long) value : String.format("%.2f", value));

        for (int i = 2; i < numSamples * 2; i += 2) {
            raw = ((sampleData[i] & 0xFF) << 8) | (sampleData[i + 1] & 0xFF);
            if (raw > INT16_MAX) raw -= INT16_RANGE;
            value = Math.round(raw * coefficient * multiplier) / multiplier;
            sb.append(',');
            sb.append(isAngle ? (long) value : String.format("%.2f", value));
        }

        return sb.toString();
    }
}
