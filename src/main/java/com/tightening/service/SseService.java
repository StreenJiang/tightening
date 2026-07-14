package com.tightening.service;

import com.tightening.dto.SseEvent;
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

    private volatile SseEmitter emitter;
    private final ScheduledExecutorService heartbeat = ThreadUtils.newDaemonScheduledExecutor("sse-heartbeat");
    private volatile ScheduledFuture<?> heartbeatFuture;

    public SseEmitter create() {
        SseEmitter previous = this.emitter;
        if (previous != null) {
            try { previous.complete(); } catch (Exception e) { /* ignore */ }
        }
        emitter = new SseEmitter(0L);
        startHeartbeat();
        return emitter;
    }

    public void emit(SseEvent event) {
        SseEmitter current = this.emitter;
        if (current == null) return;
        try {
            current.send(SseEmitter.event()
                .name(event.type().name())
                .data(event));
        } catch (IOException e) {
            log.warn("SSE emit failed: {}", e.getMessage());
        }
    }

    public void close() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }
        SseEmitter current = this.emitter;
        if (current != null) {
            try {
                current.complete();
            } catch (Exception e) {
                log.warn("SSE emitter complete failed: {}", e.getMessage());
            }
            this.emitter = null;
        }
    }

    private void startHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
        }
        heartbeatFuture = heartbeat.scheduleAtFixedRate(() -> {
            SseEmitter current = this.emitter;
            if (current == null) return;
            try {
                current.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException e) {
                log.warn("SSE heartbeat failed: {}", e.getMessage());
                close();
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
}
