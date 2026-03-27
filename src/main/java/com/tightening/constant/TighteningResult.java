package com.tightening.constant;

import lombok.Getter;

@Getter
public enum TighteningResult {
    OK(1),
    NG(0),
    ;

    private final int code;

    TighteningResult(int code) {
        this.code = code;
    }
}
