package com.tightening.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class ThreadUtils {

    private ThreadUtils() {}

    public static ScheduledExecutorService newDaemonScheduledExecutor(String threadName) {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }
}
