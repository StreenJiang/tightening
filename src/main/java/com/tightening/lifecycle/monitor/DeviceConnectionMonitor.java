package com.tightening.lifecycle.monitor;

import com.tightening.constant.SseEventType;
import com.tightening.device.DeviceRegistry;
import com.tightening.dto.SseEvent;
import com.tightening.service.SseService;
import com.tightening.util.ThreadUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DeviceConnectionMonitor {

    private final DeviceRegistry registry;
    private final SseService sseService;
    private ScheduledExecutorService scheduler;
    private Map<Long, Boolean> lastStatus = Map.of();
    private volatile boolean running = false;

    public DeviceConnectionMonitor(DeviceRegistry registry, SseService sseService) {
        this.registry = registry;
        this.sseService = sseService;
    }

    public void start() {
        if (running) return;
        scheduler = ThreadUtils.newDaemonScheduledExecutor("device-connection-monitor");
        running = true;
        scheduler.scheduleAtFixedRate(this::check, 0, 1000, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void check() {
        try {
            Map<Long, Boolean> current = new HashMap<>();
            var tools = registry.getAllTools();
            if (tools == null) return;
            for (var tool : tools) {
                current.put(tool.id(), tool.isConnected());
            }
            if (!current.equals(lastStatus)) {
                lastStatus = current;
                sseService.emit(new SseEvent(SseEventType.DEVICE_STATUS, current, LocalDateTime.now()));
            }
        } catch (Exception e) {
            log.warn("DeviceConnectionMonitor check error", e);
        }
    }
}
