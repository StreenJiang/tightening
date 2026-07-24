package com.tightening.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.InspectionScope;
import com.tightening.constant.PrerequisiteType;
import com.tightening.dto.PrerequisiteDetailItem;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.InspectionTaskBinding;
import com.tightening.entity.ProductTask;
import com.tightening.entity.TaskPrerequisite;
import com.tightening.i18n.BusinessException;
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
                throw BusinessException.of("prerequisite.chain_self", taskId);
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
            throw BusinessException.of("prerequisite.inspection_type");
        }
        if (PrerequisiteType.INSPECTION_CHAIN != prerequisiteType && isInspection) {
            throw BusinessException.of("prerequisite.trace_type");
        }
    }

    public void validateInspectionBinding(ProductTask boundTask) {
        if (boundTask != null && isInspectionTask(boundTask)) {
            throw BusinessException.of("prerequisite.inspection_bind");
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
                    throw BusinessException.of("barcode.segment_length_error",
                            seg.v().length(), expectedLen);
                }
            }
        } catch (BusinessException e) {
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
            throw BusinessException.of("validation.product_code_rule_limit");
        }
        boolean hasMaterial = finalRules.stream()
                .anyMatch(r -> BarCodeRuleType.MATERIAL_BARCODE.getCode() == r.getRuleType());
        if (hasMaterial && productCount == 0) {
            throw BusinessException.of("validation.product_code_required");
        }
    }

    public void validateInspectionChainSelfInspection(ProductTask task, List<PrerequisiteDetailItem> items) {
        if (items == null || items.isEmpty()) return;
        boolean hasInspectionChain = items.stream()
                .anyMatch(i -> PrerequisiteType.INSPECTION_CHAIN == i.getPrerequisiteType());
        if (hasInspectionChain && !isInspectionTask(task)) {
            throw BusinessException.of("prerequisite.inspection_chain_self_inspection");
        }
    }

    public void validateInspectionScope(ProductTask task, List<Long> boundTaskIds) {
        if (!isInspectionTask(task)) return;
        if (task.getInspectionScope() == null || task.getInspectionScope() == InspectionScope.NONE) {
            throw BusinessException.of("prerequisite.inspection_scope");
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
                throw BusinessException.of("prerequisite.inspection_target");
            }
        }
        if (task.getId() != null) {
            long count = bindingService.lambdaQuery()
                    .eq(InspectionTaskBinding::getBoundTaskId, task.getId())
                    .eq(InspectionTaskBinding::getDeleted, 0)
                    .count();
            if (count > 0) {
                throw BusinessException.of("prerequisite.task_already_inspected");
            }
        }
    }

    private boolean isInspectionTask(ProductTask task) {
        return Integer.valueOf(1).equals(task.getIsInspection());
    }

    public void validateBarcodeRuleForPrerequisite(BarCodeMatchingRule rule, PrerequisiteType prerequisiteType) {
        if (PrerequisiteType.MATERIAL_TRACE == prerequisiteType) {
            if (rule == null) {
                throw BusinessException.of("prerequisite.material_trace_rule");
            }
            if (BarCodeRuleType.MATERIAL_BARCODE.getCode() != rule.getRuleType()) {
                throw BusinessException.of("prerequisite.material_barcode_type");
            }
        } else {
            if (rule != null) {
                throw BusinessException.of("prerequisite.only_material_can_have_barcode");
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
            throw BusinessException.of("prerequisite.product_trace_duplicate");
        }
    }
}
