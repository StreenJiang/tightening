package com.tightening.constant;

import java.util.Arrays;
import java.util.Optional;

public enum FitTorqueStatus {
    OK(1),
    NG(2);

    private final int code;

    FitTorqueStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Optional<FitTorqueStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
