package com.tightening.device.handler;

import com.tightening.config.DeviceConfig;
import com.tightening.config.ToolCommonConfig;
import com.tightening.device.DeviceHolder;
import com.tightening.device.contract.ToolAdapter;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.dto.CurveDataDTO;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.CurveData;
import com.tightening.service.CurveDataService;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;
import com.tightening.util.Converter;

import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

@Slf4j
public abstract class ToolHandler extends TCPDeviceHandler {

    @Getter
    private final TighteningDataService tighteningDataService;
    @Getter
    private final CurveDataService curveDataService;
    private final ToolCommonConfig toolCommonConfig;

    private volatile ToolAdapter toolAdapter;
    private final Map<Long, DeviceTighteningCache> cacheByDevice = new ConcurrentHashMap<>();

    public ToolHandler(NioEventLoopGroup group,
                       DeviceService deviceService,
                       TighteningDataService tighteningDataService,
                       CurveDataService curveDataService,
                       ToolCommonConfig toolCommonConfig,
                       DeviceConfig deviceConfig) {
        super(group, deviceService, toolCommonConfig, deviceConfig);
        this.tighteningDataService = tighteningDataService;
        this.curveDataService = curveDataService;
        this.toolCommonConfig = toolCommonConfig;
    }

    public void setToolAdapter(ToolAdapter adapter) {
        this.toolAdapter = adapter;
    }

    public CompletableFuture<Boolean> unlock(long deviceId) {
        return changeToolState(true, deviceId, false);
    }

    public CompletableFuture<Boolean> lock(long deviceId) {
        return changeToolState(false, deviceId, false);
    }

    public CompletableFuture<Boolean> forceUnlock(long deviceId) {
        return changeToolState(true, deviceId, true);
    }

    public CompletableFuture<Boolean> forceLock(long deviceId) {
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

    public boolean isUnlocked(long deviceId) {
        return getHolder(deviceId).isUnlocked();
    }

    public CompletableFuture<Boolean> sendHeartbeat(long deviceId) {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * 通用状态变更（带冷却控制，支持强制绕过）
     *
     * @param targetEnabled  true=解锁，false=锁定
     * @param deviceId       设备ID
     * @param bypassCooldown true=绕过冷却检查，false=遵守冷却
     * @return CompletableFuture，操作结果
     */
    private CompletableFuture<Boolean> changeToolState(boolean targetEnabled, long deviceId,
                                                       boolean bypassCooldown) {
        String action = targetEnabled ? "unlock" : "lock";
        long now = System.currentTimeMillis();
        DeviceHolder holder = getHolder(deviceId);

        holder.getStateLock().lock();
        try {
            if (!bypassCooldown) {
                boolean oppositeState = targetEnabled != holder.isUnlocked();
                long lastTime = targetEnabled ? holder.getLastUnlockTime() : holder.getLastLockTime();
                log.debug("{}: deviceId={}, current isUnlocked={}, oppositeState={}",
                          action, deviceId, holder.isUnlocked(), oppositeState);

                if (!oppositeState && (now - lastTime) < toolCommonConfig.getLockUnlockCooldownMs()) {
                    log.debug("{} rejected by cooldown: deviceId={}, elapsed={}ms",
                              action, deviceId, now - lastTime);
                    return CompletableFuture.completedFuture(false);
                }
            } else {
                log.debug("force{}: deviceId={}, bypass cooldown", action, deviceId);
            }
            setLastTime(holder, targetEnabled, now);
        } finally {
            holder.getStateLock().unlock();
        }

        CompletableFuture<Boolean> operation = targetEnabled ? unlockTool(deviceId) : lockTool(deviceId);

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
                        holder.setUnlocked(true);
                    } else {
                        holder.setUnlocked(false);
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
                holder.setLastUnlockTime(time);
            } else {
                holder.setLastLockTime(time);
            }
        } finally {
            holder.getStateLock().unlock();
        }
    }

    // ============== 数据回调（InBoundHandler 解析后委托给这里） ==============

    public void handleTighteningData(TighteningDataDTO dto, Channel channel) {
        if (toolAdapter != null) {
            TCPDeviceHandler.applyToolTypeName(channel, dto);
            toolAdapter.fireTighteningData(dto);
        } else {
            log.warn("ToolAdapter not set for device, dropping tightening data: tighteningId={}", dto.getTighteningId());
        }

        long deviceId = channel.attr(DEVICE_ID).get();
        DeviceTighteningCache cache = cacheByDevice.computeIfAbsent(deviceId, k -> new DeviceTighteningCache());
        cache.byId.put(dto.getTighteningId(), dto);
        cache.latest = dto;
    }

    public void handleCurveData(CurveDataDTO dto, Channel channel) {
        CurveData data = Converter.dto2Entity(dto, CurveData::new);

        long deviceId = channel.attr(DEVICE_ID).get();
        DeviceTighteningCache cache = cacheByDevice.get(deviceId);
        if (cache != null) {
            TighteningDataDTO matched = cache.byId.get((long) dto.getTighteningId());
            if (matched == null) {
                matched = cache.latest;
            }
            if (matched != null) {
                data.setMissionRecordId(matched.getMissionRecordId());
                data.setBoltSerialNum(matched.getBoltSerialNum());
                data.setWorkstationName(matched.getWorkstationName());
                data.setParameterSet(matched.getParameterSet());
            }
        }

        curveDataService.save(data);
    }

    private static class DeviceTighteningCache {
        final Map<Long, TighteningDataDTO> byId = new LinkedHashMap<>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<Long, TighteningDataDTO> eldest) {
                return size() > 20;
            }
        };
        volatile TighteningDataDTO latest;
    }

    public void handleAlarm(String alarmMsg, long deviceId) {
        log.warn("Alarm from device {}: {}", deviceId, alarmMsg);
    }

    // ============== 抽象方法（子类必须实现，返回 CompletableFuture） ==============
    protected abstract CompletableFuture<Boolean> unlockTool(long deviceId);
    protected abstract CompletableFuture<Boolean> lockTool(long deviceId);
    protected abstract CompletableFuture<Boolean> sendPSetCmd(long deviceId, int pSet);
}
