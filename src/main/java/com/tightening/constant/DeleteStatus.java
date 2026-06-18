package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum DeleteStatus {
    NORMAL(0),
    DELETED(1);

    private final int code;

    DeleteStatus(int code) {
        this.code = code;
    }

    public static Optional<DeleteStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
