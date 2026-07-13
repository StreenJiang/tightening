package com.tightening.device.handler.impl;

import com.tightening.config.ToolCommonConfig;
import com.tightening.constant.DeviceType;
import com.tightening.netty.protocol.codec.sudongx7.SudongX7Frame;
import com.tightening.service.CurveDataService;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SudongX7Handler extends SudongSeriesHandler {

    public SudongX7Handler(NioEventLoopGroup group,
                            DeviceService deviceService,
                            TighteningDataService tighteningDataService,
                            CurveDataService curveDataService,
                            ToolCommonConfig toolCommonConfig) {
        super(group, deviceService, tighteningDataService, curveDataService, toolCommonConfig);
    }

    @Override
    public Set<DeviceType> getSupportedTypes() {
        return EnumSet.of(DeviceType.SUDONG_X7);
    }

    @Override
    protected CompletableFuture<Boolean> unlockTool(long deviceId) {
        dualSend(deviceId, SudongX7Frame.unlock());
        return CompletableFuture.completedFuture(true);
    }

    @Override
    protected CompletableFuture<Boolean> lockTool(long deviceId) {
        dualSend(deviceId, SudongX7Frame.lock());
        return CompletableFuture.completedFuture(true);
    }

    @Override
    protected CompletableFuture<Boolean> sendPSetCmd(long deviceId, int pSet) {
        return sendCmdAsync(() -> SudongX7Frame.sendPSet(pSet),
                            "PSET:" + pSet,
                            generateKey(0x8205, deviceId),
                            deviceId);
    }

    private void dualSend(long deviceId, byte[] frame) {
        Channel channel = getHolder(deviceId).getChannel();
        if (channel == null || !channel.isActive()) return;
        channel.writeAndFlush(Unpooled.wrappedBuffer(frame));
        channel.eventLoop().schedule(() -> {
            if (channel.isActive()) {
                channel.writeAndFlush(Unpooled.wrappedBuffer(frame));
            }
        }, 200, TimeUnit.MILLISECONDS);
    }
}
