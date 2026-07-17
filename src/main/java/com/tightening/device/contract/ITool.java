package com.tightening.device.contract;

import com.tightening.dto.TighteningDataDTO;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ITool extends IDevice {
    boolean isUnlocked();

    CompletableFuture<Boolean> sendLock();

    CompletableFuture<Boolean> sendUnlock();

    CompletableFuture<Boolean> sendPSet(int psetId);

    void onTighteningData(Consumer<TighteningDataDTO> callback);
}
