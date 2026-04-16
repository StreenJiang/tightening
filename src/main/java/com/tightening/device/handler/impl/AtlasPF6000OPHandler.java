package com.tightening.device.handler.impl;

import com.tightening.config.ToolCommonConfig;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;

import org.springframework.stereotype.Component;

@Component
public class AtlasPF6000OPHandler extends AtlasPFSeriesHandler {
    public AtlasPF6000OPHandler(DeviceService deviceService,
                                TighteningDataService tighteningDataService,
                                ToolCommonConfig toolCommonConfig) {
        super(deviceService, tighteningDataService, toolCommonConfig);
    }
}
