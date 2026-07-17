package com.tightening.device.handler.impl;

import com.tightening.config.DeviceConfig;
import com.tightening.config.ToolCommonConfig;
import com.tightening.constant.DeviceType;
import com.tightening.service.CurveDataService;
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
class AtlasPFSeriesHandlerTest {

    @Mock
    private NioEventLoopGroup group;

    @Mock
    private DeviceService deviceService;

    @Mock
    private TighteningDataService tighteningDataService;

    @Mock
    private CurveDataService curveDataService;

    private final ToolCommonConfig config = new ToolCommonConfig();

    @Mock
    private DeviceConfig deviceConfig;

    @Test
    void constructAtlasPFSeriesHandler_shouldBeNonNull() {
        AtlasPFSeriesHandler handler = new AtlasPFSeriesHandler(group, deviceService, tighteningDataService, curveDataService, config, deviceConfig);
        assertThat(handler).isNotNull();
    }

    @Test
    void getSupportedTypes_shouldReturnAtlasTypes() {
        AtlasPFSeriesHandler handler = new AtlasPFSeriesHandler(group, deviceService, tighteningDataService, curveDataService, config, deviceConfig);
        Set<DeviceType> types = handler.getSupportedTypes();
        assertThat(types).containsExactlyInAnyOrder(DeviceType.ATLAS_PF4000, DeviceType.ATLAS_PF6000_OP);
    }
}
