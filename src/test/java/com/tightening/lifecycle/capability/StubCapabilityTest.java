package com.tightening.lifecycle.capability;

import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stub Capabilities")
class StubCapabilityTest {

    @Test
    @DisplayName("SendArrangerSignal precondition 始终返回 false")
    void sendArrangerSignalShouldAlwaysSkip() {
        var cap = new SendArrangerSignal();
        assertThat(cap.precondition(null)).isFalse();
    }

    @Test
    @DisplayName("SendSetterSelector precondition 始终返回 false")
    void sendSetterSelectorShouldAlwaysSkip() {
        var cap = new SendSetterSelector();
        assertThat(cap.precondition(null)).isFalse();
    }

    @Test
    @DisplayName("BoltBarCodeCheck precondition=true when bolt has barcode rule")
    void boltBarCodeCheckShouldPassWhenBoltHasRule() {
        var cap = new BoltBarCodeCheck();
        var bolt = new ProductBolt();
        bolt.setId(1L);
        var ctx = MissionContext.builder()
            .boltConfigs(List.of(bolt))
            .boltBarcodeRuleIds(Map.of(1L, 100L))
            .build();
        assertThat(cap.precondition(ctx)).isTrue();
    }

    @Test
    @DisplayName("BoltBarCodeCheck precondition=false when no rule for current bolt")
    void boltBarCodeCheckShouldSkipWhenBoltHasNoRule() {
        var cap = new BoltBarCodeCheck();
        var bolt = new ProductBolt();
        bolt.setId(1L);
        var ctx = MissionContext.builder()
            .boltConfigs(List.of(bolt))
            .boltBarcodeRuleIds(Map.of())
            .build();
        assertThat(cap.precondition(ctx)).isFalse();
    }
}
