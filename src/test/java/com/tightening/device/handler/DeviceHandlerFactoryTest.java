package com.tightening.device.handler;

import com.tightening.constant.DeviceType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceHandlerFactoryTest {

    @Test
    void getHandler_returnsCorrectHandlerForEachDeviceType() {
        DeviceHandler atlas = mock(DeviceHandler.class);
        when(atlas.getSupportedTypes()).thenReturn(EnumSet.of(DeviceType.ATLAS_PF4000, DeviceType.ATLAS_PF6000_OP));
        DeviceHandler fit = mock(DeviceHandler.class);
        when(fit.getSupportedTypes()).thenReturn(EnumSet.of(DeviceType.FIT_FTC6));

        DeviceHandlerFactory factory = new DeviceHandlerFactory(List.of(atlas, fit));

        assertThat(factory.getHandler(DeviceType.ATLAS_PF4000)).isSameAs(atlas);
        assertThat(factory.getHandler(DeviceType.ATLAS_PF6000_OP)).isSameAs(atlas);
        assertThat(factory.getHandler(DeviceType.FIT_FTC6)).isSameAs(fit);
    }

    @Test
    void getHandler_emptyFactory_throwsException() {
        DeviceHandlerFactory factory = new DeviceHandlerFactory(List.of());

        assertThatThrownBy(() -> factory.getHandler(DeviceType.ATLAS_PF4000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No handler for");
    }

    @Test
    void getHandler_unmatchedDeviceType_throwsException() {
        DeviceHandler atlas = mock(DeviceHandler.class);
        when(atlas.getSupportedTypes()).thenReturn(EnumSet.of(DeviceType.ATLAS_PF4000));
        DeviceHandlerFactory factory = new DeviceHandlerFactory(List.of(atlas));

        assertThatThrownBy(() -> factory.getHandler(DeviceType.ATLAS_PF6000_OP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No handler for");
    }

    @Test
    void constructor_acceptsSubsetOfHandlers() {
        DeviceHandler fit = mock(DeviceHandler.class);
        when(fit.getSupportedTypes()).thenReturn(EnumSet.of(DeviceType.FIT_FTC6));

        DeviceHandlerFactory factory = new DeviceHandlerFactory(List.of(fit));

        assertThat(factory.getHandler(DeviceType.FIT_FTC6)).isSameAs(fit);
        assertThatThrownBy(() -> factory.getHandler(DeviceType.ATLAS_PF4000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
