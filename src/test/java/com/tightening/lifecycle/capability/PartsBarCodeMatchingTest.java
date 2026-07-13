package com.tightening.lifecycle.capability;

import com.tightening.constant.BarCodeRuleType;
import com.tightening.entity.BarCodeMatchingRule;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
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
    @DisplayName("无 PARTS_BARCODE 规则 → Skip")
    void noRule() {
        var ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .partsCode("MAT").build();
        when(ruleService.listByMissionId(1L)).thenReturn(List.of());

        assertThat(cap.execute(ctx)).isEqualTo(Skip);
    }

    @Test
    @DisplayName("有规则 + partsCode 为空 → Fail")
    void rulePresentNoCode() {
        var ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .partsCode(null).build();
        when(ruleService.listByMissionId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.PARTS_BARCODE.getCode())
                        .setSegments("[{\"s\":0,\"e\":3,\"v\":\"MAT\"}]")));

        assertThat(cap.execute(ctx)).isEqualTo(Fail);
    }

    @Test
    @DisplayName("有规则 + 匹配 → Pass")
    void ruleMatch() {
        var ctx = MissionContext.builder()
                .productMissionId(1L).missionData(new ProductMission())
                .partsCode("MAT456").build();
        when(ruleService.listByMissionId(1L))
                .thenReturn(List.of(new BarCodeMatchingRule()
                        .setRuleType(BarCodeRuleType.PARTS_BARCODE.getCode())
                        .setSegments("[{\"s\":0,\"e\":3,\"v\":\"MAT\"}]")));

        assertThat(cap.execute(ctx)).isEqualTo(Pass);
    }
}
