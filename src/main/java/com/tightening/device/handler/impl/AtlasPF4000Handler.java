package com.tightening.device.handler.impl;

import com.tightening.service.DeviceService;
import org.springframework.stereotype.Component;

@Component
public class AtlasPF4000Handler extends AtlasPFSeriesHandler {
    public AtlasPF4000Handler(DeviceService deviceService) {
        super(deviceService);
    }
}
