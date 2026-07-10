package com.tightening.dto;

import org.springframework.lang.Nullable;

public record BarcodeValidationResult(
    String result,
    @Nullable String reason,
    @Nullable Long suggestedMissionId
) {
    public static BarcodeValidationResult matched() {
        return new BarcodeValidationResult("MATCHED", null, null);
    }
    public static BarcodeValidationResult wrongMission(Long id) {
        return new BarcodeValidationResult("WRONG_MISSION", null, id);
    }
    public static BarcodeValidationResult notMatched() {
        return new BarcodeValidationResult("NOT_MATCHED", "产品追溯码不匹配", null);
    }
    public static BarcodeValidationResult pass() {
        return new BarcodeValidationResult("PASS", null, null);
    }
    public static BarcodeValidationResult fail(String reason) {
        return new BarcodeValidationResult("FAIL", reason, null);
    }
}
