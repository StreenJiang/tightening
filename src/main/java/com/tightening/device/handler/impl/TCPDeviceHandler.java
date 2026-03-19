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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class TCPDeviceHandler implements DeviceHandler {

    private final Bootstrap bootstrap;
    private final Map<Long, DeviceHolder> devices;
    private final DeviceService deviceService;

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
        TCPDevice tcpDevice = JsonUtils.parse(device.getDetail(), TCPDevice.class);
        try {
            Channel channel = bootstrap
                    .connect(new InetSocketAddress(tcpDevice.getIp(), tcpDevice.getPort()))
                    .sync()
                    .channel();

            // TODO: 思考：如果当前设备已经存过了，已存在的 future 应该怎么处理？
            deviceHolder.setChannel(channel);
        } catch (InterruptedException e) {
            // TODO: 需处理
            throw new RuntimeException(e);
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
