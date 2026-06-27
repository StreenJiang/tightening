package com.tightening.lifecycle.monitor;

import com.tightening.lifecycle.MissionContext;

public interface PersistentMonitor {
    long intervalMs();
    void execute(MissionContext ctx);
}
