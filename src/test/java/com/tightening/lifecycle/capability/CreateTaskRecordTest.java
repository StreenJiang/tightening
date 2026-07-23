package com.tightening.lifecycle.capability;

import com.tightening.entity.TaskRecord;
import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.TaskRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateTaskRecord Capability")
class CreateTaskRecordTest {

    @Mock private TaskRecordService taskRecordService;
    private CreateTaskRecord cap;

    @BeforeEach
    void setUp() {
        cap = new CreateTaskRecord(taskRecordService);
    }

    @Test
    @DisplayName("创建 TaskRecord 并回写 Context")
    void shouldCreateRecordAndSetOnContext() {
        TaskRecord record = new TaskRecord();
        record.setId(42L);
        when(taskRecordService.createRecord(1L, null, null, 0)).thenReturn(record);

        TaskContext ctx = minimalContext();
        CapabilityResult result = cap.execute(ctx);

        assertThat(result).isEqualTo(CapabilityResult.Pass);
        assertThat(ctx.getTaskRecord().getId()).isEqualTo(42L);
        verify(taskRecordService).createRecord(1L, null, null, 0);
    }

    private static TaskContext minimalContext() {
        return TaskContext.builder()
            .productTaskId(1L).taskData(new ProductTask())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .build();
    }
}
