package com.tightening.lifecycle.capability;

import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
import com.tightening.lifecycle.TaskContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stub Capabilities")
class StubCapabilityTest {

    @Test
    @DisplayName("SendArrangerSignal precondition=false when no arranger configured")
    void sendArrangerSignalShouldAlwaysSkip() {
        var cap = new SendArrangerSignal();
        var bolt = new ProductBolt();
        bolt.setId(1L);
        var ctx = TaskContext.builder()
            .boltConfigs(List.of(bolt))
            .build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("SendSetterSelector precondition=false when no setter configured")
    void sendSetterSelectorShouldAlwaysSkip() {
        var cap = new SendSetterSelector();
        var bolt = new ProductBolt();
        bolt.setId(1L);
        var ctx = TaskContext.builder()
            .boltConfigs(List.of(bolt))
            .build();
        assertThat(cap.precondition(ctx)).isFalse();
    }

    @Test
    @DisplayName("BoltBarCodeCheck precondition=true when bolt has barcode rule")
    void boltBarCodeCheckShouldPassWhenBoltHasRule() {
        var cap = new BoltBarCodeCheck();
        var bolt = new ProductBolt();
        bolt.setId(1L);
        var ctx = TaskContext.builder()
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
        var ctx = TaskContext.builder()
            .boltConfigs(List.of(bolt))
            .boltBarcodeRuleIds(Map.of())
            .build();
        assertThat(cap.precondition(ctx)).isFalse();
    }
}
