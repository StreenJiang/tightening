package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ITool;
import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LockTools Capability")
class LockToolsTest {

    @Mock private ITool tool1;
    @Mock private ITool tool2;
    private LockTools cap;

    @BeforeEach
    void setUp() {
        cap = new LockTools();
    }

    @Test
    @DisplayName("id 返回 LockTools")
    void shouldReturnCorrectId() {
        assertThat(cap.id()).isEqualTo("LockTools");
    }

    @Test
    @DisplayName("stage 返回 FINALIZATION")
    void shouldBindToFinalizationStage() {
        assertThat(cap.stage()).isEqualTo(Stage.FINALIZATION);
    }

    @Test
    @DisplayName("subState 返回 LOCKING_TOOLS")
    void shouldBindToLockingToolsSubState() {
        assertThat(cap.subState()).isEqualTo(SubState.LOCKING_TOOLS);
    }

    @Test
    @DisplayName("priority 返回 0")
    void shouldHaveZeroPriority() {
        assertThat(cap.priority()).isZero();
    }

    @Test
    @DisplayName("execute 对所有设备调用 sendLock 并返回 Pass")
    void executeShouldLockAllToolsAndReturnPass() {
        when(tool1.id()).thenReturn(101L);
        when(tool2.id()).thenReturn(102L);
        when(tool1.sendLock()).thenReturn(CompletableFuture.completedFuture(true));
        when(tool2.sendLock()).thenReturn(CompletableFuture.completedFuture(true));

        TaskContext ctx = TaskContext.builder()
            .productTaskId(1L).taskData(new ProductTask())
            .boltConfigs(List.of()).deviceRegistry(Map.of(101L, tool1, 102L, tool2))
            .build();

        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
        verify(tool1).sendLock();
        verify(tool2).sendLock();
    }
}
