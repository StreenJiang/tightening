package com.tightening.lifecycle.message;

/** 所有 Actor inbox 消息的顶层标记接口 */
public sealed interface InboundMessage
    permits InboundCommand, DeviceEvent, EngineInternal {
}
