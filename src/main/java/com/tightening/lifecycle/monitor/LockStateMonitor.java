package com.tightening.lifecycle.monitor;

import com.tightening.lifecycle.LockMessage;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LockStateMonitor implements PersistentMonitor {

    @Override
    public long intervalMs() {
        return 50;
    }

    @Override
    public void execute(MissionContext ctx) {
        for (LockMessage lm : ctx.getLockMessages()) {
            if (lm.isManual()) {
                log.debug("LockStateMonitor: {} — {}", lm.source(), lm.reason());
            }
        }
    }
}
