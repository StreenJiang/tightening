package com.tightening.device;

import com.tightening.constant.DeviceStatus;
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
}
