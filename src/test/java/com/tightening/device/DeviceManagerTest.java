package com.tightening.device;

import com.tightening.config.DeviceConfig;
import com.tightening.constant.DeviceChangeType;
import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.event.DeviceChangeEvent;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.entity.Device;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceManagerTest {

    private DeviceHandler mockHandler;
    private DeviceManager deviceManager;

    @BeforeEach
    void setUp() {
        // Mock handler used by DeviceType.handlerProvider
        mockHandler = mock(DeviceHandler.class);
        when(mockHandler.getStatus(anyLong())).thenReturn(DeviceStatus.DISCONNECTED);

        // Real DeviceConfig with short timeouts for fast cleanup, long scan delay to avoid interference
        DeviceConfig deviceConfig = new DeviceConfig();

        DeviceConfig.ConnectThread connectThread = new DeviceConfig.ConnectThread();
        connectThread.setCorePoolSize(1);
        connectThread.setMaxPoolSize(1);
        connectThread.setKeepAliveTimeMs(1000);
        connectThread.setCapacity(10);
        connectThread.setTerminationAwaitMs(5);
        deviceConfig.setConnectThread(connectThread);

        DeviceConfig.ScanThread scanThread = new DeviceConfig.ScanThread();
        scanThread.setInitDelayMs(60000);
        scanThread.setDelayMs(60000);
        scanThread.setTerminationAwaitMs(5);
        deviceConfig.setScanThread(scanThread);

        // Wire the mock handler into the static provider so DeviceType.getHandlerByTypeId() returns it
        DeviceType.initProvider(type -> mockHandler);

        deviceManager = new DeviceManager(deviceConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        deviceManager.close();
        // Reset static state for other tests
        DeviceType.initProvider(null);
    }

    @Test
    void getHandler_shouldReturnNullForUnknownDevice() {
        assertThat(deviceManager.getHandler(999L)).isNull();
    }

    @Test
    void userLoggedIn_shouldAddDevices() {
        Device device = new Device(); device.setId(1L); device.setType(1);
        deviceManager.userLoggedIn(List.of(device));

        assertThat(deviceManager.getHandler(1L)).isSameAs(mockHandler);
    }

    @Test
    void handleDeviceChange_add_shouldAddHandler() {
        Device device = new Device(); device.setId(1L); device.setType(1);
        DeviceChangeEvent event = new DeviceChangeEvent(this, DeviceChangeType.ADD, device);

        deviceManager.handleDeviceChange(event);

        assertThat(deviceManager.getHandler(1L)).isSameAs(mockHandler);
    }

    @Test
    void handleDeviceChange_delete_shouldDisconnectAndRemove() {
        // Arrange: add a device first
        Device device = new Device(); device.setId(1L); device.setType(1);
        deviceManager.userLoggedIn(List.of(device));

        // Act: send DELETE event
        DeviceChangeEvent event = new DeviceChangeEvent(this, DeviceChangeType.DELETE, 1L);
        deviceManager.handleDeviceChange(event);

        // Assert: handler disconnected and removed from map
        verify(mockHandler).disconnect(1L);
        assertThat(deviceManager.getHandler(1L)).isNull();
    }

    @Test
    void handleDeviceChange_update_shouldReplaceHandler() {
        // Arrange: add a device first
        Device device = new Device(); device.setId(1L); device.setType(1);
        deviceManager.userLoggedIn(List.of(device));

        // Act: send UPDATE event (same mock handler gets re-inserted after remove)
        DeviceChangeEvent event = new DeviceChangeEvent(this, DeviceChangeType.UPDATE, device);
        deviceManager.handleDeviceChange(event);

        // Assert: old handler was disconnected, new handler is in place
        verify(mockHandler).disconnect(1L);
        assertThat(deviceManager.getHandler(1L)).isSameAs(mockHandler);
    }

    @Test
    void userLoggedOut_shouldDisconnectAllAndClear() {
        // Arrange: add two devices
        Device device1 = new Device(); device1.setId(1L); device1.setType(1);
        Device device2 = new Device(); device2.setId(2L); device2.setType(1);
        deviceManager.userLoggedIn(List.of(device1, device2));

        // Act
        deviceManager.userLoggedOut();

        // Assert: both handlers disconnected and map cleared
        verify(mockHandler).disconnect(1L);
        verify(mockHandler).disconnect(2L);
        assertThat(deviceManager.getHandler(1L)).isNull();
        assertThat(deviceManager.getHandler(2L)).isNull();
    }
}
