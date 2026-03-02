package com.tightening.device;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.event.DeviceChangeEvent;
import com.tightening.device.handler.ADeviceHandler;
import com.tightening.entity.Device;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DeviceManager implements AutoCloseable {
    private final Map<Long, ADeviceHandler> deviceHandlers;
    private final ScheduledExecutorService scanScheduler;
    private final ExecutorService connectExecutor;
    private final AtomicInteger activeUserCount = new AtomicInteger(0);
    private volatile boolean running = false;

    public DeviceManager() {
        deviceHandlers = new ConcurrentHashMap<>();
        scanScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DeviceScan-Thread");
            t.setDaemon(true);
            return t;
        });
        connectExecutor = new ThreadPoolExecutor(5, 20, 30L, TimeUnit.SECONDS,
                                                 new LinkedBlockingQueue<>(100),
                                                 // 队列满时抛出异常，因为正常情况下不可能满，满了说明系统出问题了
                                                 new ThreadPoolExecutor.AbortPolicy());
    }

    // 用户登录时调用
    public void userLoggedIn(List<Device> devices) {
        if (activeUserCount.incrementAndGet() > 0) {
            // 重复调用无害，方法内部有判断 running
            start(devices);
        }
    }

    // 用户登出或会话销毁时调用
    public void userLoggedOut() {
        if (activeUserCount.decrementAndGet() == 0) {
            stop();
        }
    }

    private synchronized void start(List<Device> devices) {
        if (!running)
            return;

        if (devices != null && !devices.isEmpty()) {
            devices.forEach(d -> {
                ADeviceHandler handler = DeviceType.getType(d.getType()).createHandler();
                deviceHandlers.put(d.getId(), handler);
            });
        }

        running = true;
        // 使用 scheduleWithFixedDelay 确保扫描间隔从任务结束开始计算
        scanScheduler.scheduleWithFixedDelay(this::scanAndConnect, 0, 5, TimeUnit.SECONDS);
    }

    private void scanAndConnect() {
        deviceHandlers.forEach((id, handler) -> {
            if (handler.getDeviceHolder().getStatus() == DeviceStatus.DISCONNECTED) {
                connectExecutor.submit(() -> {
                    try {
                        handler.connect();  // connect 内部会处理状态更新
                    } catch (Exception e) {
                        // 日志记录，但不影响线程池
                        // 可考虑设备级别的异常处理
                    }
                });
            }
        });
    }

    private synchronized void stop() {
        if (!running)
            return;
        running = false;

        // 停止扫描线程
        scanScheduler.shutdown();
        try {
            if (!scanScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scanScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scanScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 停止连接线程池：不再接受新任务，等待已有任务完成
        connectExecutor.shutdown();
        try {
            if (!connectExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                connectExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            connectExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 断开所有设备连接（设备内部线程清理由设备自己负责）
        deviceHandlers.forEach((id, handler) -> handler.disconnect());
        deviceHandlers.clear();
    }

    // 监听设备变更事件（使用 @TransactionalEventListener 确保事务提交后处理）
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDeviceChange(DeviceChangeEvent event) {
        switch (event.getEventType()) {
            case ADD:
                addDevice(event.getDevice());
                break;
            case UPDATE:
                updateDevice(event.getDevice());
                break;
            case DELETE:
                removeDevice(event.getDeviceId());
                break;
        }
    }

    private void addDevice(Device device) {
        if (device == null)
            return;

        ADeviceHandler handler = DeviceType.getType(device.getType()).createHandler();
        deviceHandlers.put(device.getId(), handler);
    }

    private void updateDevice(Device device) {
        if (device == null)
            return;

        // 先移除旧的（并断开连接）
        removeDevice(device.getId());
        // 再添加新的
        addDevice(device);
    }

    private void removeDevice(Long deviceId) {
        ADeviceHandler old = deviceHandlers.remove(deviceId);
        if (old != null) {
            old.disconnect(); // 确保设备断开，内部线程清理
        }
    }

    @Override
    public void close() throws Exception {
        stop();
    }
}
