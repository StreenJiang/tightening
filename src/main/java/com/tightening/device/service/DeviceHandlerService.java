package com.tightening.device.service;

import org.springframework.stereotype.Service;

import com.tightening.constant.DeviceType;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.DeviceHandlerFactory;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DeviceHandlerService {
    private final DeviceHandlerFactory deviceHandlerFactory;

    public DeviceHandlerService(DeviceHandlerFactory deviceHandlerFactory) {
        this.deviceHandlerFactory = deviceHandlerFactory;
    }

    public DeviceHandler getHandler(DeviceType type) {
        return deviceHandlerFactory.getHandler(type);
    }

    @PostConstruct
    public void registerProvider() {
          DeviceType.initProvider(deviceHandlerFactory::getHandler);
          log.info("DeviceType handler provider registered");
      }
}
