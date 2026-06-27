package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum Stage {
    VALIDATION(0),
    ACTIVATION(1),
    OPERATION(2),
    FINALIZATION(3);

    private final int code;

    Stage(int code) {
        this.code = code;
    }

    public static Optional<Stage> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
