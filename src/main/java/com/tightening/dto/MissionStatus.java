package com.tightening.dto;

public record MissionStatus(
    String status,
    String stage,
    String subState,
    int currentBoltIndex,
    int totalBolts,
    Long missionRecordId
) {}
