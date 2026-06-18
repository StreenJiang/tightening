package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum MissionResult {
    NG(0),
    OK(1);

    private final int code;

    MissionResult(int code) {
        this.code = code;
    }

    public static Optional<MissionResult> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
