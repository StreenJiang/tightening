package com.tightening.device.handler.impl;

import com.tightening.config.FitConfig;
import com.tightening.service.DeviceService;
import org.springframework.stereotype.Component;

@Component
public class FitFTC6Handler extends FitSeriesHandler {
    public FitFTC6Handler(DeviceService deviceService, FitConfig fitConfig) {
        super(deviceService, fitConfig);
    }
}
