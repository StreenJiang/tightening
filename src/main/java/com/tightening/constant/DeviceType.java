package com.tightening.constant;

import com.tightening.device.handler.DeviceHandler;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

@Getter
public enum DeviceType {
    ATLAS_PF4000(1, "PF4000"),
    ATLAS_PF6000_OP(2, "PF6000-OP"),
    FIT_FTC6(3, "FIT-FTC6"),
    SUDONG_X7(4, "SUDONG-X7"),
    ;

    private final int id;
    private final String name;
    private static Function<DeviceType, DeviceHandler> handlerProvider;

    DeviceType(int id, String name) {
        this.id = id;
        this.name = name;
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
