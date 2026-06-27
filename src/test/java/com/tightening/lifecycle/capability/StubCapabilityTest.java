package com.tightening.lifecycle.capability;

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
    @DisplayName("BoltBarCodeCheck precondition 始终返回 false")
    void boltBarCodeCheckShouldAlwaysSkip() {
        var cap = new BoltBarCodeCheck();
        assertThat(cap.precondition(null)).isFalse();
    }
}
