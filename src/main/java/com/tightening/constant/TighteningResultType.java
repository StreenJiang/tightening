package com.tightening.constant;

import lombok.Getter;

@Getter
public enum TighteningResultType {
    TIGHTENING(1),
    LOOSENING(2),
    ;

    private final int code;

    TighteningResultType(int code) {
        this.code = code;
    }
}
