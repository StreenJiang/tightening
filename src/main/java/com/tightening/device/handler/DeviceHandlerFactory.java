package com.tightening.device.handler;

import com.tightening.constant.DeviceType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeviceHandlerFactory {

    private static DeviceHandlerFactory INSTANCE;
    private final Map<DeviceType, DeviceHandler> handlerMap = new ConcurrentHashMap<>();

    // Spring 自动注入所有 ADeviceHandler 实现
    public DeviceHandlerFactory(List<DeviceHandler> handlers) {
        // TODO: 这个需要思考是否有更好的设计
        INSTANCE = this; // 静态引用，供 Enum 使用
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

    public static DeviceHandler getHandlerStatic(DeviceType type) {
        // TODO: 这个同上，关联的
        if (INSTANCE == null) {
            throw new IllegalStateException("Factory not initialized yet");
        }
        return INSTANCE.getHandler(type);
    }
}
