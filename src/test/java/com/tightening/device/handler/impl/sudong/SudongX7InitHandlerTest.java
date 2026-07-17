package com.tightening.device.handler.impl.sudong;

import com.tightening.constant.DeviceStatus;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.ToolHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.entity.Device;
import com.tightening.netty.protocol.handler.sudongx7.SudongX7InitHandler;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SudongX7InitHandler")
class SudongX7InitHandlerTest {

    @Mock
    private ToolHandler deviceHandler;

    private DeviceHolder deviceHolder;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        Device device = new Device();
        device.setId(100L);
        deviceHolder = new DeviceHolder(device);

        channel = new EmbeddedChannel();
        channel.attr(TCPDeviceHandler.DEVICE_ID).set(100L);
        channel.attr(TCPDeviceHandler.DEVICE_HOLDER).set(deviceHolder);
        channel.attr(TCPDeviceHandler.MANUALLY_CLOSE).set(true);

        when(deviceHandler.forceLock(100L)).thenReturn(CompletableFuture.completedFuture(true));
        channel.pipeline().addLast(new SudongX7InitHandler(deviceHandler));
    }

    @AfterEach
    void cleanUp() {
        channel.finish();
    }

    @Test
    @DisplayName("channelActive should forceLock the tool")
    void channelActive_shouldForceLock() {
        channel.pipeline().fireChannelActive();

        verify(deviceHandler).forceLock(100L);
    }

    @Test
    @DisplayName("channelActive should set device status to CONNECTED via DeviceInitHandler")
    void channelActive_shouldSetConnected() {
        assertThat(deviceHolder.getStatus()).isEqualTo(DeviceStatus.DISCONNECTED);

        channel.pipeline().fireChannelActive();

        assertThat(deviceHolder.getStatus()).isEqualTo(DeviceStatus.CONNECTED);
    }
}
