package com.tightening.i18n;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final Object[] args;
    private final HttpStatus httpStatus;

    public BusinessException(HttpStatus httpStatus, String errorCode, Object... args) {
        super(errorCode);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.args = args;
    }

    public static BusinessException of(String code, Object... args) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, args);
    }

    public static BusinessException notFound(String code, Object... args) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, args);
    }

    public static BusinessException conflict(String code, Object... args) {
        return new BusinessException(HttpStatus.CONFLICT, code, args);
    }

    public static String toPersistenceString(Throwable t) {
        if (t instanceof BusinessException be) {
            return toErrorString(be.getErrorCode(), be.getArgs());
        }
        return t.getMessage();
    }

    /**
     * 生成持久化字段的 errorCode 格式字符串。
     * 格式：errorCode 或 errorCode:arg1,arg2
     */
    public static String toErrorString(String errorCode, Object... args) {
        if (args == null || args.length == 0) {
            return errorCode;
        }
        StringBuilder sb = new StringBuilder(errorCode).append(':');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
