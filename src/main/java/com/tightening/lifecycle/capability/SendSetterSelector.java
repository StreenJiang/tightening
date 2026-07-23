package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.TaskContext;

public class SendSetterSelector implements Capability {
    @Override public String id() { return "SendSetterSelector"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 1; }

    @Override
    public boolean precondition(TaskContext ctx) { return false; }

    @Override
    public CapabilityResult execute(TaskContext ctx) { return CapabilityResult.Skip; }
}
