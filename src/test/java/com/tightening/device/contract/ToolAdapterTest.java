package com.tightening.device.contract;

import com.tightening.constant.DeviceType;
import com.tightening.constant.DeviceStatus;
import com.tightening.device.handler.ToolHandler;
import com.tightening.dto.CurveDataDTO;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolAdapter 将 ToolHandler 适配为 ITool")
class ToolAdapterTest {

    @Mock
    private ToolHandler handler;

    private Device device;
    private ToolAdapter adapter;

    @BeforeEach
    void setUp() {
        device = new Device();
        device.setId(1L);
        device.setType(DeviceType.ATLAS_PF4000.getId());
        adapter = new ToolAdapter(handler, device);
    }

    @Test
    @DisplayName("id() 返回 device.getId()")
    void idDelegates() {
        assertThat(adapter.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("type() 返回 DeviceType")
    void typeDelegates() {
        assertThat(adapter.type()).isEqualTo(DeviceType.ATLAS_PF4000);
    }

    @Test
    @DisplayName("isConnected() 当 status=CONNECTED 返回 true")
    void isConnectedTrue() {
        when(handler.getStatus(1L)).thenReturn(DeviceStatus.CONNECTED);
        assertThat(adapter.isConnected()).isTrue();
    }

    @Test
    @DisplayName("isConnected() 当 status=DISCONNECTED 返回 false")
    void isConnectedFalse() {
        when(handler.getStatus(1L)).thenReturn(DeviceStatus.DISCONNECTED);
        assertThat(adapter.isConnected()).isFalse();
    }

    @Test
    @DisplayName("sendLock() 委托 unlock")
    void sendLockDelegates() {
        when(handler.unlock(1L)).thenReturn(CompletableFuture.completedFuture(true));
        assertThat(adapter.sendLock()).isCompletedWithValue(true);
    }

    @Test
    @DisplayName("sendUnlock() 委托 lock")
    void sendUnlockDelegates() {
        when(handler.lock(1L)).thenReturn(CompletableFuture.completedFuture(false));
        assertThat(adapter.sendUnlock()).isCompletedWithValue(false);
    }

    @Test
    @DisplayName("sendPSet() 委托 sendPSetOp")
    void sendPSetDelegates() {
        when(handler.sendPSetOp(1L, 5)).thenReturn(CompletableFuture.completedFuture(true));
        assertThat(adapter.sendPSet(5)).isCompletedWithValue(true);
    }

    @Test
    @DisplayName("onTighteningData() 注册 Consumer，fireTighteningData() 通知所有 Consumer")
    void tighteningDataCallback() {
        AtomicInteger count = new AtomicInteger(0);
        TighteningDataDTO dto = new TighteningDataDTO();
        dto.setTighteningStatus(1);

        adapter.onTighteningData(d -> count.incrementAndGet());
        adapter.fireTighteningData(dto);

        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("onCurveData() 注册 Consumer，fireCurveData() 通知所有 Consumer")
    void curveDataCallback() {
        AtomicInteger count = new AtomicInteger(0);
        CurveDataDTO dto = new CurveDataDTO();

        adapter.onCurveData(d -> count.incrementAndGet());
        adapter.fireCurveData(dto);

        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("多个 Consumer 全部被通知")
    void multipleConsumers() {
        AtomicInteger c1 = new AtomicInteger(0);
        AtomicInteger c2 = new AtomicInteger(0);
        TighteningDataDTO dto = new TighteningDataDTO();

        adapter.onTighteningData(d -> c1.incrementAndGet());
        adapter.onTighteningData(d -> c2.incrementAndGet());
        adapter.fireTighteningData(dto);

        assertThat(c1.get()).isEqualTo(1);
        assertThat(c2.get()).isEqualTo(1);
    }
}
