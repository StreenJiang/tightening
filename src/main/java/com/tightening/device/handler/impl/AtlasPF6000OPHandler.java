package com.tightening.device.handler.impl;

import com.tightening.constant.DeviceStatus;
import com.tightening.service.DeviceService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.springframework.stereotype.Component;

@Component
public class AtlasPF6000OPHandler extends TCPDeviceHandler {

    public AtlasPF6000OPHandler(DeviceService deviceService) {
        super(deviceService);
    }

    @Override
    public void disconnect(long deviceId) {

    }

    @Override
    protected ChannelInitializer<NioSocketChannel> setupChannelInitializer() {
        return new ChannelInitializer<>() {

            @Override
            protected void initChannel(NioSocketChannel ch) throws Exception {
                System.out.println("PF6000OP initialized...");

                // Init
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        // Init logic
                        super.channelActive(ctx);
                    }
                });
            }
        };
    }
}
