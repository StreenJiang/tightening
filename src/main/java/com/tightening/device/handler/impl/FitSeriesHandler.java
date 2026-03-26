package com.tightening.device.handler.impl;

import com.tightening.constant.fit.FitCommandType;
import com.tightening.constant.fit.FitConstants;
import com.tightening.netty.protocol.codec.FitFrameCodec;
import com.tightening.netty.protocol.fit.FitFrame;
import com.tightening.service.DeviceService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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
        return new ChannelInitializer<NioSocketChannel>() {
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
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        super.channelActive(ctx);

                        FitFrame fitFrame = new FitFrame(FitCommandType.ENABLE.getCode(),
                                                         new byte[] { 0x01 });
                        ctx.writeAndFlush(fitFrame);

                        FitFrame fitFrame2 = new FitFrame(FitCommandType.PARAMETER_SET.getCode(),
                                                          new byte[] { 0x01 });
                        ctx.writeAndFlush(fitFrame2);
                    }
                });
            }
        };
    }
}
