package com.tightening.device.handler;

import com.tightening.constant.DeviceType;
import com.tightening.device.handler.impl.AtlasPF4000Handler;
import com.tightening.device.handler.impl.AtlasPF6000OPHandler;
import com.tightening.device.handler.impl.FitFTC6Handler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DeviceHandlerFactoryTest {

    @Test
    void getHandler_returnsCorrectHandlerForEachDeviceType() {
        AtlasPF4000Handler atlas4000 = mock(AtlasPF4000Handler.class);
        AtlasPF6000OPHandler atlas6000 = mock(AtlasPF6000OPHandler.class);
        FitFTC6Handler fit = mock(FitFTC6Handler.class);

        DeviceHandlerFactory factory = new DeviceHandlerFactory(List.of(atlas4000, atlas6000, fit));

        assertThat(factory.getHandler(DeviceType.ATLAS_PF4000)).isSameAs(atlas4000);
        assertThat(factory.getHandler(DeviceType.ATLAS_PF6000_OP)).isSameAs(atlas6000);
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
        AtlasPF4000Handler atlas4000 = mock(AtlasPF4000Handler.class);
        DeviceHandlerFactory factory = new DeviceHandlerFactory(List.of(atlas4000));

        assertThatThrownBy(() -> factory.getHandler(DeviceType.ATLAS_PF6000_OP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No handler for");
    }

    @Test
    void constructor_acceptsSubsetOfHandlers() {
        FitFTC6Handler fit = mock(FitFTC6Handler.class);

        DeviceHandlerFactory factory = new DeviceHandlerFactory(List.of(fit));

        // Only FIT_FTC6 should be mappable
        assertThat(factory.getHandler(DeviceType.FIT_FTC6)).isSameAs(fit);
        assertThatThrownBy(() -> factory.getHandler(DeviceType.ATLAS_PF4000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
