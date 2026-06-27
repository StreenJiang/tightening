package com.tightening.lifecycle.message;

public sealed interface EngineInternal extends InboundMessage {

    /** 定时监控 tick（由 ScheduledExecutorService 定时投递） */
    record MonitorTick() implements EngineInternal {}

    /** 引擎崩溃 / 故障 */
    record Faulted(String reason) implements EngineInternal {}
}
