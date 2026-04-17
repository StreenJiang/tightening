package com.tightening.netty.protocol.handler.atlas;

import com.tightening.constant.atlas.AtlasCommandType;
import com.tightening.device.handler.impl.AtlasPFSeriesHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.netty.protocol.handler.DeviceInitHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
                        String key = getErrKey(deviceId);
                        CompletableFuture<String> errorMsgFuture = deviceHandler.getErrorMsgFuture(key);
                        if (errorMsgFuture != null) {
                            errorMsgFuture.thenAccept(msg -> log.warn(
                                    "Connect failed, deviceId={}, reason={}",
                                    deviceId, msg)
                            );
                        } else {
                            log.warn("Connect failed, deviceId={}", deviceId);
                        }
                        return CompletableFuture.failedFuture(new IllegalStateException("Connect failed"));
                    }
                    return handler.subscribeTighteningData(deviceId); // 成功则执行订阅
                })
                // 检查订阅结果，失败同样短路
                .thenCompose(result -> {
                    if (result == null || !result) {
                        String key = getErrKey(deviceId);
                        CompletableFuture<String> errorMsgFuture = deviceHandler.getErrorMsgFuture(key);
                        if (errorMsgFuture != null) {
                            errorMsgFuture.thenAccept(msg -> log.warn(
                                    "Subscribe failed, deviceId={}, reason={}",
                                    deviceId, msg)
                            );
                        } else {
                            log.warn("Subscribe failed, deviceId={}", deviceId);
                        }
                        return CompletableFuture.failedFuture(new IllegalStateException("Subscribe failed"));
                    }
                    return CompletableFuture.completedFuture(null);
                })
                // 全部成功，执行最终操作
                .thenCompose(ignored -> handler.forceDisableToolOp(deviceId))
                .thenAccept(disableResult -> {
                    if (disableResult == null || !disableResult) {
                        // 非关键失败，仅记录
                        String key = getErrKey(deviceId);
                        CompletableFuture<String> errorMsgFuture = deviceHandler.getErrorMsgFuture(key);
                        if (errorMsgFuture != null) {
                            errorMsgFuture.thenAccept(msg -> log.warn(
                                    "Force disable failed, deviceId={}, reason={}",
                                    deviceId, msg)
                            );
                        } else {
                            log.warn("Force disable failed, deviceId={}, no detail", deviceId);
                        }
                    }
                })
                // 统一收尾：捕获短路异常或网络异常，关闭连接
                .exceptionally(throwable -> {
                    log.error("Controller connection pipeline failed, deviceId={}, reason={}",
                              deviceId, throwable.getMessage());
                    ctx.close();
                    return null;
                });
    }

    private String getErrKey(long deviceId) {
        return deviceHandler.generateKey(AtlasCommandType.NEGATIVE_ACK.getMid(), deviceId);
    }
}
