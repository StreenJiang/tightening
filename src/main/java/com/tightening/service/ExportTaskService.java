package com.tightening.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.ExportTaskStatus;
import com.tightening.entity.ExportTask;
import com.tightening.mapper.ExportTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ExportTaskService extends ServiceImpl<ExportTaskMapper, ExportTask> {

    public void createTask(String type, Long taskRecordId, String payload) {
        ExportTask task = new ExportTask()
                .setType(type)
                .setTaskRecordId(taskRecordId)
                .setPayload(payload)
                .setStatus(ExportTaskStatus.PENDING.getCode())
                .setRetryCount(0)
                .setMaxRetries(3);
        save(task);
    }

    public List<ExportTask> findPending(int limit) {
        return lambdaQuery()
                .eq(ExportTask::getStatus, ExportTaskStatus.PENDING.getCode())
                .orderByAsc(ExportTask::getId)
                .last("LIMIT " + limit)
                .list();
    }

    public void markProcessing(Long id) {
        lambdaUpdate()
                .eq(ExportTask::getId, id)
                .eq(ExportTask::getStatus, ExportTaskStatus.PENDING.getCode())
                .set(ExportTask::getStatus, ExportTaskStatus.PROCESSING.getCode())
                .update();
    }

    public void markCompleted(Long id) {
        lambdaUpdate()
                .eq(ExportTask::getId, id)
                .set(ExportTask::getStatus, ExportTaskStatus.COMPLETED.getCode())
                .set(ExportTask::getCompletedAt, LocalDateTime.now())
                .update();
    }

    public void markFailed(Long id, String errorMessage, int retryCount, int maxRetries) {
        int newStatus = retryCount >= maxRetries
                ? ExportTaskStatus.FAILED.getCode() : ExportTaskStatus.PENDING.getCode();
        lambdaUpdate()
                .eq(ExportTask::getId, id)
                .set(ExportTask::getStatus, newStatus)
                .set(ExportTask::getRetryCount, retryCount)
                .set(ExportTask::getErrorMessage, errorMessage)
                .update();
    }

    /**
     * 清理超过指定天数的已完成/失败导出任务（使用逻辑删除）。
     * @param olderThanDays 保留天数
     * @return 删除的任务数量
     */
    public int cleanupTasks(int olderThanDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        return baseMapper.delete(new LambdaQueryWrapper<ExportTask>()
                .in(ExportTask::getStatus, ExportTaskStatus.COMPLETED.getCode(), ExportTaskStatus.FAILED.getCode())
                .apply("completed_at IS NOT NULL AND completed_at < {0}", cutoff.toString()));
    }
}
