package com.tightening.device.handler.impl;

import com.tightening.constant.DeviceStatus;
import com.tightening.device.DeviceHolder;
import com.tightening.entity.Device;
import com.tightening.entity.TighteningData;
import com.tightening.service.DeviceService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TCPDeviceHandler 抽象层测试")
class TCPDeviceHandlerTest {

    private NioEventLoopGroup group;

    @Mock
    private DeviceService deviceService;

    private TCPDeviceHandler handler;

    @BeforeEach
    void setUp() {
        group = new NioEventLoopGroup(1);
        handler = new TCPDeviceHandler(group, deviceService) {
            @Override
            public java.util.Set<com.tightening.constant.DeviceType> getSupportedTypes() {
                return java.util.Set.of();
            }

            @Override
            protected ChannelInitializer<NioSocketChannel> setupChannelInitializer() {
                return new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) {
                        // no-op stub for abstract method
                    }
                };
            }
        };
    }

    @AfterEach
    void tearDown() throws Exception {
        handler.close();
        group.shutdownGracefully();
    }

    // ======================== addDeviceInfo ========================

    @Test
    @DisplayName("addDeviceInfo 创建 DeviceHolder 并存入 devices 映射")
    void addDeviceInfo_shouldCreateHolderAndAddToMap() {
        Device device = new Device();
        device.setId(1L);
        device.setName("tool-1");

        DeviceHolder result = handler.addDeviceInfo(device);

        assertThat(result).isNotNull();
        assertThat(result.getDevice()).isSameAs(device);
        assertThat(result.getStatus()).isEqualTo(DeviceStatus.DISCONNECTED);
        assertThat(handler.getStatus(1L)).isEqualTo(DeviceStatus.DISCONNECTED);
    }

    // ======================== tryAddDeviceInfo ========================

    @Test
    @DisplayName("tryAddDeviceInfo 在设备已存在时不覆盖")
    void tryAddDeviceInfo_shouldNotAddIfAlreadyPresent() {
        Device original = new Device();
        original.setId(1L);
        original.setName("original");
        handler.addDeviceInfo(original);

        Device attempt = new Device();
        attempt.setId(1L);
        attempt.setName("override");
        handler.tryAddDeviceInfo(attempt);

        DeviceHolder holder = handler.getHolder(1L);
        assertThat(holder.getDevice().getName()).isEqualTo("original");
    }

    @Test
    @DisplayName("tryAddDeviceInfo 在设备不存在时添加")
    void tryAddDeviceInfo_shouldAddIfNotPresent() {
        Device device = new Device();
        device.setId(1L);
        handler.tryAddDeviceInfo(device);

        assertThat(handler.getStatus(1L)).isEqualTo(DeviceStatus.DISCONNECTED);
    }

    // ======================== getStatus ========================

    @Test
    @DisplayName("getStatus 对未知设备返回 NONE")
    void getStatus_shouldReturnNoneForUnknownDevice() {
        assertThat(handler.getStatus(999L)).isEqualTo(DeviceStatus.NONE);
    }

    @Test
    @DisplayName("getStatus 返回已添加设备的状态")
    void getStatus_shouldReturnDeviceStatus() {
        Device device = new Device();
        device.setId(1L);
        handler.addDeviceInfo(device);

        assertThat(handler.getStatus(1L)).isEqualTo(DeviceStatus.DISCONNECTED);
    }

    // ======================== getHolder ========================

    @Test
    @DisplayName("getHolder 返回已知设备的 Holder")
    void getHolder_shouldReturnHolderForKnownDevice() {
        Device device = new Device();
        device.setId(1L);
        handler.addDeviceInfo(device);

        DeviceHolder holder = handler.getHolder(1L);
        assertThat(holder).isNotNull();
        assertThat(holder.getDevice()).isSameAs(device);
    }

    @Test
    @DisplayName("getHolder 对未知设备抛出异常")
    void getHolder_shouldThrowForUnknownDevice() {
        assertThatThrownBy(() -> handler.getHolder(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("999");
    }

    // ======================== generateKey ========================

    @Test
    @DisplayName("generateKey 拼接命令与设备 ID")
    void generateKey_shouldFormatKey() {
        assertThat(handler.generateKey("enable", 1L)).isEqualTo("enable:1");
        assertThat(handler.generateKey("disable", 42L)).isEqualTo("disable:42");
        assertThat(handler.generateKey(101, 7L)).isEqualTo("101:7");
    }

    // ======================== addResultFuture ========================

    @Test
    @DisplayName("addResultFuture 完成已注册的 Future")
    void addResultFuture_shouldCompleteExistingFuture() throws Exception {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        injectRspFuture("cmd:1", future);

        handler.addResultFuture("cmd:1", true);

        assertThat(future).isDone();
        assertThat(future).isCompletedWithValue(true);
    }

    @Test
    @DisplayName("addResultFuture 对未知 Key 不做任何事")
    void addResultFuture_shouldDoNothingForUnknownKey() {
        // Must not throw even when completing an absent future
        handler.addResultFuture("nonexistent", true);
        handler.addResultFuture("nonexistent", false);
    }

    // ======================== addErrorMsgFuture / getErrorMsgFuture ========================

    @Test
    @DisplayName("addErrorMsgFuture 存储已完成的 Future，getErrorMsgFuture 取出")
    void errorMsgFuture_shouldRoundTrip() {
        handler.addErrorMsgFuture("err:1", "connection lost");

        CompletableFuture<String> future = handler.getErrorMsgFuture("err:1");
        assertThat(future).isNotNull();
        assertThat(future).isCompletedWithValue("connection lost");
    }

    @Test
    @DisplayName("getErrorMsgFuture 返回并移除条目")
    void getErrorMsgFuture_shouldReturnAndRemoveEntry() {
        handler.addErrorMsgFuture("err:1", "test");

        assertThat(handler.getErrorMsgFuture("err:1")).isNotNull();
        assertThat(handler.getErrorMsgFuture("err:1")).isNull();
    }

    @Test
    @DisplayName("getErrorMsgFuture 对未知 Key 返回 null")
    void getErrorMsgFuture_shouldReturnNullForUnknownKey() {
        assertThat(handler.getErrorMsgFuture("nonexistent")).isNull();
    }

    // ======================== disconnect ========================

    @Test
    @DisplayName("disconnect 移除设备并关闭底层通道")
    void disconnect_shouldRemoveDeviceAndCloseChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        Device device = new Device();
        device.setId(1L);
        handler.addDeviceInfo(device);
        handler.getHolder(1L).setChannel(channel);

        handler.disconnect(1L);

        assertThat(handler.getStatus(1L)).isEqualTo(DeviceStatus.NONE);
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    @DisplayName("disconnect 对未知设备不做任何事")
    void disconnect_shouldDoNothingForUnknownDevice() {
        // Must not throw for unknown device ID
        handler.disconnect(999L);
    }

    // ======================== connect ========================

    @Test
    @DisplayName("connect 对未添加设备调用 service.getById 并创建 Holder")
    void connect_shouldCallServiceAndAddDeviceWhenUnknown() {
        Device device = new Device();
        device.setId(1L);
        device.setType(1);
        device.setDetail("{\"ip\":\"0.0.0.0\",\"port\":1}");

        when(deviceService.getById(1L)).thenReturn(device);

        handler.connect(1L);

        assertThat(handler.getStatus(1L)).isEqualTo(DeviceStatus.CONNECTING);
    }

    @Test
    @DisplayName("connect 重复调用复用已有 Holder，不再查询 service")
    void connect_shouldReuseExistingHolder() {
        Device device = new Device();
        device.setId(1L);
        device.setDetail("{\"ip\":\"0.0.0.0\",\"port\":1}");
        handler.addDeviceInfo(device);
        assertThat(handler.getStatus(1L)).isEqualTo(DeviceStatus.DISCONNECTED);

        handler.connect(1L);

        // deviceService is deliberately not stubbed;
        // the else-branch must NOT call getById()
        assertThat(handler.getStatus(1L)).isEqualTo(DeviceStatus.CONNECTING);
    }

    // ======================== applyToolTypeName (static) ========================

    @Test
    @DisplayName("applyToolTypeName 从 Channel 持有者读取工具类型名")
    void applyToolTypeName_shouldSetToolTypeNameFromHolder() {
        EmbeddedChannel channel = new EmbeddedChannel();
        Device device = new Device();
        device.setType(3); // FIT-FTC6 -> "FIT-FTC6"
        DeviceHolder holder = new DeviceHolder(device);
        channel.attr(TCPDeviceHandler.DEVICE_HOLDER).set(holder);

        TighteningData data = new TighteningData();
        TCPDeviceHandler.applyToolTypeName(channel, data);

        assertThat(data.getToolTypeName()).isEqualTo("FIT-FTC6");
    }

    @Test
    @DisplayName("applyToolTypeName 无 Holder 时不设置")
    void applyToolTypeName_shouldDoNothingWhenNoHolder() {
        EmbeddedChannel channel = new EmbeddedChannel();
        TighteningData data = new TighteningData();

        TCPDeviceHandler.applyToolTypeName(channel, data);

        assertThat(data.getToolTypeName()).isNull();
    }

    // ======================== helpers ========================

    @SuppressWarnings("unchecked")
    private void injectRspFuture(String key, CompletableFuture<Boolean> future) throws Exception {
        var field = TCPDeviceHandler.class.getDeclaredField("rspFutures");
        field.setAccessible(true);
        Map<String, CompletableFuture<Boolean>> rspFutures =
                (Map<String, CompletableFuture<Boolean>>) field.get(handler);
        rspFutures.put(key, future);
    }
}
