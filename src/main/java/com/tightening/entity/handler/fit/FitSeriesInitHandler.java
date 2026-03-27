package com.tightening.entity.handler.fit;

import com.tightening.constant.fit.FitCommandType;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.entity.handler.DeviceInitHandler;
import com.tightening.netty.protocol.fit.FitFrame;
import io.netty.channel.ChannelHandlerContext;

public class FitSeriesInitHandler extends DeviceInitHandler {
    public FitSeriesInitHandler(TCPDeviceHandler deviceHandler) {
        super(deviceHandler);
    }

    @Override
    protected void afterChannelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(new FitFrame(FitCommandType.PARAMETER_SET.getCode(), new byte[] { 0x01 }));
        ctx.writeAndFlush(new FitFrame(FitCommandType.ENABLE.getCode(), new byte[] { 0x01 }));
    }
}
