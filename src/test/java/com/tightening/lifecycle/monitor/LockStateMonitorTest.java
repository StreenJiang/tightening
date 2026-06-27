package com.tightening.lifecycle.monitor;

import com.tightening.entity.ProductMission;
import com.tightening.lifecycle.LockMessage;
import com.tightening.lifecycle.MissionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("LockStateMonitor")
class LockStateMonitorTest {

    @Test
    @DisplayName("空 Context 不抛异常")
    void shouldNotThrowOnEmptyContext() {
        var monitor = new LockStateMonitor();
        var ctx = minimalContext();
        assertThatCode(() -> monitor.execute(ctx)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("intervalMs 为正")
    void intervalShouldBePositive() {
        assertThat(new LockStateMonitor().intervalMs()).isPositive();
    }

    @Test
    @DisplayName("有 MANUAL_LOCK 时正常执行")
    void shouldDetectManualLock() {
        var monitor = new LockStateMonitor();
        var ctx = minimalContext();
        ctx.getLockMessages().add(new LockMessage("MANUAL_LOCK", "operator"));
        assertThatCode(() -> monitor.execute(ctx)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DeviceConnectionMonitor intervalMs 为正")
    void deviceConnectionMonitorIntervalShouldBePositive() {
        assertThat(new DeviceConnectionMonitor().intervalMs()).isPositive();
    }

    private static MissionContext minimalContext() {
        return MissionContext.builder()
            .productMissionId(1L).missionData(new ProductMission())
            .boltConfigs(List.of()).deviceRegistry(Map.of())
            .shouldSelfLoop(false).build();
    }
}
