package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.entity.InspectionTaskBinding;
import com.tightening.mapper.InspectionTaskBindingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class InspectionTaskBindingService extends ServiceImpl<InspectionTaskBindingMapper, InspectionTaskBinding> {

    public List<InspectionTaskBinding> listByInspectionTaskId(Long inspectionTaskId) {
        return lambdaQuery()
                .eq(InspectionTaskBinding::getInspectionTaskId, inspectionTaskId)
                .list();
    }
}
