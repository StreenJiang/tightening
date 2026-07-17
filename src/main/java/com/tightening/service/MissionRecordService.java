package com.tightening.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.MissionRecord;
import com.tightening.mapper.MissionRecordMapper;

import lombok.extern.slf4j.Slf4j;

import com.tightening.constant.MissionResult;

@Slf4j
@Service
public class MissionRecordService extends ServiceImpl<MissionRecordMapper, MissionRecord> {

    public MissionRecord createRecord(Long productMissionId, String productCode,
                                       String partsCode, Integer isRework) {
        MissionRecord record = new MissionRecord()
                .setProductMissionId(productMissionId)
                .setProductCode(productCode)
                .setPartsCode(partsCode)
                .setIsRework(isRework)
                .setMissionResult(MissionResult.NG.getCode());
        save(record);
        return record;
    }

    public void markAsOk(Long recordId) {
        lambdaUpdate().eq(MissionRecord::getId, recordId)
                .set(MissionRecord::getMissionResult, MissionResult.OK.getCode())
                .update();
    }

    public void markFaulted(Long recordId, String message) {
        lambdaUpdate().eq(MissionRecord::getId, recordId)
                .set(MissionRecord::getMissionResult, MissionResult.NG.getCode())
                .set(MissionRecord::getFaultMessage, message)
                .update();
    }

    public void updateSnapshot(Long recordId, String snapshotJson) {
        lambdaUpdate().eq(MissionRecord::getId, recordId)
                .set(MissionRecord::getContextSnapshot, snapshotJson)
                .update();
    }
}
