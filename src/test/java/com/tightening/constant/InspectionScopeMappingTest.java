package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InspectionScope 枚举持久化映射")
class InspectionScopeMappingTest {

    @Test
    @DisplayName("ALL → code=1 → fromCode(1) 回 ALL")
    void allRoundTrip() {
        assertThat(InspectionScope.ALL.getCode()).isEqualTo(1);
        assertThat(InspectionScope.fromCode(1)).isEqualTo(InspectionScope.ALL);
    }

    @Test
    @DisplayName("NONE → code=0 → fromCode(0) 回 NONE")
    void noneRoundTrip() {
        assertThat(InspectionScope.NONE.getCode()).isEqualTo(0);
        assertThat(InspectionScope.fromCode(0)).isEqualTo(InspectionScope.NONE);
    }

    @Test
    @DisplayName("CHOSEN → code=2 → fromCode(2) 回 CHOSEN")
    void chosenRoundTrip() {
        assertThat(InspectionScope.CHOSEN.getCode()).isEqualTo(2);
        assertThat(InspectionScope.fromCode(2)).isEqualTo(InspectionScope.CHOSEN);
    }

    @Test
    @DisplayName("未知 code 抛 IllegalArgumentException")
    void unknownCodeShouldThrow() {
        assertThatThrownBy(() -> InspectionScope.fromCode(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("@EnumValue 和 @JsonValue 都注解在 code 字段上")
    void annotationsShouldBeOnCodeField() throws Exception {
        var field = InspectionScope.class.getDeclaredField("code");
        assertThat(field.isAnnotationPresent(com.baomidou.mybatisplus.annotation.EnumValue.class)).isTrue();
        assertThat(field.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonValue.class)).isTrue();
    }
}
