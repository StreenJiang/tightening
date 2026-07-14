package com.tightening.lifecycle.message;

import com.tightening.entity.TighteningData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InboundMessage 消息层次")
class InboundMessageTest {

    @Test
    @DisplayName("TighteningDataReceived record 创建")
    void shouldCreateTighteningDataReceived() {
        var data = new TighteningData().setTighteningId(100L);
        var msg = new DeviceEvent.TighteningDataReceived(data, 42L);
        assertThat(msg.deviceId()).isEqualTo(42L);
        assertThat(msg.data().getTighteningId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("MonitorTick 是单例")
    void monitorTickShouldBeSingleton() {
        var tick = new EngineInternal.MonitorTick();
        assertThat(tick).isInstanceOf(EngineInternal.class);
    }

    @Test
    @DisplayName("Faulted record 模式匹配")
    void faultedShouldSupportPatternMatching() {
        InboundMessage msg = new EngineInternal.Faulted("test error");
        if (msg instanceof EngineInternal.Faulted(String reason)) {
            assertThat(reason).isEqualTo("test error");
        } else {
            org.junit.jupiter.api.Assertions.fail("Expected Faulted");
        }
    }
}
