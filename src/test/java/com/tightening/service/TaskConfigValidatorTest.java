package com.tightening.service;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.PrerequisiteType;
import com.tightening.dto.PrerequisiteDetailItem;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.ProductTask;
import com.tightening.i18n.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskConfigValidatorTest {

    private final TaskConfigValidator validator = new TaskConfigValidator(null, null, null);

    private void assertBusinessError(ThrowingCallable runnable, String expectedErrorCode) {
        assertThatThrownBy(runnable)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(expectedErrorCode);
    }

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
        assertBusinessError(() -> validator.validateKeyCharLength(rule), "barcode.segment_length_error");
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

            assertBusinessError(() -> validator.validateBarcodeRules(rules), "validation.product_code_rule_limit");
        }

        @Test
        @DisplayName("物料码存在但无产品码时抛异常")
        void shouldRejectMaterialBarcodeWithoutProductTrace() {
            List<BarCodeMatchingRule> rules = List.of(
                new BarCodeMatchingRule().setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode())
            );

            assertBusinessError(() -> validator.validateBarcodeRules(rules), "validation.product_code_required");
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
            ProductTask task = new ProductTask().setIsInspection(0);
            List<PrerequisiteDetailItem> items = List.of(
                new PrerequisiteDetailItem().setPrerequisiteType(PrerequisiteType.INSPECTION_CHAIN)
            );

            assertBusinessError(() -> validator.validateInspectionChainSelfInspection(task, items), "prerequisite.inspection_chain_self_inspection");
        }

        @Test
        @DisplayName("无 INSPECTION_CHAIN 时不校验")
        void shouldSkipWhenNoInspectionChain() {
            ProductTask task = new ProductTask().setIsInspection(0);
            List<PrerequisiteDetailItem> items = List.of(
                new PrerequisiteDetailItem().setPrerequisiteType(PrerequisiteType.SAME_TRACE)
            );

            assertThatCode(() -> validator.validateInspectionChainSelfInspection(task, items))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 列表不抛异常")
        void nullListNoOp() {
            assertThatCode(() -> validator.validateInspectionChainSelfInspection(new ProductTask(), null))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validateBarcodeRuleForPrerequisite")
    class ValidateBarcodeRuleForPrerequisite {

        @Test
        @DisplayName("MATERIAL_TRACE + null 规则 -> 抛异常")
        void shouldRejectMaterialTraceWithNullRule() {
            assertBusinessError(() -> validator.validateBarcodeRuleForPrerequisite(null, PrerequisiteType.MATERIAL_TRACE), "prerequisite.material_trace_rule");
        }

        @Test
        @DisplayName("MATERIAL_TRACE + PRODUCT_TRACE 规则 -> 抛异常")
        void shouldRejectMaterialTraceWithNonMaterialRule() {
            BarCodeMatchingRule rule = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode());
            assertBusinessError(() -> validator.validateBarcodeRuleForPrerequisite(rule, PrerequisiteType.MATERIAL_TRACE), "prerequisite.material_barcode_type");
        }

        @Test
        @DisplayName("非 MATERIAL_TRACE + 非 null 规则 -> 抛异常")
        void shouldRejectNonMaterialTraceWithRule() {
            BarCodeMatchingRule rule = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode());
            assertBusinessError(() -> validator.validateBarcodeRuleForPrerequisite(rule, PrerequisiteType.SAME_TRACE), "prerequisite.only_material_can_have_barcode");
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
