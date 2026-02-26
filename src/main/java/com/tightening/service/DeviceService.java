package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.DeviceEventType;
import com.tightening.device.event.DeviceChangeEvent;
import com.tightening.entity.Device;
import com.tightening.mapper.DeviceMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class DeviceService extends ServiceImpl<DeviceMapper, Device> {
    private final ApplicationEventPublisher eventPublisher;

    public DeviceService(final ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void addDevice() {
        Device device = new Device();
        baseMapper.insert(device);
        eventPublisher.publishEvent(new DeviceChangeEvent(this, DeviceEventType.ADD, device));
    }

    public void updateDevice() {
        Device device = new Device();
        baseMapper.updateById(device);
        eventPublisher.publishEvent(new DeviceChangeEvent(this, DeviceEventType.UPDATE, device));
    }

    public void deleteDevice(Long id) {
        baseMapper.deleteById(id);
        eventPublisher.publishEvent(new DeviceChangeEvent(this, DeviceEventType.DELETE, id));
    }
}
