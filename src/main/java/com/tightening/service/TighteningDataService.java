package com.tightening.service;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.TighteningData;
import com.tightening.mapper.TighteningDataMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Service
public class TighteningDataService extends ServiceImpl<TighteningDataMapper, TighteningData> {

    public List<TighteningData> listByMissionRecordId(Long missionRecordId) {
        return lambdaQuery()
                .eq(TighteningData::getMissionRecordId, missionRecordId)
                .orderByAsc(TighteningData::getId)
                .list();
    }
}
