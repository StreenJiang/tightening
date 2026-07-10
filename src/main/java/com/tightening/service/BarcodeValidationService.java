package com.tightening.service;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.entity.BarCodeMatchingRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.tightening.util.BarcodeMatcher;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BarcodeValidationService {

    private final BarCodeMatchingRuleService ruleService;

    public record ProductCodeResult(boolean matched, Long suggestedMissionId) {
        public static ProductCodeResult matchedOk() { return new ProductCodeResult(true, null); }
        public static ProductCodeResult notMatched() { return new ProductCodeResult(false, null); }
        public static ProductCodeResult wrongMission(Long id) { return new ProductCodeResult(false, id); }
    }

    public ProductCodeResult validateProductCode(Long missionId, String productCode) {
        List<BarCodeMatchingRule> rules = ruleService.listByMissionId(missionId).stream()
                .filter(r -> r.getRuleType() == BarCodeRuleType.PRODUCT_TRACE.getCode())
                .toList();

        if (rules.isEmpty()) return ProductCodeResult.matchedOk();
        if (rules.stream().anyMatch(r -> BarcodeMatcher.matches(r, productCode))) return ProductCodeResult.matchedOk();

        List<BarCodeMatchingRule> otherRules = ruleService.findProductTraceRulesExcluding(missionId);
        return otherRules.stream()
                .filter(r -> BarcodeMatcher.matches(r, productCode))
                .findFirst()
                .map(r -> ProductCodeResult.wrongMission(r.getProductMissionId()))
                .orElse(ProductCodeResult.notMatched());
    }

    public boolean validatePartsCode(Long missionId, String partsCode) {
        List<BarCodeMatchingRule> rules = ruleService.listByMissionId(missionId).stream()
                .filter(r -> r.getRuleType() == BarCodeRuleType.PARTS_BARCODE.getCode())
                .toList();

        if (rules.isEmpty()) return true;
        return rules.stream().anyMatch(r -> BarcodeMatcher.matches(r, partsCode));
    }

}
