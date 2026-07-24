package com.tightening.device.handler.impl.aneng;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.contract.IArranger;
import com.tightening.device.handler.impl.AnengGatewayHandler;
import com.tightening.entity.Device;
import com.tightening.util.ModbusUtils;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ArrangerAdapter implements IArranger {

    private static final int SLAVE_ADDR = 0x09;
    private static final int IO_REGISTER = 0x0000;

    private final AnengGatewayHandler gateway;
    private final long gatewayDeviceId;
    private final Device device;
    private final boolean reverseFirstFour;
    @Getter
    private volatile boolean healthy;

    public ArrangerAdapter(AnengGatewayHandler gateway, long gatewayDeviceId,
                    Device device, boolean reverseFirstFour) {
        this.gateway = gateway;
        this.gatewayDeviceId = gatewayDeviceId;
        this.device = device;
        this.reverseFirstFour = reverseFirstFour;
    }

    @Override
    public Long id() { return device.getId(); }

    @Override
    public DeviceType type() { return DeviceType.ARRANGER; }

    @Override
    public boolean isConnected() { return healthy; }

    @Override
    public CompletableFuture<Boolean> sendPulse(int[] channels, int pulseWidthMs) {
        // 构建 IO 输出字节（8 位，channels 中非 0 位设为 1）
        int outputBits = 0;
        for (int i = 0; i < Math.min(channels.length, 8); i++) {
            if (channels[i] != 0) outputBits |= (1 << i);
        }
        if (reverseFirstFour) {
            outputBits = swapFirstFour(outputBits);
        }
        outputBits = reverseBits(outputBits);

        // Step 1: 写 Set 信号
        return writeRegister(SLAVE_ADDR, IO_REGISTER, outputBits)
                .thenCompose(ok -> {
                    if (!ok) return CompletableFuture.completedFuture(false);
                    // Step 2: 延时 pulseWidthMs
                    CompletableFuture<Boolean> delayFuture = new CompletableFuture<>();
                    CompletableFuture.delayedExecutor(pulseWidthMs, TimeUnit.MILLISECONDS)
                            .execute(() -> delayFuture.complete(true));
                    return delayFuture;
                })
                .thenCompose(ok -> {
                    if (!ok) return CompletableFuture.completedFuture(false);
                    // Step 3: 写 Reset 信号
                    return writeRegister(SLAVE_ADDR, IO_REGISTER, 0);
                });
    }

    @Override
    public CompletableFuture<int[]> getOutputStatus() {
        return readIoStatus().thenApply(arr -> arr[0]);
    }

    @Override
    public CompletableFuture<int[]> getInputStatus() {
        return readIoStatus().thenApply(arr -> arr[1]);
    }

    @Override
    public CompletableFuture<Boolean> reset() {
        return writeRegister(SLAVE_ADDR, IO_REGISTER, 0);
    }

    public void pollHealth() {
        byte[] frame = ModbusUtils.buildReadFrame(SLAVE_ADDR, IO_REGISTER, 4);
        gateway.sendModbusFrame(gatewayDeviceId, frame)
                .whenComplete((buf, ex) -> {
                    boolean wasHealthy = healthy;
                    healthy = (ex == null && buf != null && buf.readableBytes() >= 9);
                    if (buf != null) buf.release();
                    if (wasHealthy != healthy) {
                        gateway.pushDeviceStatusEvent(device.getId(),
                                healthy ? DeviceStatus.CONNECTED : DeviceStatus.DISCONNECTED);
                    }
                });
    }

    private CompletableFuture<int[][]> readIoStatus() {
        byte[] frame = ModbusUtils.buildReadFrame(SLAVE_ADDR, IO_REGISTER, 4);
        return gateway.sendModbusFrame(gatewayDeviceId, frame)
                .thenApply(buf -> {
                    if (buf == null || buf.readableBytes() < 9) return new int[][]{new int[8], new int[8]};
                    byte[] payload = new byte[buf.readableBytes()];
                    buf.getBytes(buf.readerIndex(), payload);
                    buf.release();
                    // payload[3]=output_byte, payload[4]=input_byte
                    int outBits = payload[3] & 0xFF;
                    int inBits = payload[4] & 0xFF;
                    if (reverseFirstFour) {
                        outBits = swapFirstFour(outBits);
                    }
                    int[] out = new int[8];
                    int[] in = new int[8];
                    for (int i = 0; i < 8; i++) {
                        out[i] = (outBits >> i) & 1;
                        in[i] = (inBits >> i) & 1;
                    }
                    return new int[][]{out, in};
                });
    }

    private CompletableFuture<Boolean> writeRegister(int slaveAddr, int register, int value) {
        byte[] frame = ModbusUtils.buildWriteFrame(slaveAddr, register, value);
        return gateway.sendModbusFrame(gatewayDeviceId, frame)
                .thenApply(buf -> { if (buf != null) { buf.release(); return true; } return false; });
    }

    static int swapFirstFour(int bits) {
        int b0 = (bits >> 0) & 1, b3 = (bits >> 3) & 1;
        int b1 = (bits >> 1) & 1, b2 = (bits >> 2) & 1;
        int swapped = bits & 0xF0;
        swapped |= (b3 << 0) | (b2 << 1) | (b1 << 2) | (b0 << 3);
        return swapped;
    }

    static int reverseBits(int bits) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            result |= ((bits >> i) & 1) << (7 - i);
        }
        return result;
    }
}
