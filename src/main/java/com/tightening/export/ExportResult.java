package com.tightening.export;

/**
 * 导出操作结果。
 *
 * @param success 是否成功
 * @param message 结果描述
 */
public record ExportResult(boolean success, String message) {

    private static final String DEFAULT_OK_MESSAGE = "OK";

    public static ExportResult ok() {
        return new ExportResult(true, DEFAULT_OK_MESSAGE);
    }

    public static ExportResult ok(String message) {
        return new ExportResult(true, message);
    }

    public static ExportResult fail(String message) {
        return new ExportResult(false, message);
    }
}
