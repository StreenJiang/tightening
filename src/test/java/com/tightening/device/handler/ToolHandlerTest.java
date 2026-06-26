package com.tightening.device.handler;

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

    private ToolCommonConfig config;
    private TestToolHandler handler;
    private DeviceHolder holder;

    /**
     * ToolHandler 的匿名子类，桩实现三个抽象方法，
     * 所有操作返回 CompletableFuture.completedFuture(true)。
     */
    static class TestToolHandler extends ToolHandler {

        TestToolHandler(NioEventLoopGroup g, DeviceService ds, TighteningDataService tds, CurveDataService cds, ToolCommonConfig cfg) {
            super(g, ds, tds, cds, cfg);
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
        public CompletableFuture<Boolean> enableTool(long deviceId) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> disableTool(long deviceId) {
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
        config.setEnableDisableCooldownMs(1000L);
        handler = new TestToolHandler(group, deviceService, tighteningDataService, curveDataService, config);
        Device device = new Device();
        device.setId(DEVICE_ID);
        holder = handler.addDeviceInfo(device);
    }

    @AfterEach
    void tearDown() throws Exception {
        handler.close();
    }

    @Test
    @DisplayName("getTighteningDataService 应返回构造注入的 service")
    void getTighteningDataService_shouldReturnService() {
        assertThat(handler.getTighteningDataService()).isSameAs(tighteningDataService);
    }

    @Test
    @DisplayName("isToolEnabled 应读取 DeviceHolder 的状态")
    void isToolEnabled_shouldReadHolderState() {
        holder.setToolEnabled(false);
        assertThat(handler.isToolEnabled(DEVICE_ID)).isFalse();

        holder.setToolEnabled(true);
        assertThat(handler.isToolEnabled(DEVICE_ID)).isTrue();
    }

    // ============== changeToolState(true) ==============

    @Test
    @DisplayName("changeToolState(true): 工具禁用状态 → 允许启用（状态变更）")
    void enableToolOp_whenToolDisabled_shouldSucceed() {
        holder.setToolEnabled(false);
        holder.setLastEnableTime(0);

        assertThat(handler.enableToolOp(DEVICE_ID).join()).isTrue();
    }

    @Test
    @DisplayName("changeToolState(true): 工具已启用且在冷却期内 → 拒绝")
    void enableToolOp_whenToolEnabledAndWithinCooldown_shouldReject() {
        holder.setToolEnabled(true);
        holder.setLastEnableTime(System.currentTimeMillis());

        assertThat(handler.enableToolOp(DEVICE_ID).join()).isFalse();
    }

    @Test
    @DisplayName("changeToolState(true) bypass=true: 工具已启用且在冷却期内 → 强制允许")
    void forceEnableToolOp_whenToolEnabledAndWithinCooldown_shouldSucceed() {
        holder.setToolEnabled(true);
        holder.setLastEnableTime(System.currentTimeMillis());

        assertThat(handler.forceEnableToolOp(DEVICE_ID).join()).isTrue();
    }

    // ============== changeToolState(false) ==============

    @Test
    @DisplayName("changeToolState(false): 工具启用状态 → 允许禁用（状态变更）")
    void disableToolOp_whenToolEnabled_shouldSucceed() {
        holder.setToolEnabled(true);
        holder.setLastDisableTime(0);

        assertThat(handler.disableToolOp(DEVICE_ID).join()).isTrue();
    }

    @Test
    @DisplayName("changeToolState(false): 工具已禁用且在冷却期内 → 拒绝")
    void disableToolOp_whenToolDisabledAndWithinCooldown_shouldReject() {
        holder.setToolEnabled(false);
        holder.setLastDisableTime(System.currentTimeMillis());

        assertThat(handler.disableToolOp(DEVICE_ID).join()).isFalse();
    }

    @Test
    @DisplayName("changeToolState(false) bypass=true: 工具已禁用且在冷却期内 → 强制允许")
    void forceDisableToolOp_whenToolDisabledAndWithinCooldown_shouldSucceed() {
        holder.setToolEnabled(false);
        holder.setLastDisableTime(System.currentTimeMillis());

        assertThat(handler.forceDisableToolOp(DEVICE_ID).join()).isTrue();
    }

    // ============== 成功后的状态更新 ==============

    @Test
    @DisplayName("enableToolOp 成功后更新 isToolEnabled=true 和 lastEnableTime")
    void enableToolOp_shouldUpdateStateOnSuccess() {
        holder.setToolEnabled(false);
        holder.setLastEnableTime(0);

        handler.enableToolOp(DEVICE_ID).join();

        assertThat(holder.isToolEnabled()).isTrue();
        assertThat(holder.getLastEnableTime()).isGreaterThan(0);
    }

    @Test
    @DisplayName("disableToolOp 成功后更新 isToolEnabled=false 和 lastDisableTime")
    void disableToolOp_shouldUpdateStateOnSuccess() {
        holder.setToolEnabled(true);
        holder.setLastDisableTime(0);

        handler.disableToolOp(DEVICE_ID).join();

        assertThat(holder.isToolEnabled()).isFalse();
        assertThat(holder.getLastDisableTime()).isGreaterThan(0);
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
