package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ITool;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LockTools implements Capability {

    @Override public String id() { return "LockTools"; }
    @Override public Stage stage() { return Stage.FINALIZATION; }
    @Override public SubState subState() { return SubState.LOCKING_TOOLS; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        for (ITool tool : ctx.getDeviceRegistry().values()) {
            log.info("LockTools: locking deviceId={}", tool.id());
            tool.sendLock().whenComplete((ok, ex) -> {
                if (ex != null || !Boolean.TRUE.equals(ok)) {
                    log.warn("LockTools: deviceId={} lock failed (ok={})", tool.id(), ok, ex);
                }
            });
        }
        return CapabilityResult.Pass;
    }
}
