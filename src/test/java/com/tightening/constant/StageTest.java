package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stage 枚举")
class StageTest {

    @Test
    @DisplayName("fromCode 返回正确的 Stage")
    void fromCodeShouldReturnCorrectStage() {
        assertThat(Stage.fromCode(0)).isEqualTo(Optional.of(Stage.VALIDATION));
        assertThat(Stage.fromCode(1)).isEqualTo(Optional.of(Stage.ACTIVATION));
        assertThat(Stage.fromCode(2)).isEqualTo(Optional.of(Stage.OPERATION));
        assertThat(Stage.fromCode(3)).isEqualTo(Optional.of(Stage.FINALIZATION));
    }

    @Test
    @DisplayName("无效 code 返回 empty")
    void fromCodeShouldReturnEmptyForInvalidCode() {
        assertThat(Stage.fromCode(99)).isEmpty();
        assertThat(Stage.fromCode(-1)).isEmpty();
    }

    @Test
    @DisplayName("所有 code 唯一")
    void codesShouldBeUnique() {
        var codes = new HashSet<Integer>();
        for (Stage s : Stage.values()) {
            assertThat(codes.add(s.getCode()))
                .withFailMessage("Duplicate code: %d", s.getCode())
                .isTrue();
        }
    }
}
