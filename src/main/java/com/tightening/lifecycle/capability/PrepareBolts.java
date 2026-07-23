package com.tightening.lifecycle.capability;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class PrepareBolts implements Capability {

    @Override public String id() { return "PrepareBolts"; }
    @Override public Stage stage() { return Stage.ACTIVATION; }
    @Override public SubState subState() { return SubState.PREPARING; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        if (ctx.getBoltConfigs() == null || ctx.getBoltConfigs().isEmpty()) {
            log.warn("No bolts configured for task {}", ctx.getProductTaskId());
            return CapabilityResult.Fail;
        }
        int count = ctx.getBoltConfigs().size();
        BoltState[] states = new BoltState[count];
        Arrays.fill(states, BoltState.PENDING);
        ctx.setBoltStates(states);
        ctx.setCurrentBoltIndex(0);
        ctx.setCurrentSideIndex(0);
        log.info("PrepareBolts: {} bolts initialized", count);
        return CapabilityResult.Pass;
    }
}
