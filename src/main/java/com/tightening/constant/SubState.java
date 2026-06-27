package com.tightening.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum SubState {
    IDLE(0),
    VALIDATING(1),
    PREPARING(2),
    ACTIVATING(3),
    SWITCH_BOLT(4),
    TIGHTENING_RECEIVED(5),
    JUDGING(6),
    STORING(7),
    ADVANCING(8),
    CLEANING_TASKS(9),
    LOCKING_TOOLS(10),
    RESETTING_STATE(11),
    EXPORTING(12),
    FAULTED(99);

    private final int code;

    SubState(int code) {
        this.code = code;
    }

    public static Optional<SubState> fromCode(int code) {
        return Arrays.stream(values()).filter(s -> s.code == code).findFirst();
    }
}
