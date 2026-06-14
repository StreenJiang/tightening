package com.tightening.device.handler.impl;

import com.tightening.config.ToolCommonConfig;
import com.tightening.constant.DeviceType;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AtlasPFSeriesHandlerTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private TighteningDataService tighteningDataService;

    private final ToolCommonConfig config = new ToolCommonConfig();

    @Test
    void constructAtlasPFSeriesHandler_shouldBeNonNull() {
        AtlasPFSeriesHandler handler = new AtlasPFSeriesHandler(deviceService, tighteningDataService, config);
        assertThat(handler).isNotNull();
    }

    @Test
    void atlasPF4000Handler_shouldBeAtlasPFSeriesHandlerWithCorrectDeviceType() {
        AtlasPF4000Handler handler = new AtlasPF4000Handler(deviceService, tighteningDataService, config);
        assertThat(handler).isInstanceOf(AtlasPFSeriesHandler.class);
        assertThat(DeviceType.ATLAS_PF4000.getHandlerClass()).isEqualTo(AtlasPF4000Handler.class);
    }

    @Test
    void atlasPF6000OPHandler_shouldBeAtlasPFSeriesHandlerWithCorrectDeviceType() {
        AtlasPF6000OPHandler handler = new AtlasPF6000OPHandler(deviceService, tighteningDataService, config);
        assertThat(handler).isInstanceOf(AtlasPFSeriesHandler.class);
        assertThat(DeviceType.ATLAS_PF6000_OP.getHandlerClass()).isEqualTo(AtlasPF6000OPHandler.class);
    }
}
