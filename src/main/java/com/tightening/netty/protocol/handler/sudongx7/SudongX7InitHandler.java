package com.tightening.netty.protocol.handler.sudongx7;

import com.tightening.device.handler.ToolHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.netty.protocol.handler.DeviceInitHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SudongX7InitHandler extends DeviceInitHandler {

    public SudongX7InitHandler(ToolHandler handler) {
        super(handler);
    }

    @Override
    protected void afterChannelActive(ChannelHandlerContext ctx) {
        ToolHandler toolHandler = (ToolHandler) deviceHandler;
        Long deviceId = ctx.channel().attr(TCPDeviceHandler.DEVICE_ID).get();
        toolHandler.forceLock(deviceId).whenComplete((ok, ex) -> {
            if (ex != null || !Boolean.TRUE.equals(ok)) {
                log.warn("SudongX7 forceLock failed after connect: deviceId={}", deviceId, ex);
            } else {
                log.info("SudongX7 forceLock succeeded: deviceId={}", deviceId);
            }
        });
    }
}
