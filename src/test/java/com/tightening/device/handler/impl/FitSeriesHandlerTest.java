package com.tightening.device.handler.impl;

import com.tightening.config.FitConfig;
import com.tightening.config.ToolCommonConfig;
import com.tightening.constant.DeviceType;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FitSeriesHandlerTest {

    @Mock
    private NioEventLoopGroup group;

    @Mock
    private DeviceService deviceService;

    @Mock
    private TighteningDataService tighteningDataService;

    private final ToolCommonConfig toolCommonConfig = new ToolCommonConfig();
    private final FitConfig fitConfig = new FitConfig();

    @Test
    void constructFitSeriesHandler_shouldBeNonNull() {
        FitSeriesHandler handler = new FitSeriesHandler(group, deviceService, fitConfig, tighteningDataService, toolCommonConfig);
        assertThat(handler).isNotNull();
    }

    @Test
    void getSupportedTypes_shouldReturnFitType() {
        FitSeriesHandler handler = new FitSeriesHandler(group, deviceService, fitConfig, tighteningDataService, toolCommonConfig);
        Set<DeviceType> types = handler.getSupportedTypes();
        assertThat(types).containsExactly(DeviceType.FIT_FTC6);
    }
}
