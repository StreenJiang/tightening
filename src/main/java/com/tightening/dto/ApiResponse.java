package com.tightening.dto;

import org.springframework.http.HttpStatus;

public record ApiResponse<T>(int code, String message, T data) {

    private static final int OK = HttpStatus.OK.value();
    private static final int FAIL = HttpStatus.INTERNAL_SERVER_ERROR.value();

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(OK, "ok", data);
    }

    public static ApiResponse<String> ok() {
        return new ApiResponse<>(OK, "ok", null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(FAIL, message, null);
    }
}
