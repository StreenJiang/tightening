package com.tightening.lifecycle.monitor;

import com.tightening.device.contract.ITool;
import com.tightening.device.DeviceRegistry;
import com.tightening.service.SseService;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class DeviceConnectionMonitorTest {

    private DeviceRegistry registry;
    private SseService sseService;
    private DeviceConnectionMonitor monitor;
    private ITool tool;

    @BeforeEach
    void setUp() {
        registry = mock(DeviceRegistry.class);
        sseService = mock(SseService.class);
        tool = mock(ITool.class);
        monitor = new DeviceConnectionMonitor(registry, sseService);
    }

    @AfterEach
    void tearDown() {
        monitor.stop();
    }

    @Test
    @DisplayName("start() 应启动后台调度")
    void shouldStartScheduler() {
        when(registry.getAllTools()).thenReturn(List.of(tool));
        when(tool.isConnected()).thenReturn(true);
        when(tool.id()).thenReturn(1L);

        monitor.start();
        assertThat(monitor.isRunning()).isTrue();
    }

    @Test
    @DisplayName("设备状态变更时应推送 SSE")
    void shouldEmitWhenStatusChanges() throws Exception {
        when(registry.getAllTools()).thenReturn(List.of(tool));
        when(tool.isConnected()).thenReturn(false, true);
        when(tool.id()).thenReturn(1L);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(sseService).emitDevice(anyString(), any());

        monitor.start();
        latch.await(3, TimeUnit.SECONDS);

        verify(sseService, atLeastOnce()).emitDevice(anyString(), any());
    }
}
