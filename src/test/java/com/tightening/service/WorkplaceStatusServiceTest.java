package com.tightening.service;

import com.tightening.constant.LockReason;
import com.tightening.constant.WorkplaceStatus;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WorkplaceStatusServiceTest {

    private SseService sseService;
    private WorkplaceStatusService wsService;

    @BeforeEach
    void setUp() {
        sseService = mock(SseService.class);
        wsService = new WorkplaceStatusService(sseService);
    }

    @Test
    @DisplayName("初始状态应为 UNACTIVATED")
    void shouldStartUnactivated() {
        assertThat(wsService.current()).isEqualTo(WorkplaceStatus.UNACTIVATED);
    }

    @Test
    @DisplayName("transitionTo 应更新状态并推 SSE")
    void shouldTransitionAndEmit() {
        wsService.transitionTo(WorkplaceStatus.ACTIVATED, Set.of());

        assertThat(wsService.current()).isEqualTo(WorkplaceStatus.ACTIVATED);
        verify(sseService, times(1)).emitWorkplace(anyString(), any());
    }

    @Test
    @DisplayName("transitionTo 应推送带 lockReasons 的 payload")
    void shouldEmitWithLockReasons() {
        var reasons = Set.of(LockReason.PSET_SENDING);
        wsService.transitionTo(WorkplaceStatus.OPERATION_DISABLE, reasons);

        verify(sseService, times(1)).emitWorkplace(anyString(), any());
    }

    @Test
    @DisplayName("reset() 应回到 UNACTIVATED 且清除 lockReasons")
    void shouldResetToUnactivated() {
        wsService.transitionTo(WorkplaceStatus.OPERATION_ENABLE, Set.of(LockReason.ADMIN_CONFIRM));
        wsService.reset();

        assertThat(wsService.current()).isEqualTo(WorkplaceStatus.UNACTIVATED);
    }
}
