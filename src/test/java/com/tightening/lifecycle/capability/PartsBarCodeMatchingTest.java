package com.tightening.lifecycle.capability;

import com.tightening.constant.BarCodeRuleType;
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
@DisplayName("PartsBarCodeMatching")
class PartsBarCodeMatchingTest {

    @Mock BarCodeMatchingRuleService ruleService;
    PartsBarCodeMatching cap;

    @BeforeEach
    void setUp() { cap = new PartsBarCodeMatching(ruleService); }

    @Test
    @DisplayName("id")
    void id() { assertThat(cap.id()).isEqualTo("PartsBarCodeMatching"); }

    @Test
    @DisplayName("无 MATERIAL_BARCODE 规则 → Skip")
    void noRule() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask())
                .partsCode("MAT").build();
        when(ruleService.listByTaskId(1L)).thenReturn(List.of());

        assertThat(cap.execute(ctx)).isEqualTo(Skip);
    }

    @Test
    @DisplayName("有规则 + partsCode 为空 → Fail")
    void rulePresentNoCode() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask())
                .partsCode(null).build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode())
                        .setSegments("[{\"s\":0,\"e\":3,\"v\":\"MAT\"}]")));

        assertThat(cap.execute(ctx)).isEqualTo(Fail);
    }

    @Test
    @DisplayName("有规则 + 匹配 → Pass")
    void ruleMatch() {
        var ctx = TaskContext.builder()
                .productTaskId(1L).taskData(new ProductTask())
                .partsCode("MAT456").build();
        when(ruleService.listByTaskId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.MATERIAL_BARCODE.getCode())
                        .setSegments("[{\"s\":0,\"e\":3,\"v\":\"MAT\"}]")));

        assertThat(cap.execute(ctx)).isEqualTo(Pass);
    }
}
