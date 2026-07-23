package com.tightening.service;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.entity.BarCodeMatchingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BarcodeValidationService")
class BarcodeValidationServiceTest {

    @Mock BarCodeMatchingRuleService ruleService;

    BarcodeValidationService service;

    @BeforeEach
    void setUp() { service = new BarcodeValidationService(ruleService); }

    @Nested
    @DisplayName("validateProductCode")
    class ValidateProductCode {

        @Test
        @DisplayName("无 PRODUCT_TRACE 规则 → matched")
        void noProductTraceRule() {
            when(ruleService.listByTaskId(1L)).thenReturn(List.of());

            var result = service.validateProductCode(1L, "ABC123");

            assertThat(result.matched()).isTrue();
            assertThat(result.suggestedTaskId()).isNull();
        }

        @Test
        @DisplayName("有规则且位置匹配 → matched")
        void positionMatch() {
            var rule = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                    .setSegments("[{\"s\":0,\"e\":2,\"v\":\"AB\"}]")
                    .setExpectedLength(6).setProductTaskId(1L);
            when(ruleService.listByTaskId(1L)).thenReturn(List.of(rule));

            var result = service.validateProductCode(1L, "ABC123");

            assertThat(result.matched()).isTrue();
        }

        @Test
        @DisplayName("有规则但不匹配当前 Task → 查其它 Task")
        void wrongTask() {
            var rule1 = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                    .setSegments("[{\"s\":0,\"e\":1,\"v\":\"X\"}]")
                    .setExpectedLength(6).setProductTaskId(1L);
            when(ruleService.listByTaskId(1L)).thenReturn(List.of(rule1));
            // 模拟全库查询：只有 task 2 匹配
            when(ruleService.findProductTraceRulesExcluding(1L)).thenReturn(List.of(
                new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                    .setSegments("[{\"s\":0,\"e\":1,\"v\":\"A\"}]")
                    .setExpectedLength(6).setProductTaskId(2L)
            ));

            var result = service.validateProductCode(1L, "ABC123");

            assertThat(result.matched()).isFalse();
            assertThat(result.suggestedTaskId()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("validatePartsCode")
    class ValidatePartsCode {

        @Test
        @DisplayName("无 MATERIAL_BARCODE 规则 → true")
        void noPartsRule() {
            when(ruleService.listByTaskId(1L)).thenReturn(List.of());

            assertThat(service.validatePartsCode(1L, "MAT456")).isTrue();
        }

        @Test
        @DisplayName("有规则且匹配 → true")
        void match() {
            var rule = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode())
                    .setSegments("[{\"s\":0,\"e\":2,\"v\":\"MA\"}]")
                    .setExpectedLength(6).setProductTaskId(1L);
            when(ruleService.listByTaskId(1L)).thenReturn(List.of(rule));

            assertThat(service.validatePartsCode(1L, "MAT456")).isTrue();
        }

        @Test
        @DisplayName("有规则但不匹配 → false")
        void noMatch() {
            var rule = new BarCodeMatchingRule()
                    .setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode())
                    .setSegments("[{\"s\":0,\"e\":2,\"v\":\"XX\"}]")
                    .setExpectedLength(6).setProductTaskId(1L);
            when(ruleService.listByTaskId(1L)).thenReturn(List.of(rule));

            assertThat(service.validatePartsCode(1L, "MAT456")).isFalse();
        }
    }
}
