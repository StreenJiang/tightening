package com.tightening.device.handler;

import com.tightening.constant.DeviceType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeviceHandlerFactory {

    private final Map<DeviceType, DeviceHandler> handlerMap = new ConcurrentHashMap<>();

    // Spring 自动注入所有 ADeviceHandler 实现
    public DeviceHandlerFactory(List<DeviceHandler> handlers) {
        for (DeviceHandler handler : handlers) {
            for (DeviceType type : DeviceType.values()) {
                if (type.getHandlerClass().isInstance(handler)) {
                    handlerMap.put(type, handler);
                    break;
                }
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
