package com.tightening.device.handler.impl;

import com.tightening.config.FitConfig;
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
class FitSeriesHandlerTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private TighteningDataService tighteningDataService;

    private final ToolCommonConfig toolCommonConfig = new ToolCommonConfig();
    private final FitConfig fitConfig = new FitConfig();

    @Test
    void constructFitSeriesHandler_shouldBeNonNull() {
        FitSeriesHandler handler = new FitSeriesHandler(deviceService, fitConfig, tighteningDataService, toolCommonConfig);
        assertThat(handler).isNotNull();
    }

    @Test
    void fitFTC6Handler_shouldBeFitSeriesHandlerWithCorrectDeviceType() {
        FitFTC6Handler handler = new FitFTC6Handler(deviceService, fitConfig, tighteningDataService, toolCommonConfig);
        assertThat(handler).isInstanceOf(FitSeriesHandler.class);
        assertThat(DeviceType.FIT_FTC6.getHandlerClass()).isEqualTo(FitFTC6Handler.class);
    }
}
