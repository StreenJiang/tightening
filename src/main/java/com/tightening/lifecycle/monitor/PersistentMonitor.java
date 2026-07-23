package com.tightening.lifecycle.monitor;

import com.tightening.lifecycle.TaskContext;

public interface PersistentMonitor {
    long intervalMs();
    void execute(TaskContext ctx);
}
