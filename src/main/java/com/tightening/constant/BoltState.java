package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum BoltState {
    PENDING(0),
    TIGHTENING(1),
    JUDGED_OK(2),
    JUDGED_NG(3);

    private final int code;

    BoltState(int code) {
        this.code = code;
    }

    public static Optional<BoltState> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
