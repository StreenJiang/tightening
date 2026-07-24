package com.tightening.device.handler.impl.aneng;

import com.tightening.util.Crc16Utils;
import com.tightening.util.ModbusUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AdapterTest {

    // ========== ArrangerAdapter 静态工具方法 ==========

    @Test
    @DisplayName("ArrangerAdapter.reverseBits — 10000000 → 00000001")
    void testReverseBits() {
        assertThat(ArrangerAdapter.reverseBits(0b10000000)).isEqualTo(0b00000001);
        assertThat(ArrangerAdapter.reverseBits(0b00000001)).isEqualTo(0b10000000);
        assertThat(ArrangerAdapter.reverseBits(0b00000000)).isEqualTo(0b00000000);
        assertThat(ArrangerAdapter.reverseBits(0b11111111)).isEqualTo(0b11111111);
        assertThat(ArrangerAdapter.reverseBits(0b10101010)).isEqualTo(0b01010101);
        assertThat(ArrangerAdapter.reverseBits(0b11001100)).isEqualTo(0b00110011);
    }

    @Test
    @DisplayName("ArrangerAdapter.swapFirstFour — positions 1,2,3,4 → 4,3,2,1")
    void testSwapFirstFour() {
        // bit0（最右）↔ bit3，bit1 ↔ bit2
        // 0001 → 1000（bit0↔bit3）
        assertThat(ArrangerAdapter.swapFirstFour(0b00000001)).isEqualTo(0b00001000);
        // 1000 → 0001（bit3↔bit0）
        assertThat(ArrangerAdapter.swapFirstFour(0b00001000)).isEqualTo(0b00000001);
        // 0010 → 0100（bit1↔bit2）
        assertThat(ArrangerAdapter.swapFirstFour(0b00000010)).isEqualTo(0b00000100);
        // 0100 → 0010（bit2↔bit1）
        assertThat(ArrangerAdapter.swapFirstFour(0b00000100)).isEqualTo(0b00000010);
        // 1111 → 1111（全 1 不变）
        assertThat(ArrangerAdapter.swapFirstFour(0b00001111)).isEqualTo(0b00001111);
        // 高位 nibble 不受影响
        assertThat(ArrangerAdapter.swapFirstFour(0b10100001)).isEqualTo(0b10101000);
    }

    // ========== Modbus 帧构建正确性 ==========

    @Test
    @DisplayName("03 读帧构建 — 验证结构和 CRC")
    void buildReadFrame() {
        byte[] frame = ModbusUtils.buildReadFrame(1, 0x0003, 2);

        assertThat(frame).hasSize(8);
        // 从机地址
        assertThat(frame[0]).isEqualTo((byte) 0x01);
        // 功能码 03
        assertThat(frame[1]).isEqualTo((byte) 0x03);
        // 寄存器地址 0x0003 (大端)
        assertThat(frame[2]).isEqualTo((byte) 0x00);
        assertThat(frame[3]).isEqualTo((byte) 0x03);
        // 读取数量 2
        assertThat(frame[4]).isEqualTo((byte) 0x00);
        assertThat(frame[5]).isEqualTo((byte) 0x02);

        // CRC16 校验字节必须正确
        byte[] payload = Arrays.copyOf(frame, 6);
        int crc = Crc16Utils.compute(payload);
        assertThat(frame[6] & 0xFF).isEqualTo(crc & 0xFF);
        assertThat(frame[7] & 0xFF).isEqualTo((crc >> 8) & 0xFF);
    }

    @Test
    @DisplayName("06 写帧构建 — 验证结构和 CRC")
    void buildWriteFrame() {
        byte[] frame = ModbusUtils.buildWriteFrame(0x09, 0x0000, 0x0080);

        assertThat(frame).hasSize(8);
        // 从机地址 09
        assertThat(frame[0]).isEqualTo((byte) 0x09);
        // 功能码 06
        assertThat(frame[1]).isEqualTo((byte) 0x06);
        // 寄存器地址 0x0000
        assertThat(frame[2]).isEqualTo((byte) 0x00);
        assertThat(frame[3]).isEqualTo((byte) 0x00);
        // 写值 0x0080
        assertThat(frame[4]).isEqualTo((byte) 0x00);
        assertThat(frame[5]).isEqualTo((byte) 0x80);

        // CRC16 校验字节必须正确
        byte[] payload = Arrays.copyOf(frame, 6);
        int crc = Crc16Utils.compute(payload);
        assertThat(frame[6] & 0xFF).isEqualTo(crc & 0xFF);
        assertThat(frame[7] & 0xFF).isEqualTo((crc >> 8) & 0xFF);
    }

    @Test
    @DisplayName("不同从机地址和寄存器组合产生不同帧")
    void buildReadFrameDifferentParams() {
        byte[] frame1 = ModbusUtils.buildReadFrame(1, 0x0003, 2);
        byte[] frame2 = ModbusUtils.buildReadFrame(2, 0x0003, 1);

        // 地址不同或数量不同 → 帧不同
        assertThat(frame1).isNotEqualTo(frame2);
        // frame2: slave=02, count=01
        assertThat(frame2[0]).isEqualTo((byte) 0x02);
        assertThat(frame2[5]).isEqualTo((byte) 0x01);
    }

    @Test
    @DisplayName("ArrangerAdapter.sendPulse — outputBits 构建逻辑（combine reverseBits + swapFirstFour）")
    void testSendPulseOutputBits() {
        // 模拟 sendPulse 的内部 bit 构建逻辑：channels → reverseBits → swapFirstFour
        // channels = [1, 0, 0, 0, 0, 0, 0, 0] (通道 1 使能)
        int outputBits = 0;
        outputBits |= (1 << 0); // 通道 0

        // reverseFirstFour = true → swapFirstFour
        outputBits = ArrangerAdapter.swapFirstFour(outputBits);
        // swapFirstFour: bit0↔bit3 → bit3 置 1
        assertThat(outputBits & (1 << 3)).isNotZero();

        // reverseBits: 0b00001000 → 0b00010000
        outputBits = ArrangerAdapter.reverseBits(outputBits);
        assertThat(outputBits & (1 << 4)).isNotZero();
    }

    // ========== 辅助方法 ==========
}
