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
public enum PrerequisiteType {
    SAME_TRACE(1),
    MATERIAL_TRACE(2),
    INSPECTION_CHAIN(3);

    @EnumValue
    @JsonValue
    private final int code;
    private static final Map<Integer, PrerequisiteType> BY_CODE =
        Arrays.stream(values()).collect(Collectors.toMap(PrerequisiteType::getCode, Function.identity()));

    PrerequisiteType(int code) { this.code = code; }

    @JsonCreator
    public static PrerequisiteType fromCode(int code) {
        return Optional.ofNullable(BY_CODE.get(code))
            .orElseThrow(() -> new IllegalArgumentException("Unknown PrerequisiteType code: " + code));
    }
}
