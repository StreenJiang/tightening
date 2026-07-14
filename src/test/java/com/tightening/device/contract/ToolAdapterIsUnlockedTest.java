package com.tightening.device.contract;

import com.tightening.constant.DeviceType;
import com.tightening.device.handler.ToolHandler;
import com.tightening.entity.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolAdapterIsUnlockedTest {

    private ToolHandler handler;
    private ToolAdapter adapter;
    private Device device;

    @BeforeEach
    void setUp() {
        handler = mock(ToolHandler.class);
        device = new Device();
        device.setId(2L);
        device.setType(DeviceType.FIT_FTC6.getId());
        adapter = new ToolAdapter(handler, device);
    }

    @Test
    @DisplayName("isUnlocked should delegate to handler.isUnlocked(deviceId)")
    void shouldDelegateToHandler() {
        when(handler.isUnlocked(2L)).thenReturn(true);
        assertThat(adapter.isUnlocked()).isTrue();

        when(handler.isUnlocked(2L)).thenReturn(false);
        assertThat(adapter.isUnlocked()).isFalse();
    }
}
