package com.tightening.device;

import com.tightening.constant.DeviceChangeType;
import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.contract.IArm;
import com.tightening.device.contract.ITool;
import com.tightening.device.event.DeviceChangeEvent;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.DeviceHandlerFactory;
import com.tightening.device.handler.ToolHandler;
import com.tightening.device.handler.impl.AnengGatewayHandler;
import com.tightening.device.type.Arm;
import com.tightening.entity.ArmModelConfig;
import com.tightening.entity.Device;
import com.tightening.mapper.ArmModelConfigMapper;
import com.tightening.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceRegistry 事件驱动 ITool 注册表")
class DeviceRegistryTest {

    @Mock
    private DeviceHandlerFactory handlerFactory;

    @Mock
    private ToolHandler toolHandler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private DeviceService deviceService;

    @Mock
    private ArmModelConfigMapper armModelConfigMapper;

    private DeviceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DeviceRegistry(handlerFactory, eventPublisher, deviceService, armModelConfigMapper);
    }

    @Test
    @DisplayName("getTool() 未注册设备返回 null")
    void getToolReturnsNull() {
        assertThat(registry.getTool(999L)).isNull();
    }

    @Test
    @DisplayName("DeviceChangeEvent ADD 注册 ToolAdapter → getTool() 返回 ITool")
    void addEventRegistersTool() {
        Device device = new Device();
        device.setId(1L);
        device.setType(DeviceType.ATLAS_PF4000.getId());
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(toolHandler);

        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, device));

        ITool tool = registry.getTool(1L);
        assertThat(tool).isNotNull();
        assertThat(tool.id()).isEqualTo(1L);
        assertThat(tool.type()).isEqualTo(DeviceType.ATLAS_PF4000);
    }

    @Test
    @DisplayName("DeviceChangeEvent DELETE 移除 ITool")
    void deleteEventRemovesTool() {
        Device device = new Device();
        device.setId(1L);
        device.setType(DeviceType.ATLAS_PF4000.getId());
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(toolHandler);
        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, device));

        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.DELETE, 1L));

        assertThat(registry.getTool(1L)).isNull();
    }

    @Test
    @DisplayName("DeviceChangeEvent UPDATE 重建 ToolAdapter（先 remove 再 register）")
    void updateEventRecreatesTool() {
        Device device = new Device();
        device.setId(1L);
        device.setType(DeviceType.ATLAS_PF4000.getId());
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(toolHandler);
        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, device));

        // UPDATE：设备类型变更（如 ATLAS_PF4000 → ATLAS_PF6000_OP）
        Device updatedDevice = new Device();
        updatedDevice.setId(1L);
        updatedDevice.setType(DeviceType.ATLAS_PF6000_OP.getId());
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF6000_OP)).thenReturn(toolHandler);
        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.UPDATE, updatedDevice));

        ITool tool = registry.getTool(1L);
        assertThat(tool).isNotNull();
        assertThat(tool.type()).isEqualTo(DeviceType.ATLAS_PF6000_OP);
    }

    @Test
    @DisplayName("getAllTools() 返回全部已注册工具")
    void getAllTools() {
        Device d1 = new Device();
        d1.setId(1L);
        d1.setType(DeviceType.ATLAS_PF4000.getId());
        Device d2 = new Device();
        d2.setId(2L);
        d2.setType(DeviceType.FIT_FTC6.getId());
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(toolHandler);
        when(handlerFactory.getHandler(DeviceType.FIT_FTC6)).thenReturn(toolHandler);

        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, d1));
        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, d2));

        assertThat(registry.getAllTools()).hasSize(2);
    }

    @Test
    @DisplayName("非 ToolHandler 类型的 handler 不注册（保留给未来 IArm/IArranger）")
    void nonToolHandlerSkipped() {
        Device device = new Device();
        device.setId(1L);
        device.setType(DeviceType.ATLAS_PF4000.getId());
        DeviceHandler nonToolHandler = new DeviceHandler() {
            @Override public void connect(long id) {}
            @Override public void disconnect(long id) {}
            @Override public DeviceStatus getStatus(long id) { return DeviceStatus.DISCONNECTED; }
            @Override public java.util.Set<DeviceType> getSupportedTypes() { return java.util.Set.of(); }
        };
        when(handlerFactory.getHandler(DeviceType.ATLAS_PF4000)).thenReturn(nonToolHandler);

        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, device));

        assertThat(registry.getTool(1L)).isNull();
    }

    @Test
    @DisplayName("子设备类型 ARM → getArm 返回非 null")
    void armRegistersToDeviceRegistry() {
        // 准备 Mock 通信盒 Handler
        AnengGatewayHandler gwHandler = mock(AnengGatewayHandler.class);
        when(gwHandler.getStatus(anyLong())).thenReturn(DeviceStatus.CONNECTED);
        when(handlerFactory.getHandler(DeviceType.ANENG_GATEWAY)).thenReturn(gwHandler);

        // 准备 ArmModelConfig
        ArmModelConfig model = new ArmModelConfig();
        model.setId(1L);
        model.setXSlaveAddr(1);
        model.setXRegister(0x0003);
        model.setXCount(2);
        model.setYSlaveAddr(2);
        model.setYRegister(0x0003);
        model.setYCount(2);
        model.setParseStrategy("STANDARD");
        when(armModelConfigMapper.selectById(1)).thenReturn(model);

        // 准备 ARM 子设备
        Arm device = new Arm();
        device.setId(100L);
        device.setType(DeviceType.ARM.getId());
        device.setGatewayDeviceId(10L);
        device.setArmModelId(1);

        // 触发 ADD 事件
        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, device));

        // 验证子设备已注册到 DeviceRegistry
        IArm arm = registry.getArm(100L);
        assertThat(arm).isNotNull();
        assertThat(arm.id()).isEqualTo(100L);
        assertThat(arm.type()).isEqualTo(DeviceType.ARM);
    }

    @Test
    @DisplayName("通信盒 DELETE → 级联移除所有子设备")
    void gatewayDeleteCascadesSubDevices() {
        // 准备 Mock 通信盒 Handler
        AnengGatewayHandler gwHandler = mock(AnengGatewayHandler.class);
        when(gwHandler.getStatus(anyLong())).thenReturn(DeviceStatus.CONNECTED);
        when(handlerFactory.getHandler(DeviceType.ANENG_GATEWAY)).thenReturn(gwHandler);

        // 准备 ArmModelConfig
        ArmModelConfig model = new ArmModelConfig();
        model.setId(1L);
        model.setXSlaveAddr(1);
        model.setXRegister(0x0003);
        model.setXCount(2);
        model.setYSlaveAddr(2);
        model.setYRegister(0x0003);
        model.setYCount(2);
        model.setParseStrategy("STANDARD");
        when(armModelConfigMapper.selectById(1)).thenReturn(model);

        // 准备并注册 ARM 子设备
        Arm device = new Arm();
        device.setId(100L);
        device.setType(DeviceType.ARM.getId());
        device.setGatewayDeviceId(10L);
        device.setArmModelId(1);

        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.ADD, device));
        assertThat(registry.getArm(100L)).isNotNull();

        // 触发 DELETE 事件
        registry.onDeviceChange(new DeviceChangeEvent(this, DeviceChangeType.DELETE, 100L));

        // 验证子设备已移除
        assertThat(registry.getArm(100L)).isNull();

        // 验证通信盒的 removeSubDevice 被调用
        verify(gwHandler).removeSubDevice(100L);
    }
}
