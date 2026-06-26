package com.tightening.judgment;

import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FitJudgment 判定策略")
class FitJudgmentTest {

    private final FitJudgment judgment = new FitJudgment();

    @Test
    @DisplayName("tighteningStatus OK → isOk=true")
    void tighteningOk() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isTrue();
        assertThat(result.reason()).isEqualTo("OK");
    }

    @Test
    @DisplayName("tighteningStatus NG → isOk=false")
    void tighteningNg() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.NG.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }
}
