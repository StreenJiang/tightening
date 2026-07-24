package com.tightening.device.handler.impl;

import com.tightening.config.GatewayConfig;
import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.constant.SseEvents;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.type.TCPDevice;
import com.tightening.entity.Device;
import com.tightening.netty.protocol.codec.ModbusRtuFrameDecoder;
import com.tightening.service.DeviceService;
import com.tightening.service.SseService;
import com.tightening.util.JsonUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.tightening.device.handler.impl.aneng.ArmAdapter;
import com.tightening.device.handler.impl.aneng.ArrangerAdapter;
import com.tightening.device.handler.impl.aneng.SetterSelectorAdapter;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Lazy
@Component
public class AnengGatewayHandler implements DeviceHandler {

    private static final int RECONNECT_INTERVAL_MS = 5000;
    private static final int DEFAULT_PORT = 4545;

    private final Bootstrap bootstrap;
    private final NioEventLoopGroup group;
    private final DeviceService deviceService;
    private final GatewayConfig gatewayConfig;
    private final SseService sseService;
    private final Map<Long, DeviceHolder> devices = new ConcurrentHashMap<>();
    private final Map<Long, Channel> channels = new ConcurrentHashMap<>();

    private final Map<Long, ArmAdapter> armAdapters = new ConcurrentHashMap<>();
    private final Map<Long, SetterSelectorAdapter> setterAdapters = new ConcurrentHashMap<>();
    private final Map<Long, ArrangerAdapter> arrangerAdapters = new ConcurrentHashMap<>();

    // 响应匹配：每个通道一个 FIFO 队列，轮询时多个请求按序匹配响应
    private final Map<Channel, Deque<CompletableFuture<ByteBuf>>> responseQueues = new ConcurrentHashMap<>();
    private final AtomicInteger seqCounter = new AtomicInteger(0);

    private ScheduledExecutorService pollScheduler;

    static final AttributeKey<Long> DEVICE_ID_KEY = AttributeKey.valueOf("gwDeviceId");

    public AnengGatewayHandler(NioEventLoopGroup group, DeviceService deviceService,
                                    GatewayConfig gatewayConfig, SseService sseService) {
        this.group = group;
        this.deviceService = deviceService;
        this.gatewayConfig = gatewayConfig;
        this.sseService = sseService;
        this.bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new ModbusRtuFrameDecoder());
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
                                Deque<CompletableFuture<ByteBuf>> queue = responseQueues.get(ch);
                                if (queue != null) {
                                    CompletableFuture<ByteBuf> future = queue.poll();
                                    if (future != null) {
                                        future.complete(frame.retain());
                                    }
                                }
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                Long deviceId = ch.attr(DEVICE_ID_KEY).get();
                                if (deviceId != null) {
                                    handleDisconnect(deviceId);
                                }
                            }
                        });
                    }
                });
    }

    // === DeviceHandler 实现 ===

    @Override
    public void connect(long deviceId) {
        Device device = deviceService.lambdaQuery().eq(Device::getId, deviceId).one();
        if (device == null) {
            log.error("Device not found: {}", deviceId);
            return;
        }
        DeviceHolder holder = devices.computeIfAbsent(deviceId,
                id -> new DeviceHolder(device));
        holder.setStatus(DeviceStatus.CONNECTING);

        TCPDevice tcpDevice = JsonUtils.parse(device.getDetail(), TCPDevice.class);
        String ip = tcpDevice.getIp() != null ? tcpDevice.getIp() : "127.0.0.1";
        int port = tcpDevice.getPort() > 0 ? tcpDevice.getPort() : DEFAULT_PORT;

        bootstrap.connect(new InetSocketAddress(ip, port))
                .addListener((ChannelFuture f) -> {
                    if (f.isSuccess()) {
                        Channel ch = f.channel();
                        ch.attr(DEVICE_ID_KEY).set(deviceId);
                        channels.put(deviceId, ch);
                        holder.setChannel(ch);
                        holder.setStatus(DeviceStatus.CONNECTED);
                        startPolling(deviceId);
                        log.info("Gateway connected: deviceId={}", deviceId);
                    } else {
                        holder.setStatus(DeviceStatus.DISCONNECTED);
                        log.warn("Gateway connect failed deviceId={}, retrying in {}ms",
                                deviceId, RECONNECT_INTERVAL_MS);
                        f.channel().eventLoop().schedule(() -> connect(deviceId),
                                RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    }
                });
    }

    @Override
    public void disconnect(long deviceId) {
        Channel ch = channels.remove(deviceId);
        if (ch != null) {
            ch.close();
        }
        stopPolling(deviceId);
        DeviceHolder holder = devices.get(deviceId);
        if (holder != null) {
            holder.setStatus(DeviceStatus.DISCONNECTED);
        }
    }

    @Override
    public DeviceStatus getStatus(long deviceId) {
        DeviceHolder holder = devices.get(deviceId);
        return holder != null ? holder.getStatus() : DeviceStatus.NONE;
    }

    @Override
    public Set<DeviceType> getSupportedTypes() {
        return Set.of(DeviceType.ANENG_GATEWAY);
    }

    // === Modbus 命令 ===

    public CompletableFuture<ByteBuf> sendModbusFrame(long gatewayDeviceId, byte[] frame) {
        Channel ch = channels.get(gatewayDeviceId);
        if (ch == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Gateway not connected: " + gatewayDeviceId));
        }
        int seq = seqCounter.getAndIncrement() & 0x7FFFFFFF;
        CompletableFuture<ByteBuf> future = new CompletableFuture<>();

        responseQueues.computeIfAbsent(ch, k -> new ConcurrentLinkedDeque<>()).add(future);

        ch.writeAndFlush(Unpooled.wrappedBuffer(frame)).addListener((ChannelFuture f) -> {
            if (!f.isSuccess()) {
                // 从队列移除并标记失败
                Deque<CompletableFuture<ByteBuf>> queue = responseQueues.get(ch);
                if (queue != null) queue.remove(future);
                future.completeExceptionally(f.cause());
            }
        });

        String key = gatewayDeviceId + "-" + seq;
        ch.eventLoop().schedule(() -> {
            Deque<CompletableFuture<ByteBuf>> queue = responseQueues.get(ch);
            if (queue != null && queue.remove(future)) {
                future.completeExceptionally(
                        new TimeoutException("Modbus command timeout for " + key));
            }
        }, gatewayConfig.getArmReadTimeoutMs(), TimeUnit.MILLISECONDS);

        return future;
    }

    // === 子设备注册/注销 ===

    public void registerArm(long gatewayDeviceId, long armDeviceId, ArmAdapter adapter) {
        armAdapters.put(armDeviceId, adapter);
    }

    public void registerSetterSelector(long gatewayDeviceId, long ssDeviceId, SetterSelectorAdapter adapter) {
        setterAdapters.put(ssDeviceId, adapter);
    }

    public void registerArranger(long gatewayDeviceId, long arrDeviceId, ArrangerAdapter adapter) {
        arrangerAdapters.put(arrDeviceId, adapter);
    }

    public void removeSubDevice(long deviceId) {
        armAdapters.remove(deviceId);
        setterAdapters.remove(deviceId);
        arrangerAdapters.remove(deviceId);
    }

    // === 轮询 ===

    private void startPolling(long gatewayDeviceId) {
        if (pollScheduler == null || pollScheduler.isShutdown()) {
            pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gw-poll-" + gatewayDeviceId);
                t.setDaemon(true);
                return t;
            });
        }
        pollScheduler.scheduleWithFixedDelay(
                () -> pollAllSubDevices(gatewayDeviceId),
                0, gatewayConfig.getPollIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void stopPolling(long gatewayDeviceId) {
        if (pollScheduler != null) {
            pollScheduler.shutdown();
        }
    }

    private void pollAllSubDevices(long gatewayDeviceId) {
        for (ArmAdapter arm : armAdapters.values()) arm.pollHealth();
        for (SetterSelectorAdapter ss : setterAdapters.values()) ss.pollHealth();
        for (ArrangerAdapter arr : arrangerAdapters.values()) arr.pollHealth();
        evaluateGatewayStatus(gatewayDeviceId);
    }

    private void evaluateGatewayStatus(long gatewayDeviceId) {
        // Task 6-7: 力臂/批头选择器使用实际健康检查；Task 8 补充 Arranger
        boolean armsHealthy = armAdapters.values().stream().allMatch(ArmAdapter::isHealthy);
        boolean settersHealthy = setterAdapters.values().stream().allMatch(SetterSelectorAdapter::isHealthy);
        boolean arrangersHealthy = arrangerAdapters.values().stream().allMatch(ArrangerAdapter::isHealthy);
        boolean anyExists = !armAdapters.isEmpty()
                || !setterAdapters.isEmpty()
                || !arrangerAdapters.isEmpty();
        boolean allHealthy = armsHealthy && settersHealthy && arrangersHealthy;

        DeviceHolder holder = devices.get(gatewayDeviceId);
        if (holder != null && anyExists) {
            DeviceStatus newStatus = allHealthy ? DeviceStatus.CONNECTED : DeviceStatus.DEGRADED;
            if (holder.getStatus() != newStatus) {
                holder.setStatus(newStatus);
                pushDeviceStatusEvent(gatewayDeviceId, newStatus);
            }
        }
    }

    private void handleDisconnect(long deviceId) {
        stopPolling(deviceId);
        DeviceHolder holder = devices.get(deviceId);
        if (holder != null) {
            holder.setStatus(DeviceStatus.DISCONNECTED);
        }
        // 清理该通道的待处理响应队列
        Channel ch = channels.get(deviceId);
        if (ch != null) {
            Deque<CompletableFuture<ByteBuf>> queue = responseQueues.remove(ch);
            if (queue != null) {
                for (CompletableFuture<ByteBuf> f : queue) {
                    f.completeExceptionally(new IllegalStateException("Gateway disconnected"));
                }
            }
        }
        pushDeviceStatusEvent(deviceId, DeviceStatus.DISCONNECTED);
        group.next().schedule(() -> connect(deviceId),
                RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void pushDeviceStatusEvent(long deviceId, DeviceStatus status) {
        sseService.emitDevice(SseEvents.DEVICE_STATUS, Map.of(
                "deviceId", deviceId,
                "status", status,
                "ts", LocalDateTime.now().toString()
        ));
    }

    // === Utility ===

    public SseService getSseService() {
        return sseService;
    }
}
