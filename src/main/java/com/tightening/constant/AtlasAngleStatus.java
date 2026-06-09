package com.tightening.constant;

import java.util.Arrays;
import java.util.Optional;

public enum AtlasAngleStatus {
    LOW(0),
    OK(1),
    HIGH(2);

    private final int code;

    AtlasAngleStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Optional<AtlasAngleStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
