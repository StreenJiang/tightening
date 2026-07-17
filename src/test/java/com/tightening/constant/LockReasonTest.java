package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LockReasonTest {

    @Test
    @DisplayName("每个 LockReason 应有非空的 key 和 displayName")
    void shouldHaveNonEmptyKeyAndDisplayName() {
        for (LockReason reason : LockReason.values()) {
            assertThat(reason.getKey()).isNotBlank();
            assertThat(reason.getDisplayName()).isNotBlank();
        }
    }

    @Test
    @DisplayName("key 和 displayName 应不同（key 为英文标识，displayName 为中文展示）")
    void keyAndDisplayNameShouldDiffer() {
        for (LockReason reason : LockReason.values()) {
            assertThat(reason.getKey()).isNotEqualTo(reason.getDisplayName());
        }
    }

    @Test
    @DisplayName("应包含 5 个枚举值")
    void shouldHaveFiveValues() {
        assertThat(LockReason.values()).hasSize(5);
    }
}
