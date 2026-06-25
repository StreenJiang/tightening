package com.tightening.constant;

import lombok.Getter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum IoDeviceType {
    ARRANGER(1),
    SETTER_SELECTOR(2);

    private final int code;
    private static final Map<Integer, IoDeviceType> BY_CODE =
        Arrays.stream(values()).collect(Collectors.toMap(IoDeviceType::getCode, Function.identity()));

    IoDeviceType(int code) { this.code = code; }

    public static Optional<IoDeviceType> fromCode(int code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}
