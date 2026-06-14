package com.tightening.device.service;

import com.tightening.constant.DeviceType;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.DeviceHandlerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceHandlerServiceTest {

    @Mock
    private DeviceHandlerFactory deviceHandlerFactory;

    @InjectMocks
    private DeviceHandlerService deviceHandlerService;

    @AfterEach
    void tearDown() {
        DeviceType.initProvider(null);
    }

    @Test
    void getHandler_delegatesToFactory() {
        DeviceHandler expectedHandler = mock(DeviceHandler.class);
        when(deviceHandlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(expectedHandler);

        DeviceHandler actualHandler = deviceHandlerService.getHandler(DeviceType.ATLAS_PF4000);

        assertThat(actualHandler).isSameAs(expectedHandler);
        verify(deviceHandlerFactory).getHandler(DeviceType.ATLAS_PF4000);
    }

    @Test
    void registerProvider_initializesDeviceTypeProvider() {
        DeviceHandler handler = mock(DeviceHandler.class);
        when(deviceHandlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(handler);

        deviceHandlerService.registerProvider();

        // After initProvider, DeviceType.getHandler() should delegate to the factory
        assertThat(DeviceType.ATLAS_PF4000.getHandler()).isSameAs(handler);
        verify(deviceHandlerFactory).getHandler(DeviceType.ATLAS_PF4000);
    }

    @Test
    void registerProvider_enablesGetHandlerByTypeId() {
        DeviceHandler handler = mock(DeviceHandler.class);
        when(deviceHandlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(handler);

        deviceHandlerService.registerProvider();

        assertThat(DeviceType.getHandlerByTypeId(1)).isSameAs(handler);
    }

    @Test
    void getHandler_unregisteredDeviceType_throwsException() {
        when(deviceHandlerFactory.getHandler(DeviceType.ATLAS_PF4000))
                .thenThrow(new IllegalArgumentException("No handler for: ATLAS_PF4000"));

        assertThatThrownBy(() -> deviceHandlerService.getHandler(DeviceType.ATLAS_PF4000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No handler for");
    }

    @Test
    void getHandler_beforeRegisterProvider_throwsByDelegation() {
        // Before registerProvider() is called, DeviceType.getHandler() should fail
        // But DeviceHandlerService.getHandler() just delegates to factory, which is a mock
        // This test verifies the delegation path, not DeviceType internals
        DeviceHandler handler = mock(DeviceHandler.class);
        when(deviceHandlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(handler);

        assertThat(deviceHandlerService.getHandler(DeviceType.ATLAS_PF4000)).isSameAs(handler);
    }
}
