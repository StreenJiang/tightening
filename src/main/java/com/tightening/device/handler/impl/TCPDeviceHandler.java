package com.tightening.device.handler.impl;

import com.tightening.constant.DeviceStatus;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.util.JsonUtils;
import com.tightening.device.type.TCPDevice;
import com.tightening.entity.Device;
import com.tightening.service.DeviceService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class TCPDeviceHandler implements DeviceHandler {

    private final Bootstrap bootstrap;
    private ConcurrentHashMap<Long, DeviceHolder> devices;
    private ConcurrentHashMap<Long, ChannelFuture> channelFutures;
    private final DeviceService deviceService;

    public TCPDeviceHandler(DeviceService deviceService) {
        this.deviceService = deviceService;

        devices = new ConcurrentHashMap<>();
        channelFutures = new ConcurrentHashMap<>();
        bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .handler(setupChannelInitializer());
    }

    protected abstract ChannelInitializer<NioSocketChannel> setupChannelInitializer();

    @Override
    public final void connect(long deviceId) {
        Device device = deviceService.getById(deviceId);
        if (device == null) {
            // do something here
            throw new RuntimeException("Temp logic here");
        }

        DeviceHolder deviceHolder = new DeviceHolder();
        deviceHolder.setStatus(DeviceStatus.DISCONNECTED);
        deviceHolder.setDevice(device);
        devices.put(deviceId, deviceHolder);

        TCPDevice tcpDevice = JsonUtils.parse(device.getDetail(), TCPDevice.class);
        ChannelFuture channelFuture = bootstrap.connect(
                new InetSocketAddress(tcpDevice.getIp(), tcpDevice.getPort()));
        channelFutures.put(deviceId, channelFuture);
    }

    @Override
    public DeviceStatus getStatus(long deviceId) {
        if (devices.containsKey(deviceId)) {
            return devices.get(deviceId).getStatus();
        }
        return DeviceStatus.NONE;
    }
}
