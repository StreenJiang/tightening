package com.tightening.device.event;

import com.tightening.constant.DeviceChangeType;
import com.tightening.entity.Device;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceChangeEventTest {

    @Test
    void constructor_withDeviceId_setsFieldsCorrectly() {
        Object source = new Object();
        DeviceChangeEvent event = new DeviceChangeEvent(source, DeviceChangeType.ADD, 123L);

        assertThat(event.getSource()).isSameAs(source);
        assertThat(event.getEventType()).isEqualTo(DeviceChangeType.ADD);
        assertThat(event.getDeviceId()).isEqualTo(123L);
        assertThat(event.getDevice()).isNull();
    }

    @Test
    void constructor_withDevice_setsFieldsCorrectly() {
        Object source = new Object();
        Device device = new Device();
        device.setName("test-device");
        device.setId(456L);
        DeviceChangeEvent event = new DeviceChangeEvent(source, DeviceChangeType.UPDATE, device);

        assertThat(event.getSource()).isSameAs(source);
        assertThat(event.getEventType()).isEqualTo(DeviceChangeType.UPDATE);
        assertThat(event.getDeviceId()).isEqualTo(456L);
        assertThat(event.getDevice()).isSameAs(device);
    }

    @Test
    void constructor_withDeviceWithoutId_deviceIdIsNull() {
        Device device = new Device(); // id defaults to null
        Object source = new Object();
        DeviceChangeEvent event = new DeviceChangeEvent(source, DeviceChangeType.DELETE, device);

        assertThat(event.getDeviceId()).isNull();
        assertThat(event.getDevice()).isSameAs(device);
    }

    @Test
    void eventTypeAdd() {
        DeviceChangeEvent event = new DeviceChangeEvent("test", DeviceChangeType.ADD, 1L);
        assertThat(event.getEventType()).isEqualTo(DeviceChangeType.ADD);
    }

    @Test
    void eventTypeUpdate() {
        DeviceChangeEvent event = new DeviceChangeEvent("test", DeviceChangeType.UPDATE, 1L);
        assertThat(event.getEventType()).isEqualTo(DeviceChangeType.UPDATE);
    }

    @Test
    void eventTypeDelete() {
        DeviceChangeEvent event = new DeviceChangeEvent("test", DeviceChangeType.DELETE, 1L);
        assertThat(event.getEventType()).isEqualTo(DeviceChangeType.DELETE);
    }
}
