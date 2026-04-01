package com.tightening.device.handler.impl;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.TCPDeviceConstants;
import com.tightening.constant.ToolConstants;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.util.JsonUtils;
import com.tightening.device.type.TCPDevice;
import com.tightening.entity.Device;
import com.tightening.service.DeviceService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public abstract class TCPDeviceHandler implements DeviceHandler, Closeable {

    protected final TCPDeviceHandler deviceHandlerSelf;
    protected final Bootstrap bootstrap; // TODO: 后续再看定义的位置是否可以再往上层提
    protected final NioEventLoopGroup group;
    protected final DeviceService deviceService;
    protected final Map<Long, DeviceHolder> devices;
    protected final Map<String, CompletableFuture<Boolean>> rspFutures;

    public static final AttributeKey<Long> DEVICE_ID = AttributeKey.valueOf("deviceId");
    public static final AttributeKey<DeviceHolder> DEVICE_HOLDER = AttributeKey.valueOf("deviceHolder");
    public static final AttributeKey<Boolean> MANUALLY_CLOSE = AttributeKey.valueOf("manuallyClose");

    public TCPDeviceHandler(DeviceService deviceService) {
        deviceHandlerSelf = this;
        group = new NioEventLoopGroup();
        this.deviceService = deviceService;

        devices = new ConcurrentHashMap<>();
        rspFutures = new ConcurrentHashMap<>();
        bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(setupChannelInitializer());
    }

    protected abstract ChannelInitializer<NioSocketChannel> setupChannelInitializer();

    @Override
    public final void connect(long deviceId) {
        Device device;
        DeviceHolder deviceHolder;
        if (!devices.containsKey(deviceId)) {
            // 兜底处理，如果忘记先添加对应的 holder，就在这添加一次
            device = deviceService.getById(deviceId);
            deviceHolder = addDeviceInfo(device);
        } else {
            deviceHolder = devices.get(deviceId);
            device = deviceHolder.getDevice();
        }

        // 连接设备
        deviceHolder.setStatus(DeviceStatus.CONNECTING);
        connectToChannel(deviceId, device, deviceHolder);
    }

    protected void connectToChannel(long deviceId, Device device, DeviceHolder deviceHolder) {
        TCPDevice tcpDevice = JsonUtils.parse(device.getDetail(), TCPDevice.class);
        ChannelFuture channelFuture = bootstrap
                .connect(new InetSocketAddress(tcpDevice.getIp(), tcpDevice.getPort()));

        channelFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Channel channel = future.channel();

                // TODO: 思考：如果当前设备已经存过了，已存在的 channelFuture 应该怎么处理？
                if (deviceHolder.getChannel() != null) {
                    deviceHolder.getChannel().close();
                    deviceHolder.setChannel(null);
                }

                channel.attr(DEVICE_ID).set(deviceId);
                channel.attr(DEVICE_HOLDER).set(deviceHolder);
                channel.attr(MANUALLY_CLOSE).set(false);

                deviceHolder.setChannel(channel);
            } else {
                // TODO: 完善重连逻辑
                future.channel().eventLoop().schedule(() -> {
                    // TODO: need i18n
                    log.info("Reconnecting to server...");
                    connectToChannel(deviceId, device, deviceHolder);
                }, TCPDeviceConstants.RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
            }
        });
    }

    public void connectToChannel(long deviceId, DeviceHolder deviceHolder) {
        connectToChannel(deviceId, deviceHolder.getDevice(), deviceHolder);
    }

    @Override
    public void disconnect(long deviceId) {
        if (devices.containsKey(deviceId)) {
            DeviceHolder deviceHolder = devices.remove(deviceId);
            Channel channel = deviceHolder.getChannel();
            channel.attr(MANUALLY_CLOSE).set(true);
            try {
                channel.close().sync().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        // TODO: 这里添加清理资源的逻辑
                    }
                });
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void tryAddDeviceInfo(Device device) {
        if (!devices.containsKey(device.getId())) {
            addDeviceInfo(device);
        }
    }

    public DeviceHolder addDeviceInfo(Device device) {
        DeviceHolder deviceHolder = new DeviceHolder(device);
        devices.put(device.getId(), deviceHolder);
        return deviceHolder;
    }

    protected DeviceHolder getHolder(long deviceId) {
        DeviceHolder deviceHolder = devices.get(deviceId);
        if (deviceHolder == null) {
            throw new RuntimeException();
        }
        return deviceHolder;
    }

    /**
     * 生成唯一请求 Key
     *
     * @param cmdType  命令类型
     * @param deviceId 设备ID
     * @return 格式: "cmdType:deviceId"
     */
    public String generateKey(Object cmdType, long deviceId) {
        return cmdType + ":" + deviceId;
    }

    /**
     * 异步发送命令并等待响应（不阻塞当前线程）
     *
     * @param cmdSupplier 命令构造器
     * @param reqCmdStr   请求命令码信息，仅用作日志
     * @param key         用于给应答信息存入的对应的 future 的 key
     * @param deviceId    设备ID
     * @return CompletableFuture，成功时返回 true，失败或超时返回 false
     */
    protected <T> CompletableFuture<Boolean> sendCmdAsync(Supplier<T> cmdSupplier,
                                                          String reqCmdStr,
                                                          String key,
                                                          long deviceId) {
        DeviceHolder deviceHolder = getHolder(deviceId);
        T cmdFrame = cmdSupplier.get();
        ChannelFuture writeFuture = deviceHolder.getChannel().writeAndFlush(cmdFrame);
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        writeFuture.addListener(f -> {
            if (!f.isSuccess()) {
                // 写入失败，直接完成 false
                resultFuture.complete(false);
                log.debug("Write failed for command: {}", reqCmdStr);
                return;
            }

            // 写入成功，将 future 放入映射，等待响应处理器完成
            rspFutures.put(key, resultFuture);

            // 无论成功、失败、超时、异常，都会执行清理
            resultFuture.whenComplete((result, throwable) -> {
                rspFutures.remove(key);
                log.debug("Cleaned up future for key: {}, result: {}, error: {}",
                          key, result,
                          throwable != null ? throwable.getMessage() : "none");
            });

            // 设置超时任务，如果超时则完成 false 并移除
            deviceHolder.getChannel().eventLoop().schedule(() -> {
                if (!resultFuture.isDone()) {
                    resultFuture.complete(false);
                    log.debug("Timeout waiting for response: command={}, deviceId={}", reqCmdStr, deviceId);
                }
            }, ToolConstants.CMD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        });

        return resultFuture;
    }

    public void addResultFuture(String key, boolean result) {
        CompletableFuture<Boolean> future = rspFutures.get(key);
        if (future != null) {
            future.complete(result);
        }
    }

    @Override
    public DeviceStatus getStatus(long deviceId) {
        if (devices.containsKey(deviceId)) {
            return devices.get(deviceId).getStatus();
        }
        return DeviceStatus.NONE;
    }

    @Override
    public void close() throws IOException {
        group.shutdownGracefully();
    }
}
