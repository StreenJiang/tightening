package com.tightening.dto;

import com.tightening.i18n.Messages;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

public record ApiResponse<T>(int code, @Nullable String errorCode, String message, @Nullable T data) {

    private static final HttpStatus OK = HttpStatus.OK;
    private static String okMessage;

    public static <T> ApiResponse<T> ok(@Nullable T data) {
        if (okMessage == null) {
            okMessage = Messages.get("common.ok");
        }
        return new ApiResponse<>(OK.value(), null, okMessage, data);
    }

    public static ApiResponse<String> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(String errorCode, Object... args) {
        return fail(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, args);
    }

    public static <T> ApiResponse<T> fail(HttpStatus status, String errorCode, Object... args) {
        return new ApiResponse<>(status.value(), errorCode, Messages.get(errorCode, args), null);
    }
}
