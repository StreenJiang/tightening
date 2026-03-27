package com.tightening.device.handler.impl;

import com.tightening.constant.TCPCommand;
import com.tightening.constant.fit.FitCommandType;
import com.tightening.constant.fit.FitConstants;
import com.tightening.device.DeviceHolder;
import com.tightening.entity.Device;
import com.tightening.entity.handler.fit.FitSeriesInBoundHandler;
import com.tightening.entity.handler.fit.FitSeriesInitHandler;
import com.tightening.netty.protocol.codec.FitFrameCodec;
import com.tightening.netty.protocol.fit.FitFrame;
import com.tightening.service.DeviceService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteOrder;

@Slf4j
public class FitSeriesHandler extends TCPDeviceHandler {
    public FitSeriesHandler(DeviceService deviceService) {
        super(deviceService);
    }

    @Override
    protected ChannelInitializer<NioSocketChannel> setupChannelInitializer() {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(NioSocketChannel ch) throws Exception {
                System.out.println("FIT series devices initialized...");

                ch.pipeline().addLast(
                        new LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN, Integer.MAX_VALUE,
                                                         FitConstants.LENGTH_FIELD_OFFSET,
                                                         FitConstants.LENGTH_FIELD_LENGTH,
                                                         FitConstants.LENGTH_ADJUSTMENT,
                                                         FitConstants.INIT_BYTES_TO_STRIP,
                                                         true
                        ));
                ch.pipeline().addLast(new FitFrameCodec());
                ch.pipeline().addLast(new FitSeriesInitHandler(self));
                ch.pipeline().addLast(new FitSeriesInBoundHandler());
            }
        };
    }

    @Override
    public boolean sendCommand(long deviceId, TCPCommand cmd) {
        DeviceHolder deviceHolder = devices.get(deviceId);
        if (deviceHolder == null) {
            throw new RuntimeException();
        }

        switch (cmd) {
            case TOOL_ENABLE:
                deviceHolder.getChannel().writeAndFlush(
                        new FitFrame(FitCommandType.ENABLE.getCode(), new byte[] { 0x01 }));
                break;
            case TOOL_DISABLE:
                deviceHolder.getChannel().writeAndFlush(
                        new FitFrame(FitCommandType.ENABLE.getCode(), new byte[] { 0x00 }));
                break;
            case TOOL_PARAMETER_SET:
                deviceHolder.getChannel().writeAndFlush(
                        new FitFrame(FitCommandType.PARAMETER_SET.getCode(), new byte[] { 0x00 }));
                break;
        }
        return false;
    }
}
