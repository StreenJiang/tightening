package com.tightening.judgment;

import com.tightening.constant.AtlasAngleStatus;
import com.tightening.constant.AtlasTorqueStatus;
import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AtlasJudgment 判定策略")
class AtlasJudgmentTest {

    private final AtlasJudgment judgment = new AtlasJudgment();

    @Test
    @DisplayName("三个状态全 OK → isOk=true")
    void allOk() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.OK.getCode());
        dto.setAngleStatus(AtlasAngleStatus.OK.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isTrue();
        assertThat(result.reason()).isEqualTo("OK");
    }

    @Test
    @DisplayName("tighteningStatus NG → isOk=false")
    void tighteningNg() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.NG.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.OK.getCode());
        dto.setAngleStatus(AtlasAngleStatus.OK.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }

    @Test
    @DisplayName("torqueStatus LOW → isOk=false")
    void torqueLow() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.LOW.getCode());
        dto.setAngleStatus(AtlasAngleStatus.OK.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }

    @Test
    @DisplayName("torqueStatus HIGH → isOk=false")
    void torqueHigh() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.HIGH.getCode());
        dto.setAngleStatus(AtlasAngleStatus.OK.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }

    @Test
    @DisplayName("angleStatus LOW → isOk=false")
    void angleLow() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.OK.getCode());
        dto.setAngleStatus(AtlasAngleStatus.LOW.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }

    @Test
    @DisplayName("angleStatus HIGH → isOk=false")
    void angleHigh() {
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(TighteningStatus.OK.getCode());
        dto.setTorqueStatus(AtlasTorqueStatus.OK.getCode());
        dto.setAngleStatus(AtlasAngleStatus.HIGH.getCode());

        JudgmentResult result = judgment.judge(dto);

        assertThat(result.isOk()).isFalse();
    }
}
