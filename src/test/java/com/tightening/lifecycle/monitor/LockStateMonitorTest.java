package com.tightening.lifecycle.monitor;

import com.tightening.constant.LockReason;
import com.tightening.constant.WorkplaceStatus;
import com.tightening.device.contract.ITool;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.WorkplaceStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("LockStateMonitor")
class LockStateMonitorTest {

    private WorkplaceStatusService wsService;
    private LockStateMonitor monitor;
    private ITool tool;

    @BeforeEach
    void setUp() {
        wsService = mock(WorkplaceStatusService.class);
        monitor = new LockStateMonitor(wsService);
        tool = mock(ITool.class);
    }

    @Test
    @DisplayName("intervalMs 应为 50ms")
    void shouldReturn50msInterval() {
        assertThat(monitor.intervalMs()).isEqualTo(50);
    }

    @Test
    @DisplayName("lockReasons 非空 + tool 已 unlock → 应发送 lock")
    void shouldLockWhenReasonsNotEmptyAndToolUnlocked() {
        var ctx = ctxWithTool(tool);
        ctx.getLockReasons().add(LockReason.PSET_SENDING);
        when(tool.isUnlocked()).thenReturn(true);

        monitor.execute(ctx);

        verify(tool, times(1)).sendLock();
        verify(wsService, times(1)).transitionTo(
                eq(WorkplaceStatus.OPERATION_DISABLE), anySet());
    }

    @Test
    @DisplayName("lockReasons 为空 + tool 已 lock → 应发送 unlock")
    void shouldUnlockWhenReasonsEmptyAndToolLocked() {
        var ctx = ctxWithTool(tool);
        when(tool.isUnlocked()).thenReturn(false);

        monitor.execute(ctx);

        verify(tool, times(1)).sendUnlock();
        verify(wsService, times(1)).transitionTo(
                eq(WorkplaceStatus.OPERATION_ENABLE), eq(Set.of()));
    }

    @Test
    @DisplayName("boltUnlockOverride 为 true → 跳过所有逻辑")
    void shouldSkipWhenBoltUnlockOverrideTrue() {
        var ctx = ctxWithTool(tool);
        ctx.setBoltUnlockOverride(true);
        ctx.getLockReasons().add(LockReason.PSET_SENDING);

        monitor.execute(ctx);

        verify(tool, never()).sendLock();
        verify(tool, never()).sendUnlock();
        verify(wsService, never()).transitionTo(any(), any());
    }

    @Test
    @DisplayName("状态已匹配时不应重复发送")
    void shouldNotRedundantSend() {
        var ctx = ctxWithTool(tool);
        ctx.getLockReasons().add(LockReason.ADMIN_CONFIRM);
        when(tool.isUnlocked()).thenReturn(false);  // already locked

        monitor.execute(ctx);

        verify(tool, never()).sendLock();
        verify(tool, never()).sendUnlock();
    }

    private static TaskContext ctxWithTool(ITool tool) {
        return TaskContext.builder()
            .productTaskId(1L)
            
            .boltConfigs(java.util.List.of())
            .deviceRegistry(Map.of(1L, tool))
            .build();
    }
}
