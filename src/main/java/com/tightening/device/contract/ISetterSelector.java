package com.tightening.device.contract;

import java.util.concurrent.CompletableFuture;

public interface ISetterSelector extends IDevice {
    CompletableFuture<Boolean> writePosition(int position);
    CompletableFuture<Boolean> reset();
    int getPositionCount();
}
