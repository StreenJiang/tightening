package com.tightening.netty.protocol.handler.atlas;

import com.tightening.device.handler.ToolHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.netty.protocol.handler.DeviceInitHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

@Slf4j
public class AtlasSeriesInitHandler extends DeviceInitHandler {
    public AtlasSeriesInitHandler(TCPDeviceHandler deviceHandler) {
        super(deviceHandler);
    }

    @Override
    protected void afterChannelActive(ChannelHandlerContext ctx) {
        long deviceId = ctx.channel().attr(DEVICE_ID).get();
        ((ToolHandler) deviceHandler).forceDisableToolOp(deviceId);
    }
}
