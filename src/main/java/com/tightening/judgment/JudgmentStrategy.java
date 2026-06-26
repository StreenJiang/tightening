package com.tightening.judgment;

import com.tightening.dto.TighteningDataDTO;

public interface JudgmentStrategy {
    JudgmentResult judge(TighteningDataDTO dto);
}
