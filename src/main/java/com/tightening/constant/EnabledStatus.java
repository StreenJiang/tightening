package com.tightening.constant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum EnabledStatus {
    DISABLED(0),
    ENABLED(1);

    @JsonValue
    private final int code;
    private static final Map<Integer, EnabledStatus> BY_CODE =
        Arrays.stream(values()).collect(Collectors.toMap(EnabledStatus::getCode, Function.identity()));

    EnabledStatus(int code) { this.code = code; }

    @JsonCreator
    public static EnabledStatus fromCode(int code) {
        return Optional.ofNullable(BY_CODE.get(code))
            .orElseThrow(() -> new IllegalArgumentException("Unknown EnabledStatus code: " + code));
    }
}
