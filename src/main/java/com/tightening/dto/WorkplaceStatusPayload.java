package com.tightening.dto;

import com.tightening.constant.WorkplaceStatus;
import java.util.Map;

public record WorkplaceStatusPayload(WorkplaceStatus status, Map<String, String> lockReasons) {}
