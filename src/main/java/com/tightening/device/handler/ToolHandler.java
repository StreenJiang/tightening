package com.tightening.device.handler;

import com.tightening.config.ToolCommonConfig;
import com.tightening.device.DeviceHolder;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.dto.CurveDataDTO;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.CurveData;
import com.tightening.entity.TighteningData;
import com.tightening.service.CurveDataService;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;
import com.tightening.util.Converter;

import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class ToolHandler extends TCPDeviceHandler {

    @Getter
    private final TighteningDataService tighteningDataService;
    @Getter
    private final CurveDataService curveDataService;
    private final ToolCommonConfig toolCommonConfig;

    public ToolHandler(NioEventLoopGroup group,
                       DeviceService deviceService,
                       TighteningDataService tighteningDataService,
                       CurveDataService curveDataService,
                       ToolCommonConfig toolCommonConfig) {
        super(group, deviceService);
        this.tighteningDataService = tighteningDataService;
        this.curveDataService = curveDataService;
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
        DeviceHolder holder = getHolder(deviceId);
        holder.getPSetLock().lock();
        try {
            log.debug("sendPSetOp: deviceId={}, pSet={}", deviceId, pSet);
            return sendPSetCmd(deviceId, pSet);
        } finally {
            holder.getPSetLock().unlock();
        }
    }

    public boolean isToolEnabled(long deviceId) {
        return getHolder(deviceId).isToolEnabled();
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
        DeviceHolder holder = getHolder(deviceId);

        holder.getStateLock().lock();
        try {
            if (!bypassCooldown) {
                boolean oppositeState = targetEnabled != holder.isToolEnabled();
                long lastTime = targetEnabled ? holder.getLastEnableTime() : holder.getLastDisableTime();
                log.debug("{}ToolOp: deviceId={}, current isEnabled={}, oppositeState={}",
                          action, deviceId, holder.isToolEnabled(), oppositeState);

                if (!oppositeState && (now - lastTime) < toolCommonConfig.getEnableDisableCooldownMs()) {
                    log.debug("{}ToolOp rejected by cooldown: deviceId={}, elapsed={}ms",
                              action, deviceId, now - lastTime);
                    return CompletableFuture.completedFuture(false);
                }
            } else {
                log.debug("force{}Tool: deviceId={}, bypass cooldown", action, deviceId);
            }
            setLastTime(holder, targetEnabled, now);
        } finally {
            holder.getStateLock().unlock();
        }

        CompletableFuture<Boolean> operation = targetEnabled ? enableTool(deviceId) : disableTool(deviceId);

        return operation.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("{}Tool exception: deviceId={}", action, deviceId, ex);
                setLastTime(holder, targetEnabled, 0);
                return;
            }
            if (result != null && result) {
                holder.getStateLock().lock();
                try {
                    if (targetEnabled) {
                        holder.setToolEnabled(true);
                    } else {
                        holder.setToolEnabled(false);
                    }
                } finally {
                    holder.getStateLock().unlock();
                }
                log.info("{}Tool succeeded: deviceId={}", action, deviceId);
            } else {
                log.debug("{}Tool failed: deviceId={}", action, deviceId);
                setLastTime(holder, targetEnabled, 0);
            }
        });
    }

    private static void setLastTime(DeviceHolder holder, boolean targetEnabled, long time) {
        holder.getStateLock().lock();
        try {
            if (targetEnabled) {
                holder.setLastEnableTime(time);
            } else {
                holder.setLastDisableTime(time);
            }
        } finally {
            holder.getStateLock().unlock();
        }
    }

    // ============== 数据回调（InBoundHandler 解析后委托给这里） ==============

    public void handleTighteningData(TighteningDataDTO dto, Channel channel) {
        TighteningData data = Converter.dto2Entity(dto, TighteningData::new);
        TCPDeviceHandler.applyToolTypeName(channel, data);
        tighteningDataService.save(data);
    }

    public void handleCurveData(CurveDataDTO dto, Channel channel) {
        CurveData data = Converter.dto2Entity(dto, CurveData::new);
        curveDataService.save(data);
    }

    public void handleAlarm(String alarmMsg, long deviceId) {
        log.warn("Alarm from device {}: {}", deviceId, alarmMsg);
    }

    // ============== 抽象方法（子类必须实现，返回 CompletableFuture） ==============
    protected abstract CompletableFuture<Boolean> enableTool(long deviceId);
    protected abstract CompletableFuture<Boolean> disableTool(long deviceId);
    protected abstract CompletableFuture<Boolean> sendPSetCmd(long deviceId, int pSet);
}
