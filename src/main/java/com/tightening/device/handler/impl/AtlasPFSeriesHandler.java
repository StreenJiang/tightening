package com.tightening.device.handler.impl;

import com.tightening.config.ToolCommonConfig;
import com.tightening.constant.atlas.AtlasCommandType;
import com.tightening.constant.atlas.AtlasConstants;
import com.tightening.device.handler.ToolHandler;
import com.tightening.netty.protocol.codec.atlas.AtlasFrame;
import com.tightening.netty.protocol.codec.atlas.AtlasLengthDecoder;
import com.tightening.netty.protocol.handler.atlas.AtlasPFSeriesInBoundHandler;
import com.tightening.netty.protocol.handler.atlas.AtlasPFSeriesInitHandler;
import com.tightening.service.DeviceService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class AtlasPFSeriesHandler extends ToolHandler {
    protected static final String CMD_CONNECT = "";
    protected static final String CMD_HEART_BEAT = "";
    protected static final String CMD_DATA_SUBSCRIBE = "";
    protected static final String CMD_CURVE_SUBSCRIBE = "";
    protected static final String CMD_CURVE_DATA_ACK = "";
    protected static final String CMD_LOCK = "";
    protected static final String CMD_UNLOCK = "";

    public AtlasPFSeriesHandler(DeviceService deviceService, ToolCommonConfig toolCommonConfig) {
        super(deviceService, toolCommonConfig);
    }

    @Override
    protected ChannelInitializer<NioSocketChannel> setupChannelInitializer() {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(NioSocketChannel ch) throws Exception {
                log.debug("PF series devices initialized...");
                ch.pipeline().addLast(new AtlasLengthDecoder(
                        Integer.MAX_VALUE,
                        AtlasConstants.LENGTH_FIELD_OFFSET,
                        AtlasConstants.LENGTH_FIELD_LENGTH,
                        AtlasConstants.LENGTH_ADJUSTMENT,
                        AtlasConstants.INIT_BYTES_TO_STRIP));
                ch.pipeline().addLast(new AtlasPFSeriesInitHandler(deviceHandlerSelf));
                ch.pipeline().addLast(new AtlasPFSeriesInBoundHandler(deviceHandlerSelf));
            }
        };
    }

    public CompletableFuture<Boolean> connectToController(long deviceId) {
        return sendCmdAsync(AtlasFrame::connectTool,
                            AtlasCommandType.CONNECT.toString(),
                            generateKey(AtlasCommandType.CONNECT_ACK, deviceId),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending connect to tool", ex);
                    return false;
                });
    }

    public CompletableFuture<Boolean> subscribeTighteningData(long deviceId) {
        return sendCmdAsync(AtlasFrame::subscribeTighteningData,
                            AtlasCommandType.SUBSCRIBE_DATA.toString(),
                            generateKey(AtlasCommandType.SUBSCRIBE_DATA, deviceId),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending connect to tool", ex);
                    return false;
                });
    }

    @Override
    protected CompletableFuture<Boolean> enableTool(long deviceId) {
        return sendCmdAsync(AtlasFrame::enableTool,
                            AtlasCommandType.ENABLE.toString(),
                            generateKey(AtlasCommandType.ENABLE, deviceId),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending enable to tool", ex);
                    return false;
                });
    }

    @Override
    protected CompletableFuture<Boolean> disableTool(long deviceId) {
        return sendCmdAsync(AtlasFrame::disableTool,
                            AtlasCommandType.DISABLE.toString(),
                            generateKey(AtlasCommandType.DISABLE, deviceId),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending disable to tool", ex);
                    return false;
                });
    }

    @Override
    protected CompletableFuture<Boolean> sendPSetCmd(long deviceId, int pSet) {
        return sendCmdAsync(() -> AtlasFrame.sendPSet(pSet),
                            AtlasCommandType.PARAMETER_SET.toString(),
                            generateKey(AtlasCommandType.PARAMETER_SET.getMid(), deviceId),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending pSet={} to tool", pSet, ex);
                    return false;
                });
    }

    @Override
    public CompletableFuture<Boolean> sendHeartbeat(long deviceId) {
        return sendCmdAsync(AtlasFrame::sendHeartBeat,
                            AtlasCommandType.HEARTBEAT.toString(),
                            generateKey(AtlasCommandType.HEARTBEAT.getMid(), deviceId),
                            deviceId);
    }
}
