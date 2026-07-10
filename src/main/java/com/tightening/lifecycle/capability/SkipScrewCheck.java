package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SkipScrewCheck implements TriggerCapability {

    @Override public String id() { return "SkipScrewCheck"; }
    @Override public Stage stage() { return Stage.VALIDATION; }
    @Override public SubState subState() { return SubState.VALIDATING; }
    @Override public int priority() { return 3; }

    @Override
    public boolean precondition(MissionContext ctx) {
        return ctx.getMissionData() != null
            && Integer.valueOf(1).equals(ctx.getMissionData().getSkipScrew());
    }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        log.info("SkipScrew fast track for mission {}", ctx.getProductMissionId());
        return CapabilityResult.Interrupt;
    }
}
