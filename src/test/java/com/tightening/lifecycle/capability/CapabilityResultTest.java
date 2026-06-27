package com.tightening.lifecycle.capability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CapabilityResult 枚举")
class CapabilityResultTest {

    @Test
    @DisplayName("包含四个值")
    void shouldHaveFourValues() {
        assertThat(CapabilityResult.values()).hasSize(4);
    }

    @Test
    @DisplayName("包含 Pass, Fail, Skip, Interrupt")
    void shouldContainAllExpectedValues() {
        assertThat(CapabilityResult.valueOf("Pass")).isNotNull();
        assertThat(CapabilityResult.valueOf("Fail")).isNotNull();
        assertThat(CapabilityResult.valueOf("Skip")).isNotNull();
        assertThat(CapabilityResult.valueOf("Interrupt")).isNotNull();
    }
}

@DisplayName("ErrorAction 枚举")
class ErrorActionTest {

    @Test
    @DisplayName("包含四个值")
    void shouldHaveFourValues() {
        assertThat(ErrorAction.values()).hasSize(4);
    }

    @Test
    @DisplayName("default onError 返回 FAIL_STAGE")
    void defaultOnErrorShouldReturnFailStage() {
        Capability cap = mock(Capability.class);
        when(cap.onError(any(), any())).thenCallRealMethod();
        assertThat(cap.onError(null, new RuntimeException()))
            .isEqualTo(ErrorAction.FAIL_STAGE);
    }
}
