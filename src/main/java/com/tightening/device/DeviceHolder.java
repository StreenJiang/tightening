package com.tightening.device;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.entity.Device;
import io.netty.channel.Channel;
import lombok.Data;

@Data
public class DeviceHolder {
    private final Device device;
    private volatile DeviceStatus status;
    private volatile Channel channel;

    public DeviceHolder(Device device) {
        this.device = device;
        status = DeviceStatus.DISCONNECTED;
    }

    public String resolveToolTypeName() {
        DeviceType deviceType = DeviceType.getType(device.getType());
        return deviceType != null ? deviceType.getName() : null;
    }
}
