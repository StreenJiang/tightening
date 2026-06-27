package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubState 枚举")
class SubStateTest {

    @Test
    @DisplayName("fromCode 返回正确的 SubState")
    void fromCodeShouldReturnCorrectSubState() {
        assertThat(SubState.fromCode(4)).isEqualTo(Optional.of(SubState.SWITCH_BOLT));
        assertThat(SubState.fromCode(5)).isEqualTo(Optional.of(SubState.TIGHTENING_RECEIVED));
        assertThat(SubState.fromCode(6)).isEqualTo(Optional.of(SubState.JUDGING));
        assertThat(SubState.fromCode(99)).isEqualTo(Optional.of(SubState.FAULTED));
    }

    @Test
    @DisplayName("无效 code 返回 empty")
    void fromCodeShouldReturnEmptyForInvalidCode() {
        assertThat(SubState.fromCode(100)).isEmpty();
        assertThat(SubState.fromCode(-1)).isEmpty();
    }

    @Test
    @DisplayName("所有 code 唯一")
    void codesShouldBeUnique() {
        var codes = new HashSet<Integer>();
        for (SubState s : SubState.values()) {
            assertThat(codes.add(s.getCode()))
                .withFailMessage("Duplicate code: %d", s.getCode())
                .isTrue();
        }
    }
}
