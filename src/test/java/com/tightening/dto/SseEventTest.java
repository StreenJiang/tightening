package com.tightening.dto;

import com.tightening.constant.SseEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventTest {

    @Test
    @DisplayName("SseEvent record 应正确存储 type/payload/timestamp")
    void shouldStoreFields() {
        var now = LocalDateTime.now();
        var payload = Map.of("key", "value");
        var event = new SseEvent(SseEventType.DEVICE_STATUS, payload, now);

        assertThat(event.type()).isEqualTo(SseEventType.DEVICE_STATUS);
        assertThat(event.payload()).isSameAs(payload);
        assertThat(event.timestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("WorkplaceStatusPayload 应正确存储 status 和 lockReasons")
    void shouldStoreWorkplaceStatusPayload() {
        var reasons = Map.of("pSetSending", "程序号下发中");
        var payload = new WorkplaceStatusPayload(
                com.tightening.constant.WorkplaceStatus.OPERATION_ENABLE, reasons);

        assertThat(payload.status()).isEqualTo(
                com.tightening.constant.WorkplaceStatus.OPERATION_ENABLE);
        assertThat(payload.lockReasons()).containsEntry("pSetSending", "程序号下发中");
    }
}
