package com.tightening.service;

import com.tightening.constant.SseEventType;
import com.tightening.dto.SseEvent;
import org.junit.jupiter.api.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

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
        service.close();
    }

    @Test
    @DisplayName("create() should return a non-null SseEmitter")
    void shouldCreateSseEmitter() {
        SseEmitter emitter = service.create();
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("emit() should not throw when emitter exists")
    void shouldEmitWithoutException() {
        service.create();
        var event = new SseEvent(SseEventType.DEVICE_STATUS, "test", LocalDateTime.now());
        assertThatCode(() -> service.emit(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("close() then create() should return a new emitter instance")
    void shouldAllowRecreateAfterClose() {
        SseEmitter first = service.create();
        service.close();
        SseEmitter second = service.create();
        assertThat(second).isNotSameAs(first);
    }
}
