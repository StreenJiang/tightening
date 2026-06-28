package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum ExportTaskStatus {
    PENDING(0),
    PROCESSING(1),
    COMPLETED(2),
    FAILED(3);

    private final int code;

    ExportTaskStatus(int code) {
        this.code = code;
    }

    public static Optional<ExportTaskStatus> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
