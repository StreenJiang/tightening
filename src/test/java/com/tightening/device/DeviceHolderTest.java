package com.tightening.device;

import com.tightening.constant.DeviceStatus;
import com.tightening.entity.Device;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceHolderTest {

    private static Device createDevice(int type) {
        Device device = new Device();
        device.setName("TestDevice");
        device.setType(type);
        device.setDetail("192.168.1.100:5000");
        return device;
    }

    @Test
    void construct_shouldSetInitialState() {
        Device device = createDevice(1);
        DeviceHolder holder = new DeviceHolder(device);

        assertThat(holder.getDevice()).isSameAs(device);
        assertThat(holder.getStatus()).isEqualTo(DeviceStatus.DISCONNECTED);
        assertThat(holder.isUnlocked()).isFalse();
        assertThat(holder.getChannel()).isNull();
        assertThat(holder.getLastUnlockTime()).isZero();
        assertThat(holder.getLastLockTime()).isZero();
        assertThat(holder.getStateLock()).isNotNull();
        assertThat(holder.getPSetLock()).isNotNull();
    }

    @Test
    void setStatus_shouldUpdateAndReturnStatus() {
        DeviceHolder holder = new DeviceHolder(createDevice(1));

        holder.setStatus(DeviceStatus.CONNECTING);
        assertThat(holder.getStatus()).isEqualTo(DeviceStatus.CONNECTING);

        holder.setStatus(DeviceStatus.CONNECTED);
        assertThat(holder.getStatus()).isEqualTo(DeviceStatus.CONNECTED);

        holder.setStatus(DeviceStatus.DISCONNECTED);
        assertThat(holder.getStatus()).isEqualTo(DeviceStatus.DISCONNECTED);

        holder.setStatus(DeviceStatus.NONE);
        assertThat(holder.getStatus()).isEqualTo(DeviceStatus.NONE);
    }

    @Test
    void setChannel_shouldStoreAndReturnChannel() {
        DeviceHolder holder = new DeviceHolder(createDevice(1));
        assertThat(holder.getChannel()).isNull();

        Channel channel = new EmbeddedChannel();
        holder.setChannel(channel);
        assertThat(holder.getChannel()).isSameAs(channel);

        holder.setChannel(null);
        assertThat(holder.getChannel()).isNull();
    }

    @Test
    void isUnlocked_shouldDefaultToFalse() {
        DeviceHolder holder = new DeviceHolder(createDevice(1));
        assertThat(holder.isUnlocked()).isFalse();
    }

    @Test
    void setUnlocked_shouldToggleState() {
        DeviceHolder holder = new DeviceHolder(createDevice(1));

        holder.setUnlocked(true);
        assertThat(holder.isUnlocked()).isTrue();

        holder.setUnlocked(false);
        assertThat(holder.isUnlocked()).isFalse();
    }

    @Test
    void lastUnlockTime_shouldStoreAndReturnValue() {
        DeviceHolder holder = new DeviceHolder(createDevice(1));
        assertThat(holder.getLastUnlockTime()).isZero();

        holder.setLastUnlockTime(1000L);
        assertThat(holder.getLastUnlockTime()).isEqualTo(1000L);

        holder.setLastUnlockTime(Long.MAX_VALUE);
        assertThat(holder.getLastUnlockTime()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void lastLockTime_shouldStoreAndReturnValue() {
        DeviceHolder holder = new DeviceHolder(createDevice(1));
        assertThat(holder.getLastLockTime()).isZero();

        holder.setLastLockTime(2000L);
        assertThat(holder.getLastLockTime()).isEqualTo(2000L);

        holder.setLastLockTime(Long.MAX_VALUE);
        assertThat(holder.getLastLockTime()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void resolveToolTypeName_withValidType_shouldReturnName() {
        DeviceHolder pf4000 = new DeviceHolder(createDevice(1));
        assertThat(pf4000.resolveToolTypeName()).isEqualTo("PF4000");

        DeviceHolder pf6000op = new DeviceHolder(createDevice(2));
        assertThat(pf6000op.resolveToolTypeName()).isEqualTo("PF6000-OP");

        DeviceHolder fitftc6 = new DeviceHolder(createDevice(3));
        assertThat(fitftc6.resolveToolTypeName()).isEqualTo("FIT-FTC6");
    }

    @Test
    void resolveToolTypeName_withUnknownType_shouldReturnNull() {
        DeviceHolder holder = new DeviceHolder(createDevice(999));
        assertThat(holder.resolveToolTypeName()).isNull();
    }

    @Test
    void stateLock_and_pSetLock_shouldBeDistinct() {
        DeviceHolder holder = new DeviceHolder(createDevice(1));
        assertThat(holder.getStateLock()).isNotNull();
        assertThat(holder.getPSetLock()).isNotNull();
        assertThat(holder.getStateLock()).isNotSameAs(holder.getPSetLock());
    }
}
