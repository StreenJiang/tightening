package com.tightening.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum InspectionScope {
    ALL(1),
    CHOSEN(2);

    @EnumValue
    @JsonValue
    private final int code;
    private static final Map<Integer, InspectionScope> BY_CODE =
        Arrays.stream(values()).collect(Collectors.toMap(InspectionScope::getCode, Function.identity()));

    InspectionScope(int code) { this.code = code; }

    @JsonCreator
    public static InspectionScope fromCode(int code) {
        return Optional.ofNullable(BY_CODE.get(code))
            .orElseThrow(() -> new IllegalArgumentException("Unknown InspectionScope code: " + code));
    }
}
