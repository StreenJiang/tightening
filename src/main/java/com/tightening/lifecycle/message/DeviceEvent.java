package com.tightening.lifecycle.message;

import com.tightening.entity.TighteningData;

public sealed interface DeviceEvent extends InboundMessage {

    /** 拧紧数据到达（从 ToolAdapter 监听器转发） */
    record TighteningDataReceived(
        TighteningData data,
        long deviceId
    ) implements DeviceEvent {}

    /** 设备断线 */
    record DeviceDisconnected(long deviceId) implements DeviceEvent {}
}
