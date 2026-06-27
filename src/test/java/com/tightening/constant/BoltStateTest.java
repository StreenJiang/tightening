package com.tightening.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BoltState 枚举")
class BoltStateTest {

    @Test
    @DisplayName("fromCode 返回正确的 BoltState")
    void fromCodeShouldReturnCorrectBoltState() {
        assertThat(BoltState.fromCode(0)).isEqualTo(Optional.of(BoltState.PENDING));
        assertThat(BoltState.fromCode(1)).isEqualTo(Optional.of(BoltState.TIGHTENING));
        assertThat(BoltState.fromCode(2)).isEqualTo(Optional.of(BoltState.JUDGED_OK));
        assertThat(BoltState.fromCode(3)).isEqualTo(Optional.of(BoltState.JUDGED_NG));
    }

    @Test
    @DisplayName("无效 code 返回 empty")
    void fromCodeShouldReturnEmptyForInvalidCode() {
        assertThat(BoltState.fromCode(99)).isEmpty();
        assertThat(BoltState.fromCode(-1)).isEmpty();
    }

    @Test
    @DisplayName("所有 code 唯一")
    void codesShouldBeUnique() {
        var codes = new HashSet<Integer>();
        for (BoltState s : BoltState.values()) {
            assertThat(codes.add(s.getCode()))
                .withFailMessage("Duplicate code: %d", s.getCode())
                .isTrue();
        }
    }
}
