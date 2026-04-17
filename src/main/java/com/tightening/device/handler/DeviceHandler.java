package com.tightening.device.handler;

import com.tightening.constant.DeviceStatus;

public interface DeviceHandler {

    void connect(long deviceId);

    void disconnect(long deviceId);

    DeviceStatus getStatus(long deviceId);
}
