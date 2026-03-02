package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.DeviceChangeType;
import com.tightening.device.event.DeviceChangeEvent;
import com.tightening.dto.DeviceDTO;
import com.tightening.entity.Device;
import com.tightening.mapper.DeviceMapper;
import com.tightening.util.Converter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class DeviceService extends ServiceImpl<DeviceMapper, Device> {
    private final ApplicationEventPublisher eventPublisher;

    public DeviceService(final ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void addDevice(DeviceDTO dto) {
        Device device = Converter.dto2Entity(dto, Device::new);
        baseMapper.insert(device);
        eventPublisher.publishEvent(new DeviceChangeEvent(this, DeviceChangeType.ADD, device));
    }

    public void updateDevice(DeviceDTO dto) {
        Device device = Converter.dto2Entity(dto, Device::new);
        baseMapper.updateById(device);
        eventPublisher.publishEvent(new DeviceChangeEvent(this, DeviceChangeType.UPDATE, device));
    }

    public void deleteDevice(Long id) {
        baseMapper.deleteById(id);
        eventPublisher.publishEvent(new DeviceChangeEvent(this, DeviceChangeType.DELETE, id));
    }
}
