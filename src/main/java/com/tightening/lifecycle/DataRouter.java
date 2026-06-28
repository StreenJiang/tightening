package com.tightening.lifecycle;

import com.tightening.dto.TighteningDataDTO;

public interface DataRouter {
    void routeTighteningData(long deviceId, TighteningDataDTO dto);
}
