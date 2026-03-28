package com.tightening.device.handler.impl;

import com.tightening.constant.fit.FitCommandType;
import com.tightening.constant.fit.FitConstants;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.ToolHandler;
import com.tightening.entity.handler.fit.FitSeriesInBoundHandler;
import com.tightening.entity.handler.fit.FitSeriesInitHandler;
import com.tightening.netty.protocol.codec.FitFrameCodec;
import com.tightening.netty.protocol.fit.FitFrame;
import com.tightening.service.DeviceService;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public class FitSeriesHandler extends ToolHandler {

    public FitSeriesHandler(DeviceService deviceService) {
        super(deviceService);
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
                ch.pipeline().addLast(new FitSeriesInitHandler(deviceHandlerSelf));
                ch.pipeline().addLast(new FitSeriesInBoundHandler(deviceHandlerSelf));
            }
        };
    }

    @Override
    protected CompletableFuture<Boolean> enableTool(long deviceId) {
        return sendCmdAsync(FitFrame::enableTool,
                            FitCommandType.ENABLE_DISABLE.getCode(),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending enable to tool", ex);
                    return false;
                });
    }

    @Override
    protected CompletableFuture<Boolean> disableTool(long deviceId) {
        return sendCmdAsync(FitFrame::disableTool,
                            FitCommandType.ENABLE_DISABLE.getCode(),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending disable to tool", ex);
                    return false;
                });
    }

    @Override
    protected CompletableFuture<Boolean> sendPSetCmd(long deviceId, int pSet) {
        return sendCmdAsync(() -> FitFrame.sendPSet(pSet),
                            FitCommandType.PARAMETER_SET.getCode(),
                            deviceId)
                .exceptionally(ex -> {
                    log.warn("Error while sending pSet={} to tool", pSet, ex);
                    return false;
                });
    }

    /**
     * 异步发送命令并等待响应（不阻塞当前线程）
     *
     * @param cmdSupplier 命令构造器
     * @param respCmd     期望的响应命令码
     * @param deviceId    设备ID
     * @return CompletableFuture，成功时返回 true，失败或超时返回 false
     */
    private CompletableFuture<Boolean> sendCmdAsync(Supplier<FitFrame> cmdSupplier,
                                                    byte respCmd,
                                                    long deviceId) {
        DeviceHolder deviceHolder = getHolder(deviceId);
        FitFrame fitCmdFrame = cmdSupplier.get();
        ChannelFuture writeFuture = deviceHolder.getChannel().writeAndFlush(fitCmdFrame);
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        writeFuture.addListener(f -> {
            if (!f.isSuccess()) {
                // 写入失败，直接完成 false
                resultFuture.complete(false);
                log.debug("Write failed for command: {}", fitCmdFrame.getCmdType());
                return;
            }

            // 写入成功，将 future 放入映射，等待响应处理器完成
            String key = generateKey(respCmd, deviceId);
            rspFutures.put(key, resultFuture);

            // 设置超时任务，如果超时则完成 false 并移除
            deviceHolder.getChannel().eventLoop().schedule(() -> {
                if (!resultFuture.isDone()) {
                    resultFuture.complete(false);
                    rspFutures.remove(key);
                    log.debug("Timeout waiting for response: command={}, deviceId={}", respCmd, deviceId);
                }
            }, COMMAND_TIMEOUT, TimeUnit.MILLISECONDS);
        });

        return resultFuture;
    }
}
