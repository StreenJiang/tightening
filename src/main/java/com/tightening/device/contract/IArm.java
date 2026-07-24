package com.tightening.device.contract;

import java.util.concurrent.CompletableFuture;

public interface IArm extends IDevice {
    CompletableFuture<Coordinates3D> getCurrentCoordinates();
}
