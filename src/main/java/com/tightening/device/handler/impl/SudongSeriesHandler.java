package com.tightening.device.handler.impl;

import com.tightening.config.DeviceConfig;
import com.tightening.config.ToolCommonConfig;
import com.tightening.constant.DeviceType;
import com.tightening.device.handler.ToolHandler;
import com.tightening.netty.protocol.codec.sudongx7.SudongX7FrameCodec;
import com.tightening.netty.protocol.codec.sudongx7.SudongX7FrameDecoder;
import com.tightening.netty.protocol.handler.sudongx7.SudongX7InBoundHandler;
import com.tightening.netty.protocol.handler.sudongx7.SudongX7InitHandler;
import com.tightening.service.CurveDataService;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.context.ApplicationEventPublisher;

public abstract class SudongSeriesHandler extends ToolHandler {

    public SudongSeriesHandler(NioEventLoopGroup group,
                                DeviceService deviceService,
                                TighteningDataService tighteningDataService,
                                CurveDataService curveDataService,
                                ToolCommonConfig toolCommonConfig,
                                DeviceConfig deviceConfig,
                                ApplicationEventPublisher eventPublisher) {
        super(group, deviceService, tighteningDataService, curveDataService, toolCommonConfig, deviceConfig, eventPublisher);
    }

    @Override
    public Set<DeviceType> getSupportedTypes() {
        return Collections.emptySet();
    }

    @Override
    protected ChannelInitializer<NioSocketChannel> setupChannelInitializer() {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(NioSocketChannel ch) {
                ch.pipeline().addLast(new SudongX7FrameDecoder());
                ch.pipeline().addLast(new SudongX7FrameCodec());
                ch.pipeline().addLast(new SudongX7InitHandler(self()));
                ch.pipeline().addLast(new SudongX7InBoundHandler(self()));
            }
        };
    }

    @Override
    protected CompletableFuture<Boolean> unlockTool(long deviceId) {
        throw new UnsupportedOperationException("unlockTool must be overridden by subclass");
    }

    @Override
    protected CompletableFuture<Boolean> lockTool(long deviceId) {
        throw new UnsupportedOperationException("lockTool must be overridden by subclass");
    }

    @Override
    public CompletableFuture<Boolean> sendHeartbeat(long deviceId) {
        return CompletableFuture.completedFuture(false);
    }

    protected ToolHandler self() {
        return this;
    }
}
