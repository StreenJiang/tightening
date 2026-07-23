package com.tightening.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.InspectionScope;
import com.tightening.constant.PrerequisiteType;
import com.tightening.dto.PrerequisiteDetailItem;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.InspectionTaskBinding;
import com.tightening.entity.TaskPrerequisite;
import com.tightening.entity.ProductTask;
import com.tightening.util.BarcodeMatcher;
import com.tightening.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class TaskConfigValidator {
    private final TaskPrerequisiteService prerequisiteService;
    private final BarCodeMatchingRuleService barcodeRuleService;
    private final InspectionTaskBindingService bindingService;

    public TaskConfigValidator(TaskPrerequisiteService prerequisiteService,
                                  BarCodeMatchingRuleService barcodeRuleService,
                                  InspectionTaskBindingService bindingService) {
        this.prerequisiteService = prerequisiteService;
        this.barcodeRuleService = barcodeRuleService;
        this.bindingService = bindingService;
    }

    public void validateNoCircularDependency(Long taskId, Long prerequisiteTaskId) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(prerequisiteTaskId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!visited.add(current)) continue;
            if (current.equals(taskId)) {
                throw new IllegalArgumentException("检测到循环依赖: task " + taskId + " 不能依赖自身");
            }
            prerequisiteService.lambdaQuery()
                    .select(TaskPrerequisite::getPrerequisiteTaskId)
                    .eq(TaskPrerequisite::getTaskId, current)
                    .eq(TaskPrerequisite::getDeleted, 0)
                    .list().stream()
                    .map(TaskPrerequisite::getPrerequisiteTaskId)
                    .forEach(queue::add);
        }
    }

    public void validatePrerequisiteType(ProductTask target, PrerequisiteType prerequisiteType) {
        if (target == null) return;
        boolean isInspection = isInspectionTask(target);
        if (PrerequisiteType.INSPECTION_CHAIN == prerequisiteType && !isInspection) {
            throw new IllegalArgumentException("INSPECTION_CHAIN 的前置任务必须是点检任务 (is_inspection=1)");
        }
        if (PrerequisiteType.INSPECTION_CHAIN != prerequisiteType && isInspection) {
            throw new IllegalArgumentException("SAME_TRACE/MATERIAL_TRACE 的前置任务必须是普通任务 (is_inspection=0)");
        }
    }

    public void validateInspectionBinding(ProductTask boundTask) {
        if (boundTask != null && isInspectionTask(boundTask)) {
            throw new IllegalArgumentException("点检任务不能绑定到另一个点检任务");
        }
    }

    public void validateKeyCharLength(BarCodeMatchingRule rule) {
        String segmentsJson = rule.getSegments();
        if (segmentsJson == null || segmentsJson.isEmpty()) return;

        try {
            var segments = JsonUtils.OBJECT_MAPPER.readValue(segmentsJson,
                    new TypeReference<List<BarcodeMatcher.Segment>>() {});
            for (var seg : segments) {
                int expectedLen = seg.e() - seg.s();
                if (seg.v() != null && seg.v().length() != expectedLen) {
                    throw new IllegalArgumentException(
                            "segments value 长度(" + seg.v().length() + ")与位置范围(" + expectedLen + ")不匹配");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse segments JSON for validation", e);
        }
    }

    public void validateBarcodeRules(List<BarCodeMatchingRule> finalRules) {
        if (finalRules == null || finalRules.isEmpty()) return;
        long productCount = finalRules.stream()
                .filter(r -> BarCodeRuleType.PRODUCT_TRACE.getCode() == r.getRuleType())
                .count();
        if (productCount > 1) {
            throw new IllegalArgumentException("产品码规则最多 1 条");
        }
        boolean hasMaterial = finalRules.stream()
                .anyMatch(r -> BarCodeRuleType.MATERIAL_BARCODE.getCode() == r.getRuleType());
        if (hasMaterial && productCount == 0) {
            throw new IllegalArgumentException("必须先有产品码规则才能添加物料码规则");
        }
    }

    public void validateInspectionChainSelfInspection(ProductTask task, List<PrerequisiteDetailItem> items) {
        if (items == null || items.isEmpty()) return;
        boolean hasInspectionChain = items.stream()
                .anyMatch(i -> PrerequisiteType.INSPECTION_CHAIN == i.getPrerequisiteType());
        if (hasInspectionChain && !isInspectionTask(task)) {
            throw new IllegalArgumentException("INSPECTION_CHAIN 的前置类型要求当前任务必须是点检任务 (is_inspection=1)");
        }
    }

    public void validateInspectionScope(ProductTask task, List<Long> boundTaskIds) {
        if (!isInspectionTask(task)) return;
        if (task.getInspectionScope() == null || task.getInspectionScope() == InspectionScope.NONE) {
            throw new IllegalArgumentException("点检任务必须选择点检范围");
        }
        if (task.getInspectionScope() == InspectionScope.CHOSEN) {
            boolean hasIncoming = boundTaskIds != null && !boundTaskIds.isEmpty();
            boolean hasExisting = false;
            if (!hasIncoming && task.getId() != null) {
                hasExisting = bindingService.lambdaQuery()
                        .eq(InspectionTaskBinding::getInspectionTaskId, task.getId())
                        .eq(InspectionTaskBinding::getDeleted, 0)
                        .count() > 0;
            }
            if (!hasIncoming && !hasExisting) {
                throw new IllegalArgumentException("点检范围为指定任务时，必须选择至少一个被点检任务");
            }
        }
        if (task.getId() != null) {
            long count = bindingService.lambdaQuery()
                    .eq(InspectionTaskBinding::getBoundTaskId, task.getId())
                    .eq(InspectionTaskBinding::getDeleted, 0)
                    .count();
            if (count > 0) {
                throw new IllegalArgumentException("该任务已被其他点检任务选中，不能改为点检任务");
            }
        }
    }

    private boolean isInspectionTask(ProductTask task) {
        return Integer.valueOf(1).equals(task.getIsInspection());
    }

    public void validateBarcodeRuleForPrerequisite(BarCodeMatchingRule rule, PrerequisiteType prerequisiteType) {
        if (PrerequisiteType.MATERIAL_TRACE == prerequisiteType) {
            if (rule == null) {
                throw new IllegalArgumentException("MATERIAL_TRACE 前置必须关联条码规则");
            }
            if (BarCodeRuleType.MATERIAL_BARCODE.getCode() != rule.getRuleType()) {
                throw new IllegalArgumentException("前置关联的条码规则必须是 MATERIAL_BARCODE 类型");
            }
        } else {
            if (rule != null) {
                throw new IllegalArgumentException("只有 MATERIAL_TRACE 前置可以关联条码规则");
            }
        }
    }

    public void validateProductTraceUnique(Long productTaskId, Long ruleId) {
        long count = barcodeRuleService.lambdaQuery()
                .eq(BarCodeMatchingRule::getProductTaskId, productTaskId)
                .eq(BarCodeMatchingRule::getRuleType, BarCodeRuleType.PRODUCT_TRACE.getCode())
                .eq(BarCodeMatchingRule::getDeleted, 0)
                .ne(ruleId != null, BarCodeMatchingRule::getId, ruleId)
                .count();
        if (count > 0) {
            throw new IllegalArgumentException("该 task 已存在 PRODUCT_TRACE 规则，每个 task 最多一条");
        }
    }
}
