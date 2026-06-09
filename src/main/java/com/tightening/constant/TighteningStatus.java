package com.tightening.constant;

import java.util.Arrays;
import java.util.Optional;

public enum TighteningStatus {
    NG(0),
    OK(1);

    private final int code;

    TighteningStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static Optional<TighteningStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
