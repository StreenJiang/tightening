package com.tightening.device.handler;

import com.tightening.constant.DeviceStatus;
import com.tightening.entity.Device;

public interface DeviceHandler {

    void connect(long deviceId);

    void disconnect(long deviceId);

    DeviceStatus getStatus(long deviceId);
}