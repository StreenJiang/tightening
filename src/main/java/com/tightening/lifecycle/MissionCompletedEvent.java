package com.tightening.lifecycle;

import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;

import java.util.List;

public record MissionCompletedEvent(
    Long missionId,
    ProductMission mission,
    List<ProductBolt> bolts,
    boolean ok) {
}
