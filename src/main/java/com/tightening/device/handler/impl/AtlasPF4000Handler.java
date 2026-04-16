package com.tightening.device.handler.impl;

import com.tightening.config.ToolCommonConfig;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;

import org.springframework.stereotype.Component;

@Component
public class AtlasPF4000Handler extends AtlasPFSeriesHandler {
    public AtlasPF4000Handler(DeviceService deviceService,
                                TighteningDataService tighteningDataService,
                                ToolCommonConfig toolCommonConfig) {
        super(deviceService, tighteningDataService, toolCommonConfig);
    }
}
