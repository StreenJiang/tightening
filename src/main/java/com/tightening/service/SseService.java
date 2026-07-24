package com.tightening.service;

import com.tightening.util.ThreadUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SseService {

    private volatile SseEmitter deviceEmitter;
    private volatile SseEmitter workplaceEmitter;

    private final ScheduledExecutorService heartbeat =
            ThreadUtils.newDaemonScheduledExecutor("sse-heartbeat");

    private volatile ScheduledFuture<?> deviceHbFuture;
    private volatile ScheduledFuture<?> workplaceHbFuture;

    // ── Device emitter ──

    public SseEmitter createDeviceEmitter() {
        SseEmitter previous = this.deviceEmitter;
        if (previous != null) {
            try { previous.complete(); } catch (Exception e) { /* ignore */ }
        }
        deviceEmitter = new SseEmitter(0L);
        if (deviceHbFuture != null) deviceHbFuture.cancel(false);
        deviceHbFuture = heartbeat.scheduleAtFixedRate(
                heartbeatTask(deviceEmitter, this::closeDevice),
                30, 30, TimeUnit.SECONDS);
        return deviceEmitter;
    }

    public void emitDevice(String type, Object data) {
        SseEmitter current = this.deviceEmitter;
        if (current == null) return;
        send(current, type, data, "device");
    }

    public void closeDevice() {
        if (deviceHbFuture != null) {
            deviceHbFuture.cancel(false);
            deviceHbFuture = null;
        }
        SseEmitter emitter = this.deviceEmitter;
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception e) {
                log.warn("SSE device emitter complete failed: {}", e.getMessage());
            }
            this.deviceEmitter = null;
        }
    }

    // ── Workplace emitter ──

    public SseEmitter createWorkplaceEmitter() {
        SseEmitter previous = this.workplaceEmitter;
        if (previous != null) {
            try { previous.complete(); } catch (Exception e) { /* ignore */ }
        }
        workplaceEmitter = new SseEmitter(0L);
        if (workplaceHbFuture != null) workplaceHbFuture.cancel(false);
        workplaceHbFuture = heartbeat.scheduleAtFixedRate(
                heartbeatTask(workplaceEmitter, this::closeWorkplace),
                30, 30, TimeUnit.SECONDS);
        return workplaceEmitter;
    }

    public void emitWorkplace(String type, Object data) {
        SseEmitter current = this.workplaceEmitter;
        if (current == null) return;
        send(current, type, data, "workplace");
    }

    public void closeWorkplace() {
        if (workplaceHbFuture != null) {
            workplaceHbFuture.cancel(false);
            workplaceHbFuture = null;
        }
        SseEmitter emitter = this.workplaceEmitter;
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception e) {
                log.warn("SSE workplace emitter complete failed: {}", e.getMessage());
            }
            this.workplaceEmitter = null;
        }
    }

    // ── Internal ──

    private void send(SseEmitter emitter, String type, Object data, String channel) {
        try {
            emitter.send(SseEmitter.event().name(type).data(data));
        } catch (IOException e) {
            log.warn("SSE {} emit failed: {}", channel, e.getMessage());
        }
    }

    private Runnable heartbeatTask(SseEmitter emitter, Runnable onFailure) {
        return () -> {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException e) {
                log.warn("SSE heartbeat failed: {}", e.getMessage());
                onFailure.run();
            }
        };
    }
}
