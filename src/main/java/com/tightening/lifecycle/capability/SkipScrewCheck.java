package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SkipScrewCheck implements TriggerCapability {

    @Override public String id() { return "SkipScrewCheck"; }
    @Override public Stage stage() { return Stage.VALIDATION; }
    @Override public SubState subState() { return SubState.VALIDATING; }
    @Override public int priority() { return 3; }

    @Override
    public boolean precondition(TaskContext ctx) {
        return ctx.getTaskData() != null
            && Integer.valueOf(1).equals(ctx.getTaskData().getSkipScrew());
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        log.info("SkipScrew fast track for task {}", ctx.getProductTaskId());
        return CapabilityResult.Interrupt;
    }
}
