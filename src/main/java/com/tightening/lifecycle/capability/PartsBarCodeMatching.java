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
public class PartsBarCodeMatching implements TriggerCapability {

    private final BarCodeMatchingRuleService ruleService;

    @Override public String id() { return "PartsBarCodeMatching"; }
    @Override public Stage stage() { return Stage.VALIDATION; }
    @Override public SubState subState() { return SubState.VALIDATING; }
    @Override public int priority() { return 2; }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        List<BarCodeMatchingRule> rules = ruleService.listByMissionId(ctx.getProductMissionId()).stream()
                .filter(r -> r.getRuleType() == BarCodeRuleType.MATERIAL_BARCODE.getCode())
                .toList();

        if (rules.isEmpty()) {
            log.debug("No MATERIAL_BARCODE rule for mission {}", ctx.getProductMissionId());
            return CapabilityResult.Skip;
        }

        String code = ctx.getPartsCode();
        if (code == null || code.isEmpty()) {
            log.warn("MATERIAL_BARCODE rule configured but no partsCode provided");
            return CapabilityResult.Fail;
        }

        boolean matched = rules.stream().anyMatch(r -> BarcodeMatcher.matches(r, code));
        if (!matched) {
            log.warn("Parts code '{}' does not match any rule", code);
            return CapabilityResult.Fail;
        }

        return CapabilityResult.Pass;
    }
}
