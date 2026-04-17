package com.tightening.constant;

import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.impl.AtlasPF4000Handler;
import com.tightening.device.handler.impl.AtlasPF6000OPHandler;
import com.tightening.device.handler.impl.FitFTC6Handler;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

@Getter
public enum DeviceType {
    ATLAS_PF4000(1, "PF4000", AtlasPF4000Handler.class),
    ATLAS_PF6000_OP(2, "PF6000-OP", AtlasPF6000OPHandler.class),
    FIT_FTC6(3, "FIT-FTC6", FitFTC6Handler.class),
    ;

    private final int id;
    private final String name;
    private final Class<? extends DeviceHandler> handlerClass;
    private static Function<DeviceType, DeviceHandler> handlerProvider;

    DeviceType(int id, String name, Class<? extends DeviceHandler> handlerClass) {
        this.id = id;
        this.name = name;
        this.handlerClass = handlerClass;
    }

    public static DeviceType getType(int id) {
        Optional<DeviceType> first = Arrays
                .stream(DeviceType.values())
                .filter(t -> t.getId() == id)
                .findFirst();
        return first.orElse(null);
    }

    public static void initProvider(Function<DeviceType, DeviceHandler> provider) {
        handlerProvider = provider;
    }

    public DeviceHandler getHandler() {
        return Optional.ofNullable(handlerProvider)
                .map(provider -> provider.apply(this))
                .orElseThrow(() -> new IllegalStateException(
                        "Handler provider not initialized. Call DeviceType.initProvider() first."));
    }

    public static DeviceHandler getHandlerByTypeId(int id) {
        DeviceType type = getType(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown device type id: " + id);
        }
        return type.getHandler();
    }
}
