package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkplaceStatusTest {

    @Test
    @DisplayName("应包含 4 个状态值")
    void shouldHaveFourValues() {
        assertThat(WorkplaceStatus.values()).hasSize(4);
        assertThat(WorkplaceStatus.valueOf("UNACTIVATED")).isNotNull();
        assertThat(WorkplaceStatus.valueOf("ACTIVATED")).isNotNull();
        assertThat(WorkplaceStatus.valueOf("OPERATION_ENABLE")).isNotNull();
        assertThat(WorkplaceStatus.valueOf("OPERATION_DISABLE")).isNotNull();
    }
}
