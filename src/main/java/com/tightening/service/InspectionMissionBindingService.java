package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.InspectionMissionBinding;
import com.tightening.mapper.InspectionMissionBindingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class InspectionMissionBindingService extends ServiceImpl<InspectionMissionBindingMapper, InspectionMissionBinding> {

    public List<InspectionMissionBinding> listByInspectionMissionId(Long inspectionMissionId) {
        return lambdaQuery()
                .eq(InspectionMissionBinding::getInspectionMissionId, inspectionMissionId)
                .list();
    }
}
