package com.tightening.lifecycle.monitor;

import com.tightening.constant.WorkplaceStatus;
import com.tightening.device.contract.ITool;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.WorkplaceStatusService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

@Slf4j
public class LockStateMonitor implements PersistentMonitor {

    private final WorkplaceStatusService wsService;

    public LockStateMonitor(WorkplaceStatusService wsService) {
        this.wsService = wsService;
    }

    @Override
    public long intervalMs() {
        return 50;
    }

    @Override
    public void execute(TaskContext ctx) {
        if (ctx.isBoltUnlockOverride()) {
            return;
        }

        boolean shouldLock = !ctx.getLockReasons().isEmpty();

        for (ITool tool : ctx.getDeviceRegistry().values()) {
            boolean currentlyLocked = !tool.isUnlocked();
            if (shouldLock == currentlyLocked) {
                continue;
            }

            if (shouldLock) {
                tool.sendLock();
                wsService.transitionTo(WorkplaceStatus.OPERATION_DISABLE,
                    new HashSet<>(ctx.getLockReasons()));
                log.debug("LockStateMonitor: lock — reasons: {}", ctx.getLockReasons());
            } else {
                tool.sendUnlock();
                wsService.transitionTo(WorkplaceStatus.OPERATION_ENABLE,
                    Set.of());
                log.debug("LockStateMonitor: unlock");
            }
        }
    }
}
