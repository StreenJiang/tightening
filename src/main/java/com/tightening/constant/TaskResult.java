package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum TaskResult {
    NG(0),
    OK(1);

    private final int code;

    TaskResult(int code) {
        this.code = code;
    }

    public static Optional<TaskResult> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
