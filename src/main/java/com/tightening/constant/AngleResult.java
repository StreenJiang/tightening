package com.tightening.constant;

import lombok.Getter;

@Getter
public enum AngleResult {
    OK(1),
    NG(2),
    LOW(0),
    HIGH(3),
    ;

    private final int code;

    AngleResult(int code) {
        this.code = code;
    }
}
