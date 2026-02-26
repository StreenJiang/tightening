package com.tightening.device;

import com.tightening.constant.DeviceStatus;
import com.tightening.entity.Device;
import lombok.Data;

@Data
public class DeviceHolder {
    private DeviceStatus status;
    private Device device;
}
