package com.tightening.device.handler.impl;

import com.tightening.constant.DeviceStatus;
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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class TCPDeviceHandler implements DeviceHandler {

    protected final Bootstrap bootstrap; // TODO: 后续再看定义的位置是否可以再往上层提
    protected final DeviceService deviceService;
    protected final Map<Long, DeviceHolder> devices;

    protected final AttributeKey<Long> DEVICE_ID = AttributeKey.valueOf("deviceId");
    protected final AttributeKey<DeviceHolder> DEVICE_HOLDER = AttributeKey.valueOf("deviceHolder");

    public TCPDeviceHandler(DeviceService deviceService) {
        this.deviceService = deviceService;

        devices = new ConcurrentHashMap<>();
        bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup())
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
                }

                channel.attr(DEVICE_ID).set(deviceId);
                channel.attr(DEVICE_HOLDER).set(deviceHolder);

                deviceHolder.setChannel(channel);
            } else {
                // TODO: 完善重连逻辑
                future.channel().eventLoop().schedule(() -> {
                    System.err.println("重连服务端...");
                    connectToChannel(deviceId, device, deviceHolder);
                }, 3000, TimeUnit.MILLISECONDS);
            }
        });
    }

    protected void connectToChannel(long deviceId, DeviceHolder deviceHolder) {
        connectToChannel(deviceId, deviceHolder.getDevice(), deviceHolder);
    }

    @Override
    public void disconnect(long deviceId) {
        if (devices.containsKey(deviceId)) {
            DeviceHolder deviceHolder = devices.remove(deviceId);
            Channel channel = deviceHolder.getChannel();
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

    @Override
    public DeviceStatus getStatus(long deviceId) {
        if (devices.containsKey(deviceId)) {
            return devices.get(deviceId).getStatus();
        }
        return DeviceStatus.NONE;
    }
}
