package com.tightening.judgment;

import com.tightening.constant.TighteningStatus;
import com.tightening.dto.TighteningDataDTO;

public class FitJudgment implements JudgmentStrategy {

    @Override
    public JudgmentResult judge(TighteningDataDTO dto) {
        if (dto.getTighteningStatus() != TighteningStatus.OK.getCode()) {
            return JudgmentResult.ng("tighteningStatus is NG");
        }
        return JudgmentResult.ok();
    }
}
