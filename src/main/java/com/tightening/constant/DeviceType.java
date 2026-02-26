package com.tightening.constant;

import com.tightening.device.handler.ADeviceHandler;
import com.tightening.device.handler.AtlasPF4000Handler;
import com.tightening.device.handler.AtlasPF6000OPHandler;
import lombok.Getter;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Optional;

@Getter
public enum DeviceType {
    ATLAS_PF4000(1, "PF4000", AtlasPF4000Handler.class),
    ATLAS_PF6000_OP(2, "PF6000-OP", AtlasPF6000OPHandler.class),
    ;

    private final int id;
    private final String name;
    private final Class<? extends ADeviceHandler> handlerClass;

    DeviceType(int id, String name, Class<? extends ADeviceHandler> handlerClass) {
        this.id = id;
        this.name = name;
        this.handlerClass = handlerClass;
    }

    public static DeviceType getType(int id) {
        Optional<DeviceType> first = Arrays.stream(DeviceType.values()).filter(
                t -> t.getId() == id).findFirst();
        return first.orElse(null);
    }

    public ADeviceHandler createHandler() {
        try {
            return handlerClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
