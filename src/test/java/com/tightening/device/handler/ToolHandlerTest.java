package com.tightening.device.handler;

import com.tightening.config.DeviceConfig;
import com.tightening.config.ToolCommonConfig;
import com.tightening.device.DeviceHolder;
import com.tightening.entity.Device;
import com.tightening.service.CurveDataService;
import com.tightening.service.DeviceService;
import com.tightening.service.TighteningDataService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolHandler 冷却与状态变更测试")
class ToolHandlerTest {

    private static final long DEVICE_ID = 1L;

    @Mock
    private NioEventLoopGroup group;

    @Mock
    private DeviceService deviceService;

    @Mock
    private TighteningDataService tighteningDataService;

    @Mock
    private CurveDataService curveDataService;

    @Mock
    private DeviceConfig deviceConfig;

    private ToolCommonConfig config;
    private TestToolHandler handler;
    private DeviceHolder holder;

    /**
     * ToolHandler 的匿名子类，桩实现三个抽象方法，
     * 所有操作返回 CompletableFuture.completedFuture(true)。
     */
    static class TestToolHandler extends ToolHandler {

        TestToolHandler(NioEventLoopGroup g, DeviceService ds, TighteningDataService tds, CurveDataService cds, ToolCommonConfig cfg, DeviceConfig deviceConfig) {
            super(g, ds, tds, cds, cfg, deviceConfig, null);
        }

        @Override
        protected ChannelInitializer<NioSocketChannel> setupChannelInitializer() {
            return new ChannelInitializer<>() {
                @Override
                protected void initChannel(NioSocketChannel ch) {
                    // 测试用途，不执行任何初始化
                }
            };
        }

        @Override
        public CompletableFuture<Boolean> unlockTool(long deviceId) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> lockTool(long deviceId) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> sendPSetCmd(long deviceId, int pSet) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public java.util.Set<com.tightening.constant.DeviceType> getSupportedTypes() {
            return java.util.Set.of();
        }
    }

    @BeforeEach
    void setUp() {
        config = new ToolCommonConfig();
        config.setLockUnlockCooldownMs(1000L);
        handler = new TestToolHandler(group, deviceService, tighteningDataService, curveDataService, config, deviceConfig);
        Device device = new Device();
        device.setId(DEVICE_ID);
        holder = handler.addDeviceInfo(device);
    }


    @Test
    @DisplayName("getTighteningDataService 应返回构造注入的 service")
    void getTighteningDataService_shouldReturnService() {
        assertThat(handler.getTighteningDataService()).isSameAs(tighteningDataService);
    }

    @Test
    @DisplayName("isUnlocked 应读取 DeviceHolder 的状态")
    void isUnlocked_shouldReadHolderState() {
        holder.setUnlocked(false);
        assertThat(handler.isUnlocked(DEVICE_ID)).isFalse();

        holder.setUnlocked(true);
        assertThat(handler.isUnlocked(DEVICE_ID)).isTrue();
    }

    // ============== changeToolState(true) ==============

    @Test
    @DisplayName("changeToolState(true): 工具禁用状态 → 允许启用（状态变更）")
    void unlock_whenToolDisabled_shouldSucceed() {
        holder.setUnlocked(false);
        holder.setLastUnlockTime(0);

        assertThat(handler.unlock(DEVICE_ID).join()).isTrue();
    }

    @Test
    @DisplayName("changeToolState(true): 工具已启用且在冷却期内 → 拒绝")
    void unlock_whenToolEnabledAndWithinCooldown_shouldReject() {
        holder.setUnlocked(true);
        holder.setLastUnlockTime(System.currentTimeMillis());

        assertThat(handler.unlock(DEVICE_ID).join()).isFalse();
    }

    @Test
    @DisplayName("changeToolState(true) bypass=true: 工具已启用且在冷却期内 → 强制允许")
    void forceUnlock_whenToolEnabledAndWithinCooldown_shouldSucceed() {
        holder.setUnlocked(true);
        holder.setLastUnlockTime(System.currentTimeMillis());

        assertThat(handler.forceUnlock(DEVICE_ID).join()).isTrue();
    }

    // ============== changeToolState(false) ==============

    @Test
    @DisplayName("changeToolState(false): 工具启用状态 → 允许禁用（状态变更）")
    void lock_whenToolEnabled_shouldSucceed() {
        holder.setUnlocked(true);
        holder.setLastLockTime(0);

        assertThat(handler.lock(DEVICE_ID).join()).isTrue();
    }

    @Test
    @DisplayName("changeToolState(false): 工具已禁用且在冷却期内 → 拒绝")
    void lock_whenToolDisabledAndWithinCooldown_shouldReject() {
        holder.setUnlocked(false);
        holder.setLastLockTime(System.currentTimeMillis());

        assertThat(handler.lock(DEVICE_ID).join()).isFalse();
    }

    @Test
    @DisplayName("changeToolState(false) bypass=true: 工具已禁用且在冷却期内 → 强制允许")
    void forceLock_whenToolDisabledAndWithinCooldown_shouldSucceed() {
        holder.setUnlocked(false);
        holder.setLastLockTime(System.currentTimeMillis());

        assertThat(handler.forceLock(DEVICE_ID).join()).isTrue();
    }

    // ============== 成功后的状态更新 ==============

    @Test
    @DisplayName("unlock 成功后更新 isUnlocked=true 和 lastUnlockTime")
    void unlock_shouldUpdateStateOnSuccess() {
        holder.setUnlocked(false);
        holder.setLastUnlockTime(0);

        handler.unlock(DEVICE_ID).join();

        assertThat(holder.isUnlocked()).isTrue();
        assertThat(holder.getLastUnlockTime()).isGreaterThan(0);
    }

    @Test
    @DisplayName("lock 成功后更新 isUnlocked=false 和 lastLockTime")
    void lock_shouldUpdateStateOnSuccess() {
        holder.setUnlocked(true);
        holder.setLastLockTime(0);

        handler.lock(DEVICE_ID).join();

        assertThat(holder.isUnlocked()).isFalse();
        assertThat(holder.getLastLockTime()).isGreaterThan(0);
    }

    // ============== sendPSetOp ==============

    @Test
    @DisplayName("sendPSetOp 正常完成并释放 pSetLock")
    void sendPSetOp_shouldCompleteAndReleaseLock() {
        CompletableFuture<Boolean> result = handler.sendPSetOp(DEVICE_ID, 5);

        assertThat(result.join()).isTrue();

        ReentrantLock lock = holder.getPSetLock();
        assertThat(lock.isHeldByCurrentThread()).as("sendPSetOp 应在 finally 中释放锁").isFalse();
    }
}
