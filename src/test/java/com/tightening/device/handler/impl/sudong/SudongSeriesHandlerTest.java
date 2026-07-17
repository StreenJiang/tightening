package com.tightening.device.handler.impl.sudong;

import com.tightening.config.DeviceConfig;
import com.tightening.config.ToolCommonConfig;
import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.handler.impl.SudongX7Handler;
import com.tightening.entity.Device;
import com.tightening.service.CurveDataService;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SudongSeriesHandler (via SudongX7Handler)")
class SudongSeriesHandlerTest {

    private NioEventLoopGroup group;

    @Mock
    private DeviceService deviceService;

    @Mock
    private TighteningDataService tighteningDataService;

    @Mock
    private CurveDataService curveDataService;

    @Mock
    private DeviceConfig deviceConfig;

    private ToolCommonConfig toolCommonConfig;
    private SudongX7Handler handler;

    static class ExposedSudongX7Handler extends SudongX7Handler {
        ExposedSudongX7Handler(NioEventLoopGroup group,
                                DeviceService deviceService,
                                TighteningDataService tighteningDataService,
                                CurveDataService curveDataService,
                                ToolCommonConfig toolCommonConfig,
                                DeviceConfig deviceConfig) {
            super(group, deviceService, tighteningDataService, curveDataService, toolCommonConfig, deviceConfig);
        }

        @Override
        public ChannelInitializer<NioSocketChannel> setupChannelInitializer() {
            return super.setupChannelInitializer();
        }
    }

    @BeforeEach
    void setUp() {
        group = new NioEventLoopGroup(1);
        toolCommonConfig = new ToolCommonConfig();
        handler = new SudongX7Handler(
                group, deviceService, tighteningDataService,
                curveDataService, toolCommonConfig, deviceConfig);
    }

    @AfterEach
    void tearDown() {
        group.shutdownGracefully();
    }

    @Test
    @DisplayName("getSupportedTypes returns SUDONG_X7")
    void getSupportedTypes() {
        Set<DeviceType> types = handler.getSupportedTypes();
        assertThat(types).containsExactly(DeviceType.SUDONG_X7);
    }

    @Test
    @DisplayName("setupChannelInitializer returns a valid initializer")
    void setupChannelInitializer() {
        ExposedSudongX7Handler exposed = new ExposedSudongX7Handler(
                group, deviceService, tighteningDataService,
                curveDataService, toolCommonConfig, deviceConfig);
        ChannelInitializer<NioSocketChannel> initializer = exposed.setupChannelInitializer();
        assertThat(initializer).isNotNull();
    }

    @Test
    @DisplayName("addDeviceInfo and getStatus round-trip")
    void addDeviceInfoAndGetStatus() {
        Device device = new Device();
        device.setId(1L);
        handler.addDeviceInfo(device);

        assertThat(handler.getStatus(1L)).isEqualTo(DeviceStatus.DISCONNECTED);
    }

    @Test
    @DisplayName("getStatus for unknown device returns NONE")
    void getStatusUnknown() {
        assertThat(handler.getStatus(999L)).isEqualTo(DeviceStatus.NONE);
    }

    @Test
    @DisplayName("sendHeartbeat returns completed false")
    void sendHeartbeat() {
        assertThat(handler.sendHeartbeat(1L).join()).isFalse();
    }

    @Test
    @DisplayName("disconnect unknown device does nothing")
    void disconnectUnknown() {
        handler.disconnect(999L);
    }
}
