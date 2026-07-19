package com.tightening.service;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.PrerequisiteType;
import com.tightening.dto.PrerequisiteSaveItem;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.ProductMission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionConfigValidatorTest {

    private final MissionConfigValidator validator = new MissionConfigValidator(null, null, null);

    @Test
    @DisplayName("segments: single char passes")
    void singleCharPasses() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setSegments("[{\"s\":3,\"e\":4,\"v\":\"A\"}]");
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }

    @Test
    @DisplayName("segments: range with matching length passes")
    void rangeMatchingLengthPasses() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setSegments("[{\"s\":2,\"e\":6,\"v\":\"ABCD\"}]");
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }

    @Test
    @DisplayName("segments: length mismatch throws")
    void mismatchThrows() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule()
                .setSegments("[{\"s\":2,\"e\":6,\"v\":\"ABC\"}]");
        assertThatThrownBy(() -> validator.validateKeyCharLength(rule))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不匹配");
    }

    @Test
    @DisplayName("null segments is no-op")
    void nullSegmentsNoOp() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule();
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }

    @Test
    @DisplayName("empty segments is no-op")
    void emptySegmentsNoOp() {
        BarCodeMatchingRule rule = new BarCodeMatchingRule().setSegments("");
        assertThatNoException().isThrownBy(() -> validator.validateKeyCharLength(rule));
    }

    @Nested
    @DisplayName("validateBarcodeRules")
    class ValidateBarcodeRules {

        @Test
        @DisplayName("产品码 > 1 条时抛异常")
        void shouldRejectMultipleProductTrace() {
            List<BarCodeMatchingRule> rules = List.of(
                new BarCodeMatchingRule().setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode()),
                new BarCodeMatchingRule().setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
            );

            assertThatThrownBy(() -> validator.validateBarcodeRules(rules))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("产品码规则最多 1 条");
        }

        @Test
        @DisplayName("物料码存在但无产品码时抛异常")
        void shouldRejectMaterialBarcodeWithoutProductTrace() {
            List<BarCodeMatchingRule> rules = List.of(
                new BarCodeMatchingRule().setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode())
            );

            assertThatThrownBy(() -> validator.validateBarcodeRules(rules))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须先有产品码规则");
        }

        @Test
        @DisplayName("产品码 1 条 + 物料码 2 条 -> 通过")
        void shouldAcceptValidCombination() {
            List<BarCodeMatchingRule> rules = List.of(
                new BarCodeMatchingRule().setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode()),
                new BarCodeMatchingRule().setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode()),
                new BarCodeMatchingRule().setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode())
            );

            assertThatCode(() -> validator.validateBarcodeRules(rules))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 列表不抛异常")
        void nullListNoOp() {
            assertThatCode(() -> validator.validateBarcodeRules(null))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validateInspectionChainSelfInspection")
    class ValidateInspectionChainSelfInspection {

        @Test
        @DisplayName("INSPECTION_CHAIN 前置但当前任务不是点检 -> 抛异常")
        void shouldRejectWhenSelfIsNotInspection() {
            ProductMission mission = new ProductMission().setIsInspection(0);
            List<PrerequisiteSaveItem> items = List.of(
                new PrerequisiteSaveItem().setPrerequisiteType(PrerequisiteType.INSPECTION_CHAIN)
            );

            assertThatThrownBy(() -> validator.validateInspectionChainSelfInspection(mission, items))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INSPECTION_CHAIN 的前置类型要求当前任务必须是点检任务");
        }

        @Test
        @DisplayName("无 INSPECTION_CHAIN 时不校验")
        void shouldSkipWhenNoInspectionChain() {
            ProductMission mission = new ProductMission().setIsInspection(0);
            List<PrerequisiteSaveItem> items = List.of(
                new PrerequisiteSaveItem().setPrerequisiteType(PrerequisiteType.SAME_TRACE)
            );

            assertThatCode(() -> validator.validateInspectionChainSelfInspection(mission, items))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 列表不抛异常")
        void nullListNoOp() {
            assertThatCode(() -> validator.validateInspectionChainSelfInspection(new ProductMission(), null))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validateBarcodeRuleForPrerequisite")
    class ValidateBarcodeRuleForPrerequisite {

        @Test
        @DisplayName("MATERIAL_TRACE + null 规则 -> 抛异常")
        void shouldRejectMaterialTraceWithNullRule() {
            assertThatThrownBy(() -> validator.validateBarcodeRuleForPrerequisite(null, PrerequisiteType.MATERIAL_TRACE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MATERIAL_TRACE 前置必须关联条码规则");
        }

        @Test
        @DisplayName("MATERIAL_TRACE + PRODUCT_TRACE 规则 -> 抛异常")
        void shouldRejectMaterialTraceWithNonMaterialRule() {
            BarCodeMatchingRule rule = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode());
            assertThatThrownBy(() -> validator.validateBarcodeRuleForPrerequisite(rule, PrerequisiteType.MATERIAL_TRACE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是 MATERIAL_BARCODE 类型");
        }

        @Test
        @DisplayName("非 MATERIAL_TRACE + 非 null 规则 -> 抛异常")
        void shouldRejectNonMaterialTraceWithRule() {
            BarCodeMatchingRule rule = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode());
            assertThatThrownBy(() -> validator.validateBarcodeRuleForPrerequisite(rule, PrerequisiteType.SAME_TRACE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有 MATERIAL_TRACE 前置可以关联条码规则");
        }

        @Test
        @DisplayName("SAME_TRACE + null 规则 -> 通过")
        void shouldAllowNonMaterialTraceWithNullRule() {
            assertThatCode(() -> validator.validateBarcodeRuleForPrerequisite(null, PrerequisiteType.SAME_TRACE))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("MATERIAL_TRACE + MATERIAL_BARCODE 规则 -> 通过")
        void shouldAllowMaterialTraceWithMaterialRule() {
            BarCodeMatchingRule rule = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode());
            assertThatCode(() -> validator.validateBarcodeRuleForPrerequisite(rule, PrerequisiteType.MATERIAL_TRACE))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("INSPECTION_CHAIN + null 规则 -> 通过")
        void shouldAllowInspectionChainWithNullRule() {
            assertThatCode(() -> validator.validateBarcodeRuleForPrerequisite(null, PrerequisiteType.INSPECTION_CHAIN))
                .doesNotThrowAnyException();
        }
    }
}
