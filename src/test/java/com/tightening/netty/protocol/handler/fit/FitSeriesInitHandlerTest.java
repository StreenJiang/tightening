package com.tightening.netty.protocol.handler.fit;

import com.tightening.constant.DeviceStatus;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.ToolHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FitSeriesInitHandlerTest {

    @Mock
    private ToolHandler deviceHandler;

    @Mock
    private DeviceHolder deviceHolder;

    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        channel = new EmbeddedChannel();
        channel.attr(TCPDeviceHandler.DEVICE_ID).set(100L);
        channel.attr(TCPDeviceHandler.DEVICE_HOLDER).set(deviceHolder);
        channel.attr(TCPDeviceHandler.MANUALLY_CLOSE).set(true);
        channel.pipeline().addLast(new FitSeriesInitHandler(deviceHandler));
    }

    @AfterEach
    void cleanUp() {
        channel.finish();
    }

    @Test
    void channelActive_shouldForceDisableTool() {
        channel.pipeline().fireChannelActive();

        verify(deviceHandler).forceDisableToolOp(100L);
    }

    @Test
    void channelActive_shouldSetDeviceStatusConnected() {
        channel.pipeline().fireChannelActive();

        verify(deviceHolder).setStatus(DeviceStatus.CONNECTED);
    }
}
