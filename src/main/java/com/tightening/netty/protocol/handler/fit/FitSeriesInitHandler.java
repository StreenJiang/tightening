package com.tightening.netty.protocol.handler.fit;

import com.tightening.device.handler.ToolHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.netty.protocol.handler.DeviceInitHandler;
import io.netty.channel.ChannelHandlerContext;

import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

public class FitSeriesInitHandler extends DeviceInitHandler {
    public FitSeriesInitHandler(TCPDeviceHandler deviceHandler) {
        super(deviceHandler);
    }

    @Override
    protected void afterChannelActive(ChannelHandlerContext ctx) {
        long deviceId = ctx.channel().attr(DEVICE_ID).get();
        ((ToolHandler) deviceHandler).forceDisableToolOp(deviceId);
    }
}
