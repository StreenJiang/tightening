package com.tightening.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.tightening.constant.DeviceChangeType;
import com.tightening.device.event.DeviceChangeEvent;
import com.tightening.dto.DeviceDTO;
import com.tightening.entity.Device;
import com.tightening.mapper.DeviceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(eventPublisher);
        ReflectionTestUtils.setField(deviceService, "baseMapper", deviceMapper);
    }

    @Test
    void addDevice_shouldInsertDeviceAndPublishAddEvent() {
        DeviceDTO dto = new DeviceDTO();
        dto.setName("pf-6000");
        dto.setType(1);
        dto.setDescription("atlas copco");
        dto.setDetail("production line A");

        deviceService.addDevice(dto);

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).insert(deviceCaptor.capture());
        Device device = deviceCaptor.getValue();
        assertThat(device.getName()).isEqualTo("pf-6000");
        assertThat(device.getType()).isEqualTo(1);
        assertThat(device.getDescription()).isEqualTo("atlas copco");
        assertThat(device.getDetail()).isEqualTo("production line A");

        ArgumentCaptor<DeviceChangeEvent> eventCaptor = ArgumentCaptor.forClass(DeviceChangeEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DeviceChangeEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(DeviceChangeType.ADD);
        assertThat(event.getDevice()).isEqualTo(device);
    }

    @Test
    void updateDevice_shouldUpdateByIdAndPublishUpdateEvent() {
        DeviceDTO dto = new DeviceDTO();
        dto.setId(10L);
        dto.setName("fit-ftc6");
        dto.setType(2);
        dto.setDescription("updated description");

        deviceService.updateDevice(dto);

        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).updateById(deviceCaptor.capture());
        Device device = deviceCaptor.getValue();
        assertThat(device.getId()).isEqualTo(10L);
        assertThat(device.getName()).isEqualTo("fit-ftc6");
        assertThat(device.getType()).isEqualTo(2);
        assertThat(device.getDescription()).isEqualTo("updated description");

        ArgumentCaptor<DeviceChangeEvent> eventCaptor = ArgumentCaptor.forClass(DeviceChangeEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DeviceChangeEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(DeviceChangeType.UPDATE);
        assertThat(event.getDevice()).isEqualTo(device);
    }

    @Test
    void deleteDevice_shouldDeleteByIdAndPublishDeleteEvent() {
        Long deviceId = 42L;

        deviceService.deleteDevice(deviceId);

        verify(deviceMapper).deleteById(deviceId);

        ArgumentCaptor<DeviceChangeEvent> eventCaptor = ArgumentCaptor.forClass(DeviceChangeEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DeviceChangeEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(DeviceChangeType.DELETE);
        assertThat(event.getDeviceId()).isEqualTo(deviceId);
    }
}
