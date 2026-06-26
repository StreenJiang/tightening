package com.tightening.judgment;

import com.tightening.constant.AtlasAngleStatus;
import com.tightening.constant.AtlasTorqueStatus;
import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;

public class AtlasJudgment implements JudgmentStrategy {

    @Override
    public JudgmentResult judge(TighteningDataDTO dto) {
        if (dto.getTighteningStatus() != TighteningStatus.OK.getCode()) {
            return JudgmentResult.ng("tighteningStatus is NG");
        }
        if (dto.getTorqueStatus() != AtlasTorqueStatus.OK.getCode()) {
            return JudgmentResult.ng("torqueStatus is LOW or HIGH");
        }
        if (dto.getAngleStatus() != AtlasAngleStatus.OK.getCode()) {
            return JudgmentResult.ng("angleStatus is LOW or HIGH");
        }
        return JudgmentResult.ok();
    }
}
