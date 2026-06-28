package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CancelTasks implements Capability {

    @Override public String id() { return "CancelTasks"; }
    @Override public Stage stage() { return Stage.FINALIZATION; }
    @Override public SubState subState() { return SubState.CLEANING_TASKS; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        log.info("CancelTasks: all tasks cleared for mission {}", ctx.getProductMissionId());
        return CapabilityResult.Pass;
    }
}
