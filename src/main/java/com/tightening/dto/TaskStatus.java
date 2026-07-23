package com.tightening.dto;

public record TaskStatus(
    String status,
    String stage,
    String subState,
    int currentBoltIndex,
    int totalBolts,
    Long taskRecordId
) {}
