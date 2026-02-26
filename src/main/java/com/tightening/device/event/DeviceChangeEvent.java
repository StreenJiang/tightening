package com.tightening.device.event;

import com.tightening.constant.DeviceEventType;
import com.tightening.entity.Device;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DeviceChangeEvent extends ApplicationEvent {
    private final DeviceEventType eventType;
    private final Long deviceId;
    private final Device device; // 可选，新增或修改时携带完整设备信息

    public DeviceChangeEvent(Object source, DeviceEventType eventType, Long deviceId) {
        super(source);
        this.eventType = eventType;
        this.deviceId = deviceId;
        this.device = null;
    }

    public DeviceChangeEvent(Object source, DeviceEventType eventType, Device device) {
        super(source);
        this.eventType = eventType;
        this.deviceId = device.getId();
        this.device = device;
    }
}
