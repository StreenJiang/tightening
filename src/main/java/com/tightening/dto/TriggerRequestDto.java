package com.tightening.dto;

import org.springframework.lang.Nullable;

public record TriggerRequestDto(
    @Nullable String productCode,
    @Nullable String partsCode
) {}
