package com.tightening.device;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.entity.Device;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.locks.ReentrantLock;

@Getter
public class DeviceHolder {
    private final Device device;
    @Setter private volatile DeviceStatus status;
    @Setter private volatile Channel channel;

    private final ReentrantLock stateLock = new ReentrantLock();
    private final ReentrantLock pSetLock = new ReentrantLock();
    @Setter private volatile boolean isToolEnabled = false;
    @Setter private volatile long lastEnableTime = 0;
    @Setter private volatile long lastDisableTime = 0;

    public DeviceHolder(Device device) {
        this.device = device;
        status = DeviceStatus.DISCONNECTED;
    }

    public String resolveToolTypeName() {
        DeviceType deviceType = DeviceType.getType(device.getType());
        return deviceType != null ? deviceType.getName() : null;
    }
}
