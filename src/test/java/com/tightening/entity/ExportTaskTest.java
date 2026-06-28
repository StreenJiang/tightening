package com.tightening.entity;

import com.tightening.constant.ExportTaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExportTask 实体")
class ExportTaskTest {

    @Test
    @DisplayName("链式 setter 设置字段")
    void shouldSetFieldsWithChain() {
        ExportTask task = new ExportTask()
                .setType("standard_excel")
                .setMissionRecordId(42L)
                .setPayload("{\"key\":\"value\"}")
                .setStatus(ExportTaskStatus.PENDING.getCode())
                .setRetryCount(0)
                .setMaxRetries(3);

        assertThat(task.getType()).isEqualTo("standard_excel");
        assertThat(task.getMissionRecordId()).isEqualTo(42L);
        assertThat(task.getPayload()).isEqualTo("{\"key\":\"value\"}");
        assertThat(task.getStatus()).isEqualTo(ExportTaskStatus.PENDING.getCode());
        assertThat(task.getRetryCount()).isEqualTo(0);
        assertThat(task.getMaxRetries()).isEqualTo(3);
        assertThat(task.getErrorMessage()).isNull();
        assertThat(task.getCompletedAt()).isNull();
    }
}
