package com.tightening.constant;

import com.tightening.device.handler.DeviceHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceTypeTest {

    @AfterEach
    void tearDown() {
        DeviceType.initProvider(null);
    }

    @Test
    void getType_validId() {
        assertThat(DeviceType.getType(1)).isEqualTo(DeviceType.ATLAS_PF4000);
        assertThat(DeviceType.getType(2)).isEqualTo(DeviceType.ATLAS_PF6000_OP);
        assertThat(DeviceType.getType(3)).isEqualTo(DeviceType.FIT_FTC6);
    }

    @Test
    void getType_invalidId() {
        assertThat(DeviceType.getType(999)).isNull();
        assertThat(DeviceType.getType(-1)).isNull();
    }

    @Test
    void getId() {
        assertThat(DeviceType.ATLAS_PF4000.getId()).isEqualTo(1);
        assertThat(DeviceType.ATLAS_PF6000_OP.getId()).isEqualTo(2);
        assertThat(DeviceType.FIT_FTC6.getId()).isEqualTo(3);
    }

    @Test
    void getName() {
        assertThat(DeviceType.ATLAS_PF4000.getName()).isEqualTo("PF4000");
        assertThat(DeviceType.ATLAS_PF6000_OP.getName()).isEqualTo("PF6000-OP");
        assertThat(DeviceType.FIT_FTC6.getName()).isEqualTo("FIT-FTC6");
    }

    @Test
    void getHandler_providerNotInit() {
        assertThatThrownBy(() -> DeviceType.ATLAS_PF4000.getHandler())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Handler provider not initialized");
    }

    @Test
    void getHandler_providerInit() {
        DeviceHandler mockHandler = new DeviceHandler() {
            @Override public void connect(long deviceId) {}
            @Override public void disconnect(long deviceId) {}
            @Override public DeviceStatus getStatus(long deviceId) { return null; }
            @Override public Set<DeviceType> getSupportedTypes() { return Set.of(); }
        };
        DeviceType.initProvider(type -> mockHandler);
        assertThat(DeviceType.ATLAS_PF4000.getHandler()).isSameAs(mockHandler);
    }

    @Test
    void getHandlerByTypeId_valid() {
        DeviceHandler mockHandler = new DeviceHandler() {
            @Override public void connect(long deviceId) {}
            @Override public void disconnect(long deviceId) {}
            @Override public DeviceStatus getStatus(long deviceId) { return null; }
            @Override public Set<DeviceType> getSupportedTypes() { return Set.of(); }
        };
        DeviceType.initProvider(type -> mockHandler);
        assertThat(DeviceType.getHandlerByTypeId(1)).isSameAs(mockHandler);
    }

    @Test
    void getHandlerByTypeId_invalid() {
        assertThatThrownBy(() -> DeviceType.getHandlerByTypeId(999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    @Test
    void values_count() {
        assertThat(DeviceType.values()).hasSize(3);
    }

    @Test
    void codes_unique() {
        long distinctCount = java.util.Arrays.stream(DeviceType.values())
                .mapToInt(DeviceType::getId)
                .distinct()
                .count();
        assertThat(distinctCount).isEqualTo(DeviceType.values().length);
    }
}
