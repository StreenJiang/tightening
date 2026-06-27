package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;

public class SendArrangerSignal implements Capability {
    @Override public String id() { return "SendArrangerSignal"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 0; }

    @Override
    public boolean precondition(MissionContext ctx) { return false; }

    @Override
    public CapabilityResult execute(MissionContext ctx) { return CapabilityResult.Skip; }
}
