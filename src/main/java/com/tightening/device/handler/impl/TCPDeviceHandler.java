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

    protected final TCPDeviceHandler self;
    protected final Bootstrap bootstrap; // TODO: 后续再看定义的位置是否可以再往上层提
    protected final NioEventLoopGroup group;
    protected final DeviceService deviceService;
    protected final Map<Long, DeviceHolder> devices;
    protected final Map<String, CompletableFuture<Boolean>> rspFutures;
    protected final Map<String, CompletableFuture<String>> errorMsgFutures;

    public static final AttributeKey<Long> DEVICE_ID = AttributeKey.valueOf("deviceId");
    public static final AttributeKey<DeviceHolder> DEVICE_HOLDER = AttributeKey.valueOf("deviceHolder");
    public static final AttributeKey<Boolean> MANUALLY_CLOSE = AttributeKey.valueOf("manuallyClose");

    public TCPDeviceHandler(DeviceService deviceService) {
        self = this;
        group = new NioEventLoopGroup();
        this.deviceService = deviceService;

        devices = new ConcurrentHashMap<>();
        rspFutures = new ConcurrentHashMap<>();
        errorMsgFutures = new ConcurrentHashMap<>();
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
     * 向后兼容原有调用方
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
        // 默认需要响应，复用新方法
        return sendCmdAsync(cmdSupplier, reqCmdStr, key, deviceId, true);
    }

    /**
     * 异步发送命令（可配置是否需要等待响应）
     *
     * @param cmdSupplier 命令构造器
     * @param reqCmdStr   请求命令码信息，仅用作日志
     * @param key         用于给应答信息存入的对应的 future 的 key（needRsp=false 时可传 null）
     * @param deviceId    设备ID
     * @param needRsp     是否需要等待响应
     * @return CompletableFuture，成功时返回 true，失败或超时返回 false
     */
    private <T> CompletableFuture<Boolean> sendCmdAsync(Supplier<T> cmdSupplier,
                                                        String reqCmdStr,
                                                        String key,
                                                        long deviceId,
                                                        boolean needRsp) {
        DeviceHolder deviceHolder = getHolder(deviceId);
        Channel channel = deviceHolder.getChannel();
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        ChannelFuture writeFuture = channel.writeAndFlush(cmdSupplier.get());
        writeFuture.addListener(f -> {
            // 1. 写入失败：直接完成 false
            if (!f.isSuccess()) {
                resultFuture.complete(false);
                log.debug("Write failed for command: {}, deviceId={}", reqCmdStr, deviceId);
                return;
            }

            // 2. 写入成功，但不需要响应：直接完成 true，无需注册 future
            if (!needRsp) {
                resultFuture.complete(true);
                log.debug("Command sent (fire-and-forget): {}, deviceId={}", reqCmdStr, deviceId);
                return;
            }

            // 3. 需要响应：注册 future 并设置超时
            // 注意：key 不应为 null，调用方需保证
            rspFutures.put(key, resultFuture);

            // 清理逻辑：无论成功/失败/超时/异常，都移除 future，防止内存泄漏
            resultFuture.whenComplete((result, throwable) -> {
                rspFutures.remove(key);
                if (log.isDebugEnabled()) {
                    log.debug("Cleaned up future for key: {}, result: {}, error: {}",
                              key, result,
                              throwable != null ? throwable.getMessage() : "none");
                }
            });

            // 超时任务：使用 channel.eventLoop() 确保线程安全
            channel.eventLoop().schedule(() -> {
                if (!resultFuture.isDone()) {
                    resultFuture.complete(false);
                    log.warn("Timeout waiting for response: command={}, deviceId={}", reqCmdStr, deviceId);
                    // 超时后断开连接，防止迟来的响应干扰后续命令（根据业务需求可选）
                    channel.close();
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

    public void addErrorMsgFuture(String key, String errorMsg) {
        errorMsgFutures.put(key, CompletableFuture.completedFuture(errorMsg));
    }

    public CompletableFuture<String> getErrorMsgFuture(String key) {
        return errorMsgFutures.get(key);
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
