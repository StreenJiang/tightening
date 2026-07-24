package com.tightening.device.handler.impl.aneng;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.constant.SseEventType;
import com.tightening.device.contract.ISetterSelector;
import com.tightening.device.handler.impl.AnengGatewayHandler;
import com.tightening.dto.SseEvent;
import com.tightening.entity.Device;
import com.tightening.util.ModbusUtils;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SetterSelectorAdapter implements ISetterSelector {

    // 硬编码 Modbus 参数（排列机和批头选择器固定）
    private static final int SLAVE_ADDR = 0x09;
    private static final int IO_REGISTER = 0x0000;

    private final AnengGatewayHandler gateway;
    private final long gatewayDeviceId;
    private final Device device;
    private final int setterCount;
    @Getter
    private volatile boolean healthy;

    public SetterSelectorAdapter(AnengGatewayHandler gateway, long gatewayDeviceId,
                                  Device device, int setterCount) {
        this.gateway = gateway;
        this.gatewayDeviceId = gatewayDeviceId;
        this.device = device;
        this.setterCount = setterCount;
    }

    @Override
    public Long id() { return device.getId(); }

    @Override
    public DeviceType type() { return DeviceType.SETTER_SELECTOR; }

    @Override
    public boolean isConnected() { return healthy; }

    @Override
    public int getPositionCount() { return setterCount; }

    @Override
    public CompletableFuture<Boolean> writePosition(int position) {
        if (position < 1 || position > setterCount) {
            return CompletableFuture.completedFuture(false);
        }
        // 构建 IO 输出字节：position 位设为 1
        int outputBits = 0;
        for (int i = 0; i < setterCount; i++) {
            if (i == position - 1) outputBits |= (1 << i);
        }
        return writeRegister(SLAVE_ADDR, IO_REGISTER, outputBits);
    }

    @Override
    public CompletableFuture<Boolean> reset() {
        return writeRegister(SLAVE_ADDR, IO_REGISTER, 0);
    }

    private CompletableFuture<Boolean> writeRegister(int slaveAddr, int register, int value) {
        byte[] frame = ModbusUtils.buildWriteFrame(slaveAddr, register, value);
        return gateway.sendModbusFrame(gatewayDeviceId, frame)
                .thenApply(buf -> {
                    if (buf != null) { buf.release(); return true; }
                    return false;
                });
    }

    public void pollHealth() {
        byte[] frame = ModbusUtils.buildReadFrame(SLAVE_ADDR, IO_REGISTER, 4);
        gateway.sendModbusFrame(gatewayDeviceId, frame)
                .whenComplete((buf, ex) -> {
                    boolean wasHealthy = healthy;
                    healthy = (ex == null && buf != null && buf.readableBytes() >= 7);
                    if (buf != null) buf.release();
                    if (wasHealthy != healthy) {
                        gateway.pushDeviceStatusEvent(device.getId(),
                                healthy ? DeviceStatus.CONNECTED : DeviceStatus.DISCONNECTED);
                    }
                });
    }

}
