package com.tightening.device.handler;

import com.tightening.config.ToolCommonConfig;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.service.DeviceService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public abstract class ToolHandler extends TCPDeviceHandler {

    private final ToolCommonConfig toolCommonConfig;

    private final ReentrantLock stateLock = new ReentrantLock();  // 保护状态字段和冷却检查
    private final ReentrantLock pSetLock = new ReentrantLock();   // 保护 PSET 操作

    @Getter
    private volatile long lastEnableTime = 0;
    @Getter
    private volatile long lastDisableTime = 0;
    @Getter
    private volatile boolean isToolEnabled = false;

    public ToolHandler(DeviceService deviceService, ToolCommonConfig toolCommonConfig) {
        super(deviceService);
        this.toolCommonConfig = toolCommonConfig;
    }

    public CompletableFuture<Boolean> enableToolOp(long deviceId) {
        return changeToolState(true, deviceId, false);
    }

    public CompletableFuture<Boolean> disableToolOp(long deviceId) {
        return changeToolState(false, deviceId, false);
    }

    public CompletableFuture<Boolean> forceEnableToolOp(long deviceId) {
        return changeToolState(true, deviceId, true);
    }

    public CompletableFuture<Boolean> forceDisableToolOp(long deviceId) {
        return changeToolState(false, deviceId, true);
    }

    public CompletableFuture<Boolean> sendPSetOp(long deviceId, int pSet) {
        pSetLock.lock();
        try {
            log.debug("sendPSetOp: deviceId={}, pSet={}", deviceId, pSet);
            return sendPSetCmd(deviceId, pSet);
        } finally {
            pSetLock.unlock();
        }
    }

    public CompletableFuture<Boolean> sendHeartbeat(long deviceId) {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * 通用状态变更（带冷却控制，支持强制绕过）
     *
     * @param targetEnabled  true=启用，false=禁用
     * @param deviceId       设备ID
     * @param bypassCooldown true=绕过冷却检查，false=遵守冷却
     * @return CompletableFuture，操作结果
     */
    private CompletableFuture<Boolean> changeToolState(boolean targetEnabled, long deviceId,
                                                       boolean bypassCooldown) {
        String action = targetEnabled ? "enable" : "disable";
        long now = System.currentTimeMillis();

        // 1. 冷却检查与意图记录（同步）
        stateLock.lock();
        try {
            // 如果不是强制绕过，则进行常规冷却检查
            if (!bypassCooldown) {
                boolean oppositeState = targetEnabled != isToolEnabled; // 与当前状态相反时绕过冷却
                long lastTime = targetEnabled ? lastEnableTime : lastDisableTime;
                log.debug("{}ToolOp: deviceId={}, current isEnabled={}, oppositeState={}",
                          action, deviceId, isToolEnabled, oppositeState);

                if (!oppositeState && (now - lastTime) < toolCommonConfig.getEnableDisableCooldownMs()) {
                    log.debug("{}ToolOp rejected by cooldown: deviceId={}, elapsed={}ms",
                              action, deviceId, now - lastTime);
                    return CompletableFuture.completedFuture(false);
                }
            } else {
                log.debug("force{}Tool: deviceId={}, bypass cooldown", action, deviceId);
            }
        } finally {
            stateLock.unlock();
        }

        // 2. 调用底层异步操作
        CompletableFuture<Boolean> operation = targetEnabled ? enableTool(deviceId) : disableTool(deviceId);

        // 3. 操作完成后更新状态（如果成功）
        return operation.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("{}Tool exception: deviceId={}", action, deviceId, ex);
                return;
            }
            if (result != null && result) {
                stateLock.lock();
                try {
                    if (targetEnabled) {
                        isToolEnabled = true;
                        lastEnableTime = System.currentTimeMillis();
                        log.info("{}Tool enable succeeded: deviceId={}", action, deviceId);
                    } else {
                        isToolEnabled = false;
                        lastDisableTime = System.currentTimeMillis();
                        log.info("{}Tool disable succeeded: deviceId={}", action, deviceId);
                    }
                } finally {
                    stateLock.unlock();
                }
            } else {
                log.debug("{}Tool failed: deviceId={}", action, deviceId);
            }
        });
    }

    // ============== 抽象方法（子类必须实现，返回 CompletableFuture） ==============
    protected abstract CompletableFuture<Boolean> enableTool(long deviceId);
    protected abstract CompletableFuture<Boolean> disableTool(long deviceId);
    protected abstract CompletableFuture<Boolean> sendPSetCmd(long deviceId, int pSet);
}
