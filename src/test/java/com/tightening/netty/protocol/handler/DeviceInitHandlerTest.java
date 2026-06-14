package com.tightening.netty.protocol.handler;

import com.tightening.constant.DeviceStatus;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.entity.Device;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceInitHandler abstract template method")
class DeviceInitHandlerTest {

    private static final long TEST_DEVICE_ID = 100L;

    @Mock
    private TCPDeviceHandler deviceHandler;

    private EmbeddedChannel channel;
    private DeviceHolder deviceHolder;
    private DeviceInitHandler handler;

    // Template method call flags
    private boolean beforeActiveCalled;
    private boolean afterActiveCalled;
    private boolean beforeInactiveCalled;
    private boolean afterInactiveCalled;
    private boolean cleanUpCalled;

    @BeforeEach
    void setUp() {
        Device device = new Device();
        device.setId(TEST_DEVICE_ID);
        deviceHolder = new DeviceHolder(device);

        channel = new EmbeddedChannel();
        channel.attr(TCPDeviceHandler.DEVICE_HOLDER).set(deviceHolder);
        channel.attr(TCPDeviceHandler.DEVICE_ID).set(TEST_DEVICE_ID);
        channel.attr(TCPDeviceHandler.MANUALLY_CLOSE).set(false);

        beforeActiveCalled = false;
        afterActiveCalled = false;
        beforeInactiveCalled = false;
        afterInactiveCalled = false;
        cleanUpCalled = false;

        // Anonymous subclass that tracks template method invocations
        handler = new DeviceInitHandler(deviceHandler) {
            @Override
            protected void beforeChannelActive(ChannelHandlerContext ctx) {
                beforeActiveCalled = true;
            }

            @Override
            protected void afterChannelActive(ChannelHandlerContext ctx) {
                afterActiveCalled = true;
            }

            @Override
            protected void beforeChannelInactive(ChannelHandlerContext ctx) {
                beforeInactiveCalled = true;
            }

            @Override
            protected void afterChannelInactive(ChannelHandlerContext ctx) {
                afterInactiveCalled = true;
            }

            @Override
            protected void cleanUpAfterExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                cleanUpCalled = true;
            }
        };

        channel.pipeline().addLast(handler);
    }

    @Test
    @DisplayName("channelActive: set CONNECTED, call before/after hooks")
    void testChannelActive() {
        assertThat(deviceHolder.getStatus()).isEqualTo(DeviceStatus.DISCONNECTED);

        channel.pipeline().fireChannelActive();

        assertThat(deviceHolder.getStatus()).isEqualTo(DeviceStatus.CONNECTED);
        assertThat(beforeActiveCalled).as("beforeChannelActive should be called").isTrue();
        assertThat(afterActiveCalled).as("afterChannelActive should be called").isTrue();
    }

    @Test
    @DisplayName("channelInactive with MANUALLY_CLOSE=true: call hooks, skip reconnect")
    void testChannelInactiveManuallyClosed() {
        channel.attr(TCPDeviceHandler.MANUALLY_CLOSE).set(true);
        DeviceStatus beforeInactive = deviceHolder.getStatus();

        channel.pipeline().fireChannelInactive();

        assertThat(beforeInactiveCalled).as("beforeChannelInactive should be called").isTrue();
        assertThat(afterInactiveCalled).as("afterChannelInactive should be called").isTrue();
        // Status should remain unchanged since reconnect is skipped
        assertThat(deviceHolder.getStatus()).isEqualTo(beforeInactive);
    }

    @Test
    @DisplayName("channelInactive with MANUALLY_CLOSE=false: call hooks")
    void testChannelInactiveAutoReconnect() {
        channel.pipeline().fireChannelInactive();

        assertThat(beforeInactiveCalled).as("beforeChannelInactive should be called").isTrue();
        assertThat(afterInactiveCalled).as("afterChannelInactive should be called").isTrue();
        // Note: reconnect is scheduled with RECONNECT_INTERVAL_MS delay,
        // so status change to CONNECTING happens after the delay on the event loop.
        // EmbeddedChannel does not advance clock, so we only verify hook calls.
    }

    @Test
    @DisplayName("exceptionCaught: call cleanUp hook, close channel")
    void testExceptionCaught() {
        assertThat(channel.isOpen()).isTrue();

        channel.pipeline().fireExceptionCaught(new RuntimeException("test error"));

        assertThat(cleanUpCalled).as("cleanUpAfterExceptionCaught should be called").isTrue();
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    @DisplayName("default template method implementations are no-ops")
    void testDefaultTemplateMethods() {
        // A handler that does NOT override template methods to verify defaults
        DeviceInitHandler defaultHandler = new DeviceInitHandler(deviceHandler) {};
        EmbeddedChannel defaultChannel = new EmbeddedChannel();
        defaultChannel.attr(TCPDeviceHandler.DEVICE_HOLDER).set(deviceHolder);
        defaultChannel.attr(TCPDeviceHandler.DEVICE_ID).set(TEST_DEVICE_ID);
        defaultChannel.pipeline().addLast(defaultHandler);

        // Should not throw
        defaultChannel.pipeline().fireChannelActive();
        assertThat(deviceHolder.getStatus()).isEqualTo(DeviceStatus.CONNECTED);

        defaultChannel.pipeline().fireChannelInactive();
        defaultChannel.pipeline().fireExceptionCaught(new RuntimeException("err"));
    }
}
