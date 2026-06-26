package com.tightening.device.contract;

import com.tightening.constant.DeviceType;

public interface IDevice {
    Long id();
    DeviceType type();
    boolean isConnected();
}
