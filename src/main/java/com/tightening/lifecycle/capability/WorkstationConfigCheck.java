package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkstationConfigCheck implements Capability {

    @Override public String id() { return "WorkstationConfigCheck"; }
    @Override public Stage stage() { return Stage.VALIDATION; }
    @Override public SubState subState() { return SubState.VALIDATING; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        if (ctx.getBoltConfigs() == null || ctx.getBoltConfigs().isEmpty()) {
            log.warn("WorkstationConfigCheck FAIL: no bolt configs");
            return CapabilityResult.Fail;
        }
        if (ctx.getDeviceRegistry() == null || ctx.getDeviceRegistry().isEmpty()) {
            log.warn("WorkstationConfigCheck FAIL: no devices in registry");
            return CapabilityResult.Fail;
        }
        if (ctx.getMissionData() == null) {
            log.warn("WorkstationConfigCheck FAIL: no mission data");
            return CapabilityResult.Fail;
        }
        log.info("WorkstationConfigCheck PASS: {} bolts, {} devices",
                ctx.totalBolts(), ctx.getDeviceRegistry().size());
        return CapabilityResult.Pass;
    }
}
