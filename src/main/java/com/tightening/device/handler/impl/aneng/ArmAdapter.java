package com.tightening.device.handler.impl.aneng;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.constant.SseEventType;
import com.tightening.device.contract.Coordinates3D;
import com.tightening.device.contract.IArm;
import com.tightening.device.handler.impl.AnengGatewayHandler;
import com.tightening.dto.SseEvent;
import com.tightening.entity.ArmModelConfig;
import com.tightening.entity.Device;
import com.tightening.util.ModbusUtils;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ArmAdapter implements IArm {

    private final AnengGatewayHandler gateway;
    private final long gatewayDeviceId;
    private final ArmModelConfig model;
    private final Device device;
    @Getter
    private volatile boolean healthy;

    public ArmAdapter(AnengGatewayHandler gateway, long gatewayDeviceId,
                      ArmModelConfig model, Device device) {
        this.gateway = gateway;
        this.gatewayDeviceId = gatewayDeviceId;
        this.model = model;
        this.device = device;
    }

    @Override
    public Long id() { return device.getId(); }

    @Override
    public DeviceType type() { return DeviceType.ARM; }

    @Override
    public boolean isConnected() { return healthy; }

    @Override
    public CompletableFuture<Coordinates3D> getCurrentCoordinates() {
        CompletableFuture<ByteBuf> xFuture = gateway.sendModbusFrame(gatewayDeviceId,
                ModbusUtils.buildReadFrame(model.getXSlaveAddr(), model.getXRegister(), model.getXCount()));
        CompletableFuture<ByteBuf> yFuture = gateway.sendModbusFrame(gatewayDeviceId,
                ModbusUtils.buildReadFrame(model.getYSlaveAddr(), model.getYRegister(), model.getYCount()));

        CompletableFuture<ByteBuf> zFuture;
        if (model.getZSlaveAddr() != null) {
            zFuture = gateway.sendModbusFrame(gatewayDeviceId,
                    ModbusUtils.buildReadFrame(model.getZSlaveAddr(), model.getZRegister(),
                            model.getZCount() != null ? model.getZCount() : 1));
        } else {
            zFuture = CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(xFuture, yFuture, zFuture)
                .thenApply(v -> {
                    try {
                        int x = parseCoordinate(xFuture.get(), model.getParseStrategy());
                        int y = parseCoordinate(yFuture.get(), model.getParseStrategy());
                        int z = zFuture.get() != null
                                ? parseCoordinate(zFuture.get(), model.getParseStrategy()) : 0;
                        return new Coordinates3D(x, y, z);
                    } catch (Exception e) {
                        return Coordinates3D.ZERO;
                    }
                });
    }

    public void pollHealth() {
        byte[] frame = ModbusUtils.buildReadFrame(model.getXSlaveAddr(), model.getXRegister(), model.getXCount());
        gateway.sendModbusFrame(gatewayDeviceId, frame)
                .whenComplete((buf, ex) -> {
                    boolean wasHealthy = healthy;
                    healthy = (ex == null && buf != null);
                    if (buf != null) buf.release();
                    if (wasHealthy != healthy) {
                        gateway.pushDeviceStatusEvent(device.getId(),
                                healthy ? DeviceStatus.CONNECTED : DeviceStatus.DISCONNECTED);
                    }
                });
    }

    private int parseCoordinate(ByteBuf response, String parseStrategy) {
        if (response == null || response.readableBytes() < 5) return 0;
        byte[] payload = new byte[response.readableBytes()];
        response.getBytes(response.readerIndex(), payload);
        int byteCount = payload[2] & 0xFF;
        if (byteCount < 2) return 0;
        int rawValue = ((payload[3] & 0xFF) << 8) | (payload[4] & 0xFF);

        if ("DIVIDE_BY_100".equals(parseStrategy)) {
            return rawValue / 100;
        }
        return rawValue;
    }
}
