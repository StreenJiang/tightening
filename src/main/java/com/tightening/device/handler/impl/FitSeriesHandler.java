package com.tightening.device.handler.impl;

import com.tightening.config.FitConfig;
import com.tightening.config.ToolCommonConfig;
import com.tightening.constant.fit.FitCommandType;
import com.tightening.constant.fit.FitConstants;
import com.tightening.device.handler.HeartbeatHandler;
import com.tightening.device.handler.ToolHandler;
import com.tightening.netty.protocol.handler.fit.FitSeriesInBoundHandler;
import com.tightening.netty.protocol.handler.fit.FitSeriesInitHandler;
import com.tightening.netty.protocol.codec.fit.FitFrameCodec;
import com.tightening.netty.protocol.codec.fit.FitFrame;
import com.tightening.service.DeviceService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FitSeriesHandler extends ToolHandler {

    private final FitConfig fitConfig;

    public FitSeriesHandler(DeviceService deviceService, FitConfig fitConfig,
                            ToolCommonConfig toolCommonConfig) {
        super(deviceService, toolCommonConfig);
        this.fitConfig = fitConfig;
    }

    @Override
    protected ChannelInitializer<NioSocketChannel> setupChannelInitializer() {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(NioSocketChannel ch) {
                log.debug("FIT series device channel initialized");
                ch.pipeline().addLast(
                        new LengthFieldBasedFrameDecoder(
                                ByteOrder.LITTLE_ENDIAN, Integer.MAX_VALUE,
                                FitConstants.LENGTH_FIELD_OFFSET,
                                FitConstants.LENGTH_FIELD_LENGTH,
                                FitConstants.LENGTH_ADJUSTMENT,
                                FitConstants.INIT_BYTES_TO_STRIP,
                                true
                        ));
                ch.pipeline().addLast(new FitFrameCodec());
                ch.pipeline().addLast(
                        new IdleStateHandler(0, fitConfig.getHeartBeatIntervalMs(), 0,
                                             TimeUnit.MILLISECONDS));
                ch.pipeline().addLast(new HeartbeatHandler(fitConfig.getHeartBeatRetryMax(),
                                                           deviceId -> sendHeartbeat(deviceId)));
                ch.pipeline().addLast(new FitSeriesInitHandler(deviceHandlerSelf));
                ch.pipeline().addLast(new FitSeriesInBoundHandler(deviceHandlerSelf));
            }
        };
    }

    @Override
    protected CompletableFuture<Boolean> enableTool(long deviceId) {
        return sendCmdAsync(FitFrame::enableTool,
                            FitCommandType.ENABLE_DISABLE.toString(),
                            generateKey(FitCommandType.ENABLE_DISABLE.getCode(), deviceId),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending enable to tool", ex);
                    return false;
                });
    }

    @Override
    protected CompletableFuture<Boolean> disableTool(long deviceId) {
        return sendCmdAsync(FitFrame::disableTool,
                            FitCommandType.ENABLE_DISABLE.toString(),
                            generateKey(FitCommandType.ENABLE_DISABLE.getCode(), deviceId),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending disable to tool", ex);
                    return false;
                });
    }

    @Override
    protected CompletableFuture<Boolean> sendPSetCmd(long deviceId, int pSet) {
        return sendCmdAsync(() -> FitFrame.sendPSet(pSet),
                            FitCommandType.PARAMETER_SET.toString(),
                            generateKey(FitCommandType.PARAMETER_SET.getCode(), deviceId),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending pSet={} to tool", pSet, ex);
                    return false;
                });
    }

    @Override
    public CompletableFuture<Boolean> sendHeartbeat(long deviceId) {
        return sendCmdAsync(FitFrame::sendHeartBeat,
                            FitCommandType.HEARTBEAT_REQ.toString(),
                            generateKey(FitCommandType.HEARTBEAT_ACK.getCode(), deviceId),
                            deviceId);
    }
}
