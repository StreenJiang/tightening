package com.tightening.lifecycle.monitor;

import com.tightening.device.contract.ITool;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeviceConnectionMonitor implements PersistentMonitor {

    @Override
    public long intervalMs() {
        return 1000;
    }

    @Override
    public void execute(MissionContext ctx) {
        for (ITool tool : ctx.getDeviceRegistry().values()) {
            if (!tool.isConnected()) {
                log.warn("Device {} disconnected", tool.id());
            }
        }
    }
}
