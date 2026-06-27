package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ControllerStatusCheck implements Capability {

    @Override public String id() { return "ControllerStatusCheck"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.JUDGING; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        var data = ctx.getCurrentOperationData();
        if (data == null) {
            log.warn("No current operation data");
            return CapabilityResult.Fail;
        }
        ctx.setTighteningStatus(data.getTighteningStatus());
        log.debug("ControllerStatusCheck: status={}", data.getTighteningStatus());
        return CapabilityResult.Pass;  // NG 也不阻断
    }
}
