package com.tightening.lifecycle.capability;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.BarCodeMatchingRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.tightening.lifecycle.capability.CapabilityResult.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductBarCodeCheck")
class ProductBarCodeCheckTest {

    @Mock BarCodeMatchingRuleService ruleService;
    ProductBarCodeCheck cap;

    @BeforeEach
    void setUp() { cap = new ProductBarCodeCheck(ruleService); }

    @Test
    @DisplayName("id")
    void id() { assertThat(cap.id()).isEqualTo("ProductBarCodeCheck"); }

    @Test
    @DisplayName("无 PRODUCT_TRACE 规则 → Skip")
    void noRule() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask())
                .productCode("ABC").build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode())));

        assertThat(cap.execute(ctx)).isEqualTo(Skip);
    }

    @Test
    @DisplayName("有规则 + productCode 为空 → Fail")
    void rulePresentNoCode() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask())
                .productCode(null).build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                        .setSegments("[{\"s\":0,\"e\":3,\"v\":\"ABC\"}]")));

        assertThat(cap.execute(ctx)).isEqualTo(Fail);
    }

    @Test
    @DisplayName("有规则 + 匹配 → Pass")
    void ruleMatch() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask())
                .productCode("ABC123").build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                        .setSegments("[{\"s\":0,\"e\":3,\"v\":\"ABC\"}]")));

        assertThat(cap.execute(ctx)).isEqualTo(Pass);
    }

    @Test
    @DisplayName("有规则 + 不匹配 → Fail")
    void ruleNoMatch() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask())
                .productCode("XYZ123").build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.PRODUCT_TRACE.getCode())
                        .setSegments("[{\"s\":0,\"e\":3,\"v\":\"ABC\"}]")));

        assertThat(cap.execute(ctx)).isEqualTo(Fail);
    }
}
