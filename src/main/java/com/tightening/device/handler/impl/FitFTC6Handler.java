package com.tightening.device.handler.impl;

import com.tightening.config.FitConfig;
import com.tightening.config.ToolCommonConfig;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;

import org.springframework.stereotype.Component;

@Component
public class FitFTC6Handler extends FitSeriesHandler {
    public FitFTC6Handler(DeviceService deviceService,
                          FitConfig fitConfig,
                          TighteningDataService tighteningDataService,
                          ToolCommonConfig toolCommonConfig) {
        super(deviceService, fitConfig, tighteningDataService, toolCommonConfig);
    }
}
