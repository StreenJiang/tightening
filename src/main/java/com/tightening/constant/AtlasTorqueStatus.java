package com.tightening.constant;

import java.util.Arrays;
import java.util.Optional;

public enum AtlasTorqueStatus {
    LOW(0),
    OK(1),
    HIGH(2);

    private final int code;

    AtlasTorqueStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Optional<AtlasTorqueStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
