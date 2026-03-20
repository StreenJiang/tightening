package com.tightening.device.handler.impl;

import com.tightening.service.DeviceService;
import org.springframework.stereotype.Component;

@Component
public class AtlasPF6000OPHandler extends AtlasPFSeriesHandler {
    public AtlasPF6000OPHandler(DeviceService deviceService) {
        super(deviceService);
    }
}
