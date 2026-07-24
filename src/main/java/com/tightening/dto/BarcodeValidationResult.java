package com.tightening.dto;

import org.springframework.lang.Nullable;

public record BarcodeValidationResult(
    String result,
    @Nullable String reason,
    @Nullable Long suggestedTaskId
) {
    public static BarcodeValidationResult matched() {
        return new BarcodeValidationResult("MATCHED", null, null);
    }

    public static BarcodeValidationResult wrongTask(Long id) {
        return new BarcodeValidationResult("WRONG_TASK", null, id);
    }

    public static BarcodeValidationResult notMatched() {
        return new BarcodeValidationResult("NOT_MATCHED", "barcode.product_not_matched", null);
    }

    public static BarcodeValidationResult pass() {
        return new BarcodeValidationResult("PASS", null, null);
    }

    public static BarcodeValidationResult fail(String errorCode) {
        return new BarcodeValidationResult("FAIL", errorCode, null);
    }
}
