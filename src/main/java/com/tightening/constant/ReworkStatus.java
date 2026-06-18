package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum ReworkStatus {
    NORMAL(0),
    REWORK(1);

    private final int code;

    ReworkStatus(int code) {
        this.code = code;
    }

    public static Optional<ReworkStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
