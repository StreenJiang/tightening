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

    public List<BarCodeMatchingRule> listByTaskId(Long taskId) {
        return lambdaQuery()
                .eq(BarCodeMatchingRule::getProductTaskId, taskId)
                .orderByAsc(BarCodeMatchingRule::getSeq)
                .list();
    }

    public List<BarCodeMatchingRule> findProductTraceRulesExcluding(Long excludeTaskId) {
        return lambdaQuery()
                .eq(BarCodeMatchingRule::getRuleType, BarCodeRuleType.PRODUCT_TRACE.getCode())
                .ne(BarCodeMatchingRule::getProductTaskId, excludeTaskId)
                .list();
    }
}
