package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum PrerequisiteType {
    SAME_TRACE(1),
    PARTS_TRACE(2),
    INSPECTION_CHAIN(3);

    private final int code;
    private static final Map<Integer, PrerequisiteType> BY_CODE =
        Arrays.stream(values()).collect(Collectors.toMap(PrerequisiteType::getCode, Function.identity()));

    PrerequisiteType(int code) { this.code = code; }

    public static Optional<PrerequisiteType> fromCode(int code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}
