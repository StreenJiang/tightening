package com.tightening.device.handler.impl;

import com.tightening.config.DeviceConfig;
import com.tightening.config.ToolCommonConfig;
import com.tightening.constant.DeviceType;
import com.tightening.constant.atlas.AtlasCommandType;
import com.tightening.constant.atlas.AtlasConstants;
import com.tightening.device.handler.ToolHandler;
import com.tightening.netty.protocol.codec.atlas.AtlasFrame;
import com.tightening.netty.protocol.codec.atlas.AtlasLengthDecoder;
import com.tightening.netty.protocol.handler.atlas.AtlasPFSeriesInBoundHandler;
import com.tightening.netty.protocol.handler.atlas.AtlasPFSeriesInitHandler;
import com.tightening.service.CurveDataService;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AtlasPFSeriesHandler extends ToolHandler {

    public AtlasPFSeriesHandler(NioEventLoopGroup group,
                                DeviceService deviceService,
                                TighteningDataService tighteningDataService,
                                CurveDataService curveDataService,
                                ToolCommonConfig toolCommonConfig,
                                DeviceConfig deviceConfig) {
        super(group, deviceService, tighteningDataService, curveDataService, toolCommonConfig, deviceConfig);
    }

    @Override
    public Set<DeviceType> getSupportedTypes() {
        return EnumSet.of(DeviceType.ATLAS_PF4000, DeviceType.ATLAS_PF6000_OP);
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
                ch.pipeline().addLast(new AtlasPFSeriesInitHandler(self));
                ch.pipeline().addLast(new AtlasPFSeriesInBoundHandler((ToolHandler) self));
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
    protected CompletableFuture<Boolean> unlockTool(long deviceId) {
        return sendCmdAsync(AtlasFrame::unlockTool,
                            AtlasCommandType.ENABLE.toString(),
                            generateKey(AtlasCommandType.ENABLE.getMid(), deviceId),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending unlock to tool", ex);
                    return false;
                });
    }

    @Override
    protected CompletableFuture<Boolean> lockTool(long deviceId) {
        return sendCmdAsync(AtlasFrame::lockTool,
                            AtlasCommandType.DISABLE.toString(),
                            generateKey(AtlasCommandType.DISABLE.getMid(), deviceId),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending lock to tool", ex);
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
