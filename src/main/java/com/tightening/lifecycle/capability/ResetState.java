package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResetState implements Capability {

    @Override public String id() { return "ResetState"; }
    @Override public Stage stage() { return Stage.FINALIZATION; }
    @Override public SubState subState() { return SubState.RESETTING_STATE; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        ctx.getExtras().clear();
        ctx.getLockReasons().clear();
        log.info("ResetState: task context extras and lock reasons cleared");
        return CapabilityResult.Pass;
    }
}
