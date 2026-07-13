package com.tightening.netty.protocol.handler.sudongx7;

import com.tightening.device.handler.ToolHandler;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.netty.protocol.codec.sudongx7.SudongX7Frame;
import com.tightening.netty.protocol.util.sudongx7.SudongX7TighteningDataParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

@Slf4j
public class SudongX7InBoundHandler extends SimpleChannelInboundHandler<SudongX7Frame> {

    private final ToolHandler deviceHandler;

    public SudongX7InBoundHandler(ToolHandler deviceHandler) {
        this.deviceHandler = deviceHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SudongX7Frame frame) {
        long deviceId = ctx.channel().attr(DEVICE_ID).get();
        int cmd = frame.getCmd();

        if (SudongX7Frame.isToolRunning(cmd)) {
            log.debug("SudongX7 tool running: deviceId={}", deviceId);
            deviceHandler.addResultFuture(
                    deviceHandler.generateKey(cmd, deviceId), true);

        } else if (SudongX7Frame.isError(cmd)) {
            log.warn("SudongX7 error: deviceId={}", deviceId);
            deviceHandler.addResultFuture(
                    deviceHandler.generateKey(cmd, deviceId), false);

        } else if (SudongX7Frame.isPsetResponse(cmd)) {
            byte subCmd = frame.getData() != null && frame.getData().length > 0
                    ? frame.getData()[0] : 0;
            log.debug("SudongX7 PSet response: deviceId={}, subCmd={}", deviceId, subCmd);
            deviceHandler.addResultFuture(
                    deviceHandler.generateKey(cmd, deviceId), true);

        } else if (SudongX7Frame.isTighteningData(cmd)) {
            byte[] data = frame.getData() != null ? frame.getData() : new byte[0];
            TighteningDataDTO dto = SudongX7TighteningDataParser.parse(cmd, data);
            deviceHandler.handleTighteningData(dto, ctx.channel());

        } else {
            log.debug("SudongX7 unknown cmd=0x{}: deviceId={}", Integer.toHexString(cmd), deviceId);
        }
    }
}
