package com.tightening.lifecycle.capability;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.util.BarcodeMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ProductBarCodeCheck implements TriggerCapability {

    private final BarCodeMatchingRuleService ruleService;

    @Override public String id() { return "ProductBarCodeCheck"; }
    @Override public Stage stage() { return Stage.VALIDATION; }
    @Override public SubState subState() { return SubState.VALIDATING; }
    @Override public int priority() { return 1; }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        List<BarCodeMatchingRule> rules = ruleService.listByMissionId(ctx.getProductMissionId()).stream()
                .filter(r -> r.getRuleType() == BarCodeRuleType.PRODUCT_TRACE.getCode())
                .toList();

        if (rules.isEmpty()) {
            log.debug("No PRODUCT_TRACE rule for mission {}", ctx.getProductMissionId());
            return CapabilityResult.Skip;
        }

        String code = ctx.getProductCode();
        if (code == null || code.isEmpty()) {
            log.warn("PRODUCT_TRACE rule configured but no productCode provided");
            return CapabilityResult.Fail;
        }

        boolean matched = rules.stream().anyMatch(r -> BarcodeMatcher.matches(r, code));
        if (!matched) {
            log.warn("Product code '{}' does not match any rule", code);
            return CapabilityResult.Fail;
        }

        return CapabilityResult.Pass;
    }
}
