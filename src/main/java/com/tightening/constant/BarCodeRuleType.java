package com.tightening.constant;

import lombok.Getter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum BarCodeRuleType {
    PRODUCT_TRACE(1),
    MATERIAL_BARCODE(2);

    private final int code;
    private static final Map<Integer, BarCodeRuleType> BY_CODE =
        Arrays.stream(values()).collect(Collectors.toMap(BarCodeRuleType::getCode, Function.identity()));

    BarCodeRuleType(int code) { this.code = code; }

    public static Optional<BarCodeRuleType> fromCode(int code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}
