package com.tightening.device.handler;

import com.tightening.constant.DeviceType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeviceHandlerFactory {

    private final Map<DeviceType, DeviceHandler> handlerMap = new ConcurrentHashMap<>();

    public DeviceHandlerFactory(List<DeviceHandler> handlers) {
        for (DeviceHandler handler : handlers) {
            for (DeviceType type : handler.getSupportedTypes()) {
                handlerMap.put(type, handler);
            }
        }
    }

    public DeviceHandler getHandler(DeviceType type) {
        DeviceHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No handler for: " + type);
        }
        return handler;
    }
}
