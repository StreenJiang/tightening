package com.tightening.constant;

/** SSE 事件类型常量。替代旧 SseEventType enum。 */
public final class SseEvents {
    private SseEvents() {}

    public static final String TASK_CONTROL = "task:control";
    public static final String TIGHTENING_DATA = "tightening:data";
    public static final String CURVE_DATA = "curve:data";
    public static final String DEVICE_STATUS = "device:status";
    public static final String WORKPLACE_STATUS = "workplace:status";
}
