package com.tightening.device.handler.impl;

import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.ToolHandler;
import com.tightening.entity.handler.atlas.AtlasSeriesInitHandler;
import com.tightening.service.DeviceService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.CompletableFuture;

public class AtlasPFSeriesHandler extends ToolHandler {
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
                ch.pipeline().addLast(new AtlasSeriesInitHandler(deviceHandlerSelf));
            }
        };
    }

    @Override
    protected CompletableFuture<Boolean> enableTool(long deviceId) {
        DeviceHolder deviceHolder = getHolder(deviceId);
        return new CompletableFuture<>();
    }

    @Override
    protected CompletableFuture<Boolean> disableTool(long deviceId) {
        DeviceHolder deviceHolder = getHolder(deviceId);
        return new CompletableFuture<>();
    }

    @Override
    protected CompletableFuture<Boolean> sendPSetCmd(long deviceId, int pSet) {
        DeviceHolder deviceHolder = getHolder(deviceId);
        return new CompletableFuture<>();
    }
}
