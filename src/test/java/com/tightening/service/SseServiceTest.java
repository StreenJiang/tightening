package com.tightening.service;

import org.junit.jupiter.api.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SseServiceTest {

    private SseService service;

    @BeforeEach
    void setUp() {
        service = new SseService();
    }

    @AfterEach
    void tearDown() {
        service.closeDevice();
        service.closeWorkplace();
    }

    @Test
    @DisplayName("createDeviceEmitter() should return a non-null SseEmitter")
    void shouldCreateDeviceEmitter() {
        SseEmitter emitter = service.createDeviceEmitter();
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("createWorkplaceEmitter() should return a non-null SseEmitter")
    void shouldCreateWorkplaceEmitter() {
        SseEmitter emitter = service.createWorkplaceEmitter();
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("emitDevice() should not throw when emitter exists")
    void shouldEmitDeviceWithoutException() {
        service.createDeviceEmitter();
        assertThatCode(() -> service.emitDevice("device:status", Map.of("ts", "now"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("emitWorkplace() should not throw when emitter exists")
    void shouldEmitWorkplaceWithoutException() {
        service.createWorkplaceEmitter();
        assertThatCode(() -> service.emitWorkplace("task:control", Map.of("control", "stopped"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("closeDevice() then createDeviceEmitter() should return a new instance")
    void shouldAllowRecreateDeviceAfterClose() {
        SseEmitter first = service.createDeviceEmitter();
        service.closeDevice();
        SseEmitter second = service.createDeviceEmitter();
        assertThat(second).isNotSameAs(first);
    }

    @Test
    @DisplayName("closeWorkplace() then createWorkplaceEmitter() should return a new instance")
    void shouldAllowRecreateWorkplaceAfterClose() {
        SseEmitter first = service.createWorkplaceEmitter();
        service.closeWorkplace();
        SseEmitter second = service.createWorkplaceEmitter();
        assertThat(second).isNotSameAs(first);
    }
}
