package com.tightening.netty.protocol.handler.atlas;

import com.tightening.device.handler.impl.AtlasPFSeriesHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.netty.protocol.handler.DeviceInitHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

@Slf4j
public class AtlasPFSeriesInitHandler extends DeviceInitHandler {
    public AtlasPFSeriesInitHandler(TCPDeviceHandler deviceHandler) {
        super(deviceHandler);
    }

    @Override
    protected void afterChannelActive(ChannelHandlerContext ctx) {
        long deviceId = ctx.channel().attr(DEVICE_ID).get();

        AtlasPFSeriesHandler handler = (AtlasPFSeriesHandler) deviceHandler;
        handler.connectToController(deviceId)
                // 检查连接结果，失败则转换为异常，触发短路
                .thenCompose(result -> {
                    if (result == null || !result) {
                        log.warn("Connect returned false, deviceId={}", deviceId);
                        return CompletableFuture.failedFuture(new IllegalStateException("Connect failed"));
                    }
                    return handler.subscribeTighteningData(deviceId); // 成功则执行订阅
                })
                // 检查订阅结果，失败同样短路
                .thenCompose(result -> {
                    if (result == null || !result) {
                        log.warn("Subscribe returned false, deviceId={}", deviceId);
                        return CompletableFuture.failedFuture(new IllegalStateException("Subscribe failed"));
                    }
                    return CompletableFuture.completedFuture(null);
                })
                // 全部成功，执行最终操作
                .thenRun(() -> handler.forceDisableToolOp(deviceId))
                // 统一收尾：捕获短路异常或网络异常，关闭连接
                .exceptionally(throwable -> {
                    log.error("Controller connection pipeline failed, deviceId={}, reason={}",
                              deviceId, throwable.getMessage());
                    ctx.close();
                    return null;
                });
    }
}
