package com.tightening.device.handler;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;

import java.util.Set;

public interface DeviceHandler {

    void connect(long deviceId);

    void disconnect(long deviceId);

    DeviceStatus getStatus(long deviceId);

    Set<DeviceType> getSupportedTypes();
}
