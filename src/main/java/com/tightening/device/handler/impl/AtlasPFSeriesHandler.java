package com.tightening.device.handler.impl;

import com.tightening.constant.DeviceStatus;
import com.tightening.device.DeviceHolder;
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
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        DeviceHolder deviceHolder = ctx.channel().attr(DEVICE_HOLDER).get();
                        deviceHolder.setStatus(DeviceStatus.CONNECTED);

                        // TODO: actions after active

                        super.channelActive(ctx);
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        System.out.println("连接断开，准备进行重连...");

                        Long deviceId = ctx.channel().attr(DEVICE_ID).get();
                        DeviceHolder deviceHolder = ctx.channel().attr(DEVICE_HOLDER).get();
                        connectToChannel(deviceId, deviceHolder);

                        super.channelInactive(ctx);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        // 捕获到异常，关闭连接，触发 channelInactive
                        // TODO: 完善是否需要处理 cause
                        ctx.close();
                    }
                });
            }
        };
    }
}
