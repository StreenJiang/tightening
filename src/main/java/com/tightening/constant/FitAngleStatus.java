package com.tightening.constant;

import java.util.Arrays;
import java.util.Optional;

public enum FitAngleStatus {
    OK(1),
    NG(2);

    private final int code;

    FitAngleStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Optional<FitAngleStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
