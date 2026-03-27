package com.tightening.device.handler.impl;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.TCPCommand;
import com.tightening.device.DeviceHolder;
import com.tightening.entity.handler.atlas.AtlasSeriesInitHandler;
import com.tightening.service.DeviceService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;

public class AtlasPFSeriesHandler extends TCPDeviceHandler {
    protected static final String CMD_CONNECT = "";
    protected static final String CMD_HEART_BEAT = "";
    protected static final String CMD_DATA_SUBSCRIBE = "";
    protected static final String CMD_CURVE_SUBSCRIBE = "";
    protected static final String CMD_CURVE_DATA_ACK = "";
    protected static final String CMD_LOCK = "";
    protected static final String CMD_UNLOCK = "";

    public AtlasPFSeriesHandler(DeviceService deviceService) {
        super(deviceService);
    }

    @Override
    protected ChannelInitializer<NioSocketChannel> setupChannelInitializer() {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(NioSocketChannel ch) throws Exception {
                System.out.println("PF series devices initialized...");

                // Init
                ch.pipeline().addLast(new AtlasSeriesInitHandler(self));
            }
        };
    }

    @Override
    public boolean sendCommand(long deviceId, TCPCommand cmd) {
        DeviceHolder deviceHolder = devices.get(deviceId);
        switch (cmd) {
            case TOOL_ENABLE:
                break;
            case TOOL_DISABLE:
                break;
            case TOOL_PARAMETER_SET:
                break;
        }
        return false;
    }
}
