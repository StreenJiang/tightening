package com.tightening.service;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.tightening.constant.ExportTaskStatus;
import com.tightening.entity.ExportTask;
import com.tightening.mapper.ExportTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportTaskService")
class ExportTaskServiceTest {

    @Mock
    private ExportTaskMapper mapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ExportTaskService service;

    @BeforeEach
    void setUp() {
        service = spy(new ExportTaskService(eventPublisher));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    @DisplayName("createTask 保存 PENDING 状态的 ExportTask")
    void shouldCreateTask() {
        ArgumentCaptor<ExportTask> captor = ArgumentCaptor.forClass(ExportTask.class);
        when(mapper.insert(any(ExportTask.class))).thenReturn(1);

        service.createTask("standard_excel", 42L, "{\"k\":\"v\"}");

        verify(mapper).insert(captor.capture());
        ExportTask task = captor.getValue();
        assertThat(task.getType()).isEqualTo("standard_excel");
        assertThat(task.getTaskRecordId()).isEqualTo(42L);
        assertThat(task.getPayload()).isEqualTo("{\"k\":\"v\"}");
        assertThat(task.getStatus()).isEqualTo(ExportTaskStatus.PENDING.getCode());
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getMaxRetries()).isEqualTo(3);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("findPending 查询 PENDING 状态的导出任务并按 ID 升序排列")
    void shouldFindPending() {
        LambdaQueryChainWrapper queryChain = mock(LambdaQueryChainWrapper.class);
        when(queryChain.eq(any(), any())).thenReturn(queryChain);
        when(queryChain.orderByAsc((SFunction) any())).thenReturn(queryChain);
        when(queryChain.last(anyString())).thenReturn(queryChain);
        when(queryChain.list()).thenReturn(List.of());
        doReturn(queryChain).when(service).lambdaQuery();

        List<ExportTask> result = service.findPending(2);

        assertThat(result).isEmpty();
        verify(queryChain).list();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("markProcessing 将状态改为 PROCESSING")
    void shouldMarkProcessing() {
        LambdaUpdateChainWrapper updateChain = mock(LambdaUpdateChainWrapper.class);
        when(updateChain.eq(any(), any())).thenReturn(updateChain);
        when(updateChain.set(any(), any())).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);
        doReturn(updateChain).when(service).lambdaUpdate();

        service.markProcessing(1L);

        verify(updateChain).update();
        verify(updateChain, atLeastOnce()).set(any(), eq(ExportTaskStatus.PROCESSING.getCode()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("markCompleted 更新状态为 COMPLETED 并设置完成时间")
    void shouldMarkCompleted() {
        LambdaUpdateChainWrapper updateChain = mock(LambdaUpdateChainWrapper.class);
        when(updateChain.eq(any(), any())).thenReturn(updateChain);
        when(updateChain.set(any(), any())).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);
        doReturn(updateChain).when(service).lambdaUpdate();

        service.markCompleted(1L);

        verify(updateChain).update();
        verify(updateChain, atLeastOnce()).set(any(), eq(ExportTaskStatus.COMPLETED.getCode()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("markFailed retryCount < maxRetries 时降级为 PENDING 重试")
    void shouldMarkFailed_willRetry() {
        LambdaUpdateChainWrapper updateChain = mock(LambdaUpdateChainWrapper.class);
        when(updateChain.eq(any(), any())).thenReturn(updateChain);
        when(updateChain.set(any(), any())).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);
        doReturn(updateChain).when(service).lambdaUpdate();

        service.markFailed(1L, "timeout", 2, 3);

        verify(updateChain).update();
        verify(updateChain, atLeastOnce()).set(any(), eq(ExportTaskStatus.PENDING.getCode()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("markFailed retryCount >= maxRetries 时设为 FAILED")
    void shouldMarkFailed_permanent() {
        LambdaUpdateChainWrapper updateChain = mock(LambdaUpdateChainWrapper.class);
        when(updateChain.eq(any(), any())).thenReturn(updateChain);
        when(updateChain.set(any(), any())).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);
        doReturn(updateChain).when(service).lambdaUpdate();

        service.markFailed(1L, "fatal", 3, 3);

        verify(updateChain).update();
        verify(updateChain, atLeastOnce()).set(any(), eq(ExportTaskStatus.FAILED.getCode()));
    }
}
