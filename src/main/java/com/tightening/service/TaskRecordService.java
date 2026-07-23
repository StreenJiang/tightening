package com.tightening.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.TaskRecord;
import com.tightening.mapper.TaskRecordMapper;

import lombok.extern.slf4j.Slf4j;

import com.tightening.constant.TaskResult;

@Slf4j
@Service
public class TaskRecordService extends ServiceImpl<TaskRecordMapper, TaskRecord> {

    public TaskRecord createRecord(Long productTaskId, String productCode,
                                       String partsCode, Integer isRework) {
        TaskRecord record = new TaskRecord()
                .setProductTaskId(productTaskId)
                .setProductCode(productCode)
                .setPartsCode(partsCode)
                .setIsRework(isRework)
                .setTaskResult(TaskResult.NG.getCode());
        save(record);
        return record;
    }

    public void markAsOk(Long recordId) {
        lambdaUpdate().eq(TaskRecord::getId, recordId)
                .set(TaskRecord::getTaskResult, TaskResult.OK.getCode())
                .update();
    }

    public void markFaulted(Long recordId, String message) {
        lambdaUpdate().eq(TaskRecord::getId, recordId)
                .set(TaskRecord::getTaskResult, TaskResult.NG.getCode())
                .set(TaskRecord::getFaultMessage, message)
                .update();
    }

    public void updateSnapshot(Long recordId, String snapshotJson) {
        lambdaUpdate().eq(TaskRecord::getId, recordId)
                .set(TaskRecord::getContextSnapshot, snapshotJson)
                .update();
    }
}
