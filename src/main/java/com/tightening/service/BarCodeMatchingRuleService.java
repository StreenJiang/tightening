package com.tightening.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tightening.constant.BarCodeRuleType;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.mapper.BarCodeMatchingRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class BarCodeMatchingRuleService extends ServiceImpl<BarCodeMatchingRuleMapper, BarCodeMatchingRule> {

    public List<BarCodeMatchingRule> listByMissionId(Long missionId) {
        return lambdaQuery()
                .eq(BarCodeMatchingRule::getProductMissionId, missionId)
                .list();
    }

    public List<BarCodeMatchingRule> findProductTraceRulesExcluding(Long excludeMissionId) {
        return lambdaQuery()
                .eq(BarCodeMatchingRule::getRuleType, BarCodeRuleType.PRODUCT_TRACE.getCode())
                .ne(BarCodeMatchingRule::getProductMissionId, excludeMissionId)
                .list();
    }
}
