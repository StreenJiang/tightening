package com.tightening.dto;

import com.tightening.constant.WorkplaceStatus;
import java.util.Set;

public record WorkplaceStatusPayload(WorkplaceStatus status, Set<String> lockReasons) {}
