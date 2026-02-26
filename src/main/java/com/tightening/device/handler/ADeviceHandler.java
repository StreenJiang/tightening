package com.tightening.device.handler;

import com.tightening.constant.DeviceStatus;
import com.tightening.device.DeviceHolder;
import lombok.Getter;

public abstract class ADeviceHandler {
    @Getter
    protected DeviceHolder deviceHolder;

    protected ADeviceHandler(DeviceHolder deviceHolder) {
        this.deviceHolder = deviceHolder;
    }

    // 模板方法：定义连接流程
    public final void connect() throws Exception {
        if (deviceHolder.getStatus() == DeviceStatus.CONNECTED) {
            return; // 或抛出异常
        }

        try {
            doConnect();    // 子类实现具体的连接逻辑
            deviceHolder.setStatus(DeviceStatus.CONNECTED);
        } catch (Exception e) {
            deviceHolder.setStatus(DeviceStatus.DISCONNECTED);
            throw e;
        } finally {
        }
    }

    // 子类必须实现的连接细节
    protected abstract void doConnect() throws Exception;

    public final void disconnect() {
        if (deviceHolder.getStatus() == DeviceStatus.DISCONNECTED)
            return;
        try {
            doDisconnect();
            deviceHolder.setStatus(DeviceStatus.DISCONNECTED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract void doDisconnect() throws Exception;
}
