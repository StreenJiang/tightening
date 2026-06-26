package com.tightening.device.contract;

import com.tightening.constant.DeviceType;
import com.tightening.constant.DeviceStatus;
import com.tightening.device.handler.ToolHandler;
import com.tightening.dto.CurveDataDTO;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.Device;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Slf4j
public class ToolAdapter implements ITool {

    private final ToolHandler handler;
    private final Device device;
    private final List<Consumer<TighteningDataDTO>> tighteningDataListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<CurveDataDTO>> curveDataListeners = new CopyOnWriteArrayList<>();

    public ToolAdapter(ToolHandler handler, Device device) {
        this.handler = handler;
        this.device = device;
    }

    @Override
    public Long id() {
        return device.getId();
    }

    @Override
    public DeviceType type() {
        return DeviceType.getType(device.getType());
    }

    @Override
    public boolean isConnected() {
        return handler.getStatus(device.getId()) == DeviceStatus.CONNECTED;
    }

    @Override
    public CompletableFuture<Boolean> sendLock() {
        return handler.enableToolOp(device.getId());
    }

    @Override
    public CompletableFuture<Boolean> sendUnlock() {
        return handler.disableToolOp(device.getId());
    }

    @Override
    public CompletableFuture<Boolean> sendPSet(int psetId) {
        return handler.sendPSetOp(device.getId(), psetId);
    }

    @Override
    public void onTighteningData(Consumer<TighteningDataDTO> callback) {
        tighteningDataListeners.add(callback);
    }

    @Override
    public void onCurveData(Consumer<CurveDataDTO> callback) {
        curveDataListeners.add(callback);
    }

    public void fireTighteningData(TighteningDataDTO dto) {
        for (Consumer<TighteningDataDTO> l : tighteningDataListeners) {
            try {
                l.accept(dto);
            } catch (Exception e) {
                log.warn("Tightening data listener error: handler={}", handler.getClass().getSimpleName(), e);
            }
        }
    }

    public void fireCurveData(CurveDataDTO dto) {
        for (Consumer<CurveDataDTO> l : curveDataListeners) {
            try {
                l.accept(dto);
            } catch (Exception e) {
                log.warn("Curve data listener error: handler={}", handler.getClass().getSimpleName(), e);
            }
        }
    }
}
