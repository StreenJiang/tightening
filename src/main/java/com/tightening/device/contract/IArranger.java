package com.tightening.device.contract;

import java.util.concurrent.CompletableFuture;

public interface IArranger extends IDevice {
    CompletableFuture<Boolean> sendPulse(int[] channels, int pulseWidthMs);
    CompletableFuture<int[]> getOutputStatus();
    CompletableFuture<int[]> getInputStatus();
    CompletableFuture<Boolean> reset();
}
