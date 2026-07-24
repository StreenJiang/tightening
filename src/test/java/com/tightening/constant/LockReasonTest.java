package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LockReasonTest {

    @Test
    @DisplayName("每个 LockReason 应有非空的 key")
    void shouldHaveNonEmptyKey() {
        for (LockReason reason : LockReason.values()) {
            assertThat(reason.getKey()).isNotBlank();
        }
    }

    @Test
    @DisplayName("应包含 5 个枚举值")
    void shouldHaveFiveValues() {
        assertThat(LockReason.values()).hasSize(5);
    }
}
