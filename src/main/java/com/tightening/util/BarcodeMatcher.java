package com.tightening.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tightening.entity.BarCodeMatchingRule;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public final class BarcodeMatcher {

    private static final ObjectMapper mapper = JsonUtils.OBJECT_MAPPER;

    private BarcodeMatcher() {}

    public record Segment(int s, int e, String v) {}

    public static boolean matches(BarCodeMatchingRule rule, String code) {
        if (code == null) return false;
        if (rule.getExpectedLength() != null && code.length() != rule.getExpectedLength()) return false;

        String segmentsJson = rule.getSegments();
        if (segmentsJson == null || segmentsJson.isEmpty()) return true;

        return matchesSegments(segmentsJson, code);
    }

    static boolean matchesSegments(String segmentsJson, String code) {
        try {
            List<Segment> segments = mapper.readValue(segmentsJson, new TypeReference<>() {});
            if (segments.isEmpty()) return true;
            for (Segment seg : segments) {
                int end = Math.min(seg.e, code.length());
                int start = Math.min(seg.s, end);
                if (!code.substring(start, end).equals(seg.v)) return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to parse barcode segments JSON: {}", segmentsJson, e);
            return false;
        }
    }
}
