package com.tightening.netty.protocol.handler.atlas;

import com.tightening.constant.DeviceStatus;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.impl.AtlasPFSeriesHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.entity.Device;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AtlasPFSeriesInitHandler initialisation pipeline")
class AtlasPFSeriesInitHandlerTest {

    private static final long TEST_DEVICE_ID = 100L;

    @Mock
    private AtlasPFSeriesHandler deviceHandler;

    private EmbeddedChannel channel;
    private DeviceHolder deviceHolder;
    private AtlasPFSeriesInitHandler initHandler;

    @BeforeEach
    void setUp() {
        // lenient: not all stubs are used in every test
        lenient().when(deviceHandler.generateKey(any(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0) + ":" + invocation.getArgument(1));
        lenient().when(deviceHandler.connectToController(anyLong()))
                .thenReturn(CompletableFuture.completedFuture(true));
        lenient().when(deviceHandler.subscribeTighteningData(anyLong()))
                .thenReturn(CompletableFuture.completedFuture(true));
        lenient().when(deviceHandler.forceLock(anyLong()))
                .thenReturn(CompletableFuture.completedFuture(true));

        Device device = new Device();
        device.setId(TEST_DEVICE_ID);
        deviceHolder = new DeviceHolder(device);

        channel = new EmbeddedChannel();
        channel.attr(TCPDeviceHandler.DEVICE_HOLDER).set(deviceHolder);
        channel.attr(TCPDeviceHandler.DEVICE_ID).set(TEST_DEVICE_ID);
        // Prevent NPE in channelInactive during cleanup (finish())
        channel.attr(TCPDeviceHandler.MANUALLY_CLOSE).set(true);

        initHandler = new AtlasPFSeriesInitHandler(deviceHandler);
        channel.pipeline().addLast(initHandler);
    }

    @AfterEach
    void cleanUp() {
        channel.finish();
    }

    @Test
    @DisplayName("successful pipeline: channel stays open")
    void testSuccessfulPipeline() {
        channel.pipeline().fireChannelActive();

        assertThat(deviceHolder.getStatus()).isEqualTo(DeviceStatus.CONNECTED);
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    @DisplayName("connect fails: channel closed")
    void testConnectFails() {
        when(deviceHandler.connectToController(TEST_DEVICE_ID))
                .thenReturn(CompletableFuture.completedFuture(false));

        channel.pipeline().fireChannelActive();

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    @DisplayName("subscribe fails: channel closed")
    void testSubscribeFails() {
        when(deviceHandler.subscribeTighteningData(TEST_DEVICE_ID))
                .thenReturn(CompletableFuture.completedFuture(false));

        channel.pipeline().fireChannelActive();

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    @DisplayName("force disable fails: channel stays open (non-critical)")
    void testForceDisableFails() {
        when(deviceHandler.forceLock(TEST_DEVICE_ID))
                .thenReturn(CompletableFuture.completedFuture(false));

        channel.pipeline().fireChannelActive();

        // force disable failure is non-critical and only logged
        assertThat(channel.isOpen()).isTrue();
    }
}
