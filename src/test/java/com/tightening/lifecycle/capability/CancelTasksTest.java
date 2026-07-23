package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CancelTasks Capability")
class CancelTasksTest {

    private CancelTasks cap;

    @BeforeEach
    void setUp() {
        cap = new CancelTasks();
    }

    @Test
    @DisplayName("id 返回 CancelTasks")
    void shouldReturnCorrectId() {
        assertThat(cap.id()).isEqualTo("CancelTasks");
    }

    @Test
    @DisplayName("stage 返回 FINALIZATION")
    void shouldBindToFinalizationStage() {
        assertThat(cap.stage()).isEqualTo(Stage.FINALIZATION);
    }

    @Test
    @DisplayName("subState 返回 CLEANING_TASKS")
    void shouldBindToCleaningTasksSubState() {
        assertThat(cap.subState()).isEqualTo(SubState.CLEANING_TASKS);
    }

    @Test
    @DisplayName("priority 返回 0")
    void shouldHaveZeroPriority() {
        assertThat(cap.priority()).isZero();
    }

    @Test
    @DisplayName("execute 返回 Pass")
    void executeShouldReturnPass() {
        TaskContext ctx = TaskContext.builder()
            .productTaskId(1L).taskData(new ProductTask())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .build();

        assertThat(cap.execute(ctx)).isEqualTo(CapabilityResult.Pass);
    }
}
