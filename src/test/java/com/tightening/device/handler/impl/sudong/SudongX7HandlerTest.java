package com.tightening.device.handler.impl.sudong;

import com.tightening.config.DeviceConfig;
import com.tightening.config.ToolCommonConfig;
import com.tightening.constant.DeviceType;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.impl.SudongX7Handler;
import com.tightening.entity.Device;
import com.tightening.service.CurveDataService;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SudongX7Handler")
class SudongX7HandlerTest {

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
    private ExposedSudongX7Handler handler;
    private EmbeddedChannel channel;

    static class ExposedSudongX7Handler extends SudongX7Handler {
        ExposedSudongX7Handler(NioEventLoopGroup group,
                                DeviceService deviceService,
                                TighteningDataService tighteningDataService,
                                CurveDataService curveDataService,
                                ToolCommonConfig toolCommonConfig,
                                DeviceConfig deviceConfig) {
            super(group, deviceService, tighteningDataService, curveDataService, toolCommonConfig, deviceConfig, null);
        }

        @Override
        public CompletableFuture<Boolean> unlockTool(long deviceId) {
            return super.unlockTool(deviceId);
        }

        @Override
        public CompletableFuture<Boolean> lockTool(long deviceId) {
            return super.lockTool(deviceId);
        }

        @Override
        public CompletableFuture<Boolean> sendPSetCmd(long deviceId, int pSet) {
            return super.sendPSetCmd(deviceId, pSet);
        }

        @Override
        public DeviceHolder getHolder(long deviceId) {
            return super.getHolder(deviceId);
        }
    }

    @BeforeEach
    void setUp() {
        group = new NioEventLoopGroup(1);
        toolCommonConfig = new ToolCommonConfig();
        toolCommonConfig.setCmdTimeoutMs(5000);
        handler = new ExposedSudongX7Handler(
                group, deviceService, tighteningDataService,
                curveDataService, toolCommonConfig, deviceConfig);
        channel = new EmbeddedChannel();
    }

    @AfterEach
    void tearDown() {
        channel.finish();
        group.shutdownGracefully();
    }

    @Test
    @DisplayName("getSupportedTypes returns SUDONG_X7")
    void getSupportedTypes() {
        Set<DeviceType> types = handler.getSupportedTypes();
        assertThat(types).containsExactly(DeviceType.SUDONG_X7);
    }

    @Test
    @DisplayName("unlockTool returns completedFuture(true)")
    void unlockTool() {
        Device device = new Device();
        device.setId(1L);
        handler.addDeviceInfo(device);

        CompletableFuture<Boolean> result = handler.unlockTool(1L);
        assertThat(result).isCompletedWithValue(true);
    }

    @Test
    @DisplayName("lockTool returns completedFuture(true)")
    void lockTool() {
        Device device = new Device();
        device.setId(1L);
        handler.addDeviceInfo(device);

        CompletableFuture<Boolean> result = handler.lockTool(1L);
        assertThat(result).isCompletedWithValue(true);
    }

    @Test
    @DisplayName("sendPSetCmd returns a CompletableFuture")
    void sendPSetCmd() {
        Device device = new Device();
        device.setId(1L);
        handler.addDeviceInfo(device);
        handler.getHolder(1L).setChannel(channel);

        CompletableFuture<Boolean> result = handler.sendPSetCmd(1L, 5);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("sendHeartbeat returns completedFuture(false)")
    void sendHeartbeat() {
        assertThat(handler.sendHeartbeat(1L)).isCompletedWithValue(false);
    }

    @Test
    @DisplayName("unlock then lock round-trip")
    void unlockThenLock() {
        Device device = new Device();
        device.setId(1L);
        handler.addDeviceInfo(device);

        assertThat(handler.unlockTool(1L)).isCompletedWithValue(true);
        assertThat(handler.lockTool(1L)).isCompletedWithValue(true);
    }
}
