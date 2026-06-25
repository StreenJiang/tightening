package com.tightening.service;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.PrerequisiteType;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.MissionPrerequisite;
import com.tightening.entity.ProductMission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class MissionConfigValidator {
    private final MissionPrerequisiteService prerequisiteService;
    private final BarCodeMatchingRuleService barcodeRuleService;

    public MissionConfigValidator(MissionPrerequisiteService prerequisiteService,
                                  BarCodeMatchingRuleService barcodeRuleService) {
        this.prerequisiteService = prerequisiteService;
        this.barcodeRuleService = barcodeRuleService;
    }

    public void validateNoCircularDependency(Long missionId, Long prerequisiteMissionId) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(prerequisiteMissionId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!visited.add(current)) continue;
            if (current.equals(missionId)) {
                throw new IllegalArgumentException("检测到循环依赖: mission " + missionId + " 不能依赖自身");
            }
            prerequisiteService.lambdaQuery()
                    .select(MissionPrerequisite::getPrerequisiteMissionId)
                    .eq(MissionPrerequisite::getMissionId, current)
                    .eq(MissionPrerequisite::getDeleted, 0)
                    .list().stream()
                    .map(MissionPrerequisite::getPrerequisiteMissionId)
                    .forEach(queue::add);
        }
    }

    public void validatePrerequisiteType(ProductMission target, Integer prerequisiteType) {
        if (target == null) return;
        boolean isInspection = Integer.valueOf(1).equals(target.getIsInspection());
        if (Integer.valueOf(PrerequisiteType.INSPECTION_CHAIN.getCode()).equals(prerequisiteType) && !isInspection) {
            throw new IllegalArgumentException("INSPECTION_CHAIN 的前置任务必须是点检任务 (is_inspection=1)");
        }
        if (!Integer.valueOf(PrerequisiteType.INSPECTION_CHAIN.getCode()).equals(prerequisiteType) && isInspection) {
            throw new IllegalArgumentException("SAME_TRACE/PARTS_TRACE 的前置任务必须是普通任务 (is_inspection=0)");
        }
    }

    public void validateInspectionBinding(ProductMission boundMission) {
        if (boundMission != null && Integer.valueOf(1).equals(boundMission.getIsInspection())) {
            throw new IllegalArgumentException("点检任务不能绑定到另一个点检任务");
        }
    }

    public void validateKeyCharLength(BarCodeMatchingRule rule) {
        if (rule.getKeyStartPosition() == null || rule.getKeyChar() == null) return;
        int expectedLen;
        if (rule.getKeyEndPosition() != null) {
            expectedLen = rule.getKeyEndPosition() - rule.getKeyStartPosition() + 1;
        } else {
            expectedLen = 1;
        }
        if (rule.getKeyChar().length() != expectedLen) {
            throw new IllegalArgumentException(
                    "key_char 长度(" + rule.getKeyChar().length() + ")与位置范围(" + expectedLen + ")不匹配");
        }
    }

    public void validateProductTraceUnique(Long productMissionId, Long ruleId) {
        long count = barcodeRuleService.lambdaQuery()
                .eq(BarCodeMatchingRule::getProductMissionId, productMissionId)
                .eq(BarCodeMatchingRule::getRuleType, BarCodeRuleType.PRODUCT_TRACE.getCode())
                .eq(BarCodeMatchingRule::getDeleted, 0)
                .ne(ruleId != null, BarCodeMatchingRule::getId, ruleId)
                .count();
        if (count > 0) {
            throw new IllegalArgumentException("该 mission 已存在 PRODUCT_TRACE 规则，每个 mission 最多一条");
        }
    }
}
