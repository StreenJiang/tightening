package com.tightening.entity.handler;

import com.tightening.constant.DeviceStatus;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_HOLDER;
import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

@Slf4j
public abstract class DeviceInitHandler extends ChannelInboundHandlerAdapter {
    private final TCPDeviceHandler deviceHandler;

    public DeviceInitHandler(TCPDeviceHandler deviceHandler) {
        this.deviceHandler = deviceHandler;
    }

    @Override
    public final void channelActive(ChannelHandlerContext ctx) throws Exception {
        // TODO: need i18n
        log.info("Connected to server...");

        // Actions before active settings
        beforeChannelActive(ctx);

        DeviceHolder deviceHolder = ctx.channel().attr(DEVICE_HOLDER).get();
        deviceHolder.setStatus(DeviceStatus.CONNECTED);
        super.channelActive(ctx);

        // Actions after active settings
        afterChannelActive(ctx);
    }

    protected void beforeChannelActive(ChannelHandlerContext ctx) { }

    protected void afterChannelActive(ChannelHandlerContext ctx) { }

    @Override
    public final void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // TODO: need i18n
        log.info("Disconnected from server, attempt to reconnect...");

        beforeChannelInactive(ctx);

        Long deviceId = ctx.channel().attr(DEVICE_ID).get();
        DeviceHolder deviceHolder = ctx.channel().attr(DEVICE_HOLDER).get();
        deviceHolder.setStatus(DeviceStatus.CONNECTING);

        ctx.channel().eventLoop().schedule(() -> {
            // TODO: need i18n
            log.info("Reconnecting to server...");
            deviceHandler.connectToChannel(deviceId, deviceHolder);
        }, 3000, TimeUnit.MILLISECONDS);

        super.channelInactive(ctx);

        afterChannelInactive(ctx);
    }

    protected void beforeChannelInactive(ChannelHandlerContext ctx) { }

    protected void afterChannelInactive(ChannelHandlerContext ctx) { }

    @Override
    public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);

        cleanUpAfterExceptionCaught(ctx, cause);

        // 捕获到异常，关闭连接，触发 channelInactive
        ctx.close();
    }

    protected void cleanUpAfterExceptionCaught(ChannelHandlerContext ctx, Throwable cause) { }
}
