package com.tightening.device;

import com.tightening.constant.DeviceStatus;
import com.tightening.constant.DeviceType;
import com.tightening.device.contract.IArranger;
import com.tightening.device.contract.IArm;
import com.tightening.device.contract.ISetterSelector;
import com.tightening.device.contract.ITool;
import com.tightening.device.contract.ToolAdapter;
import com.tightening.device.event.DeviceChangeEvent;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.DeviceHandlerFactory;
import com.tightening.device.handler.ToolHandler;
import com.tightening.device.handler.impl.AnengGatewayHandler;
import com.tightening.device.handler.impl.aneng.ArmAdapter;
import com.tightening.device.handler.impl.aneng.ArrangerAdapter;
import com.tightening.device.handler.impl.aneng.SetterSelectorAdapter;
import com.tightening.device.type.Arranger;
import com.tightening.device.type.SetterSelector;
import com.tightening.entity.ArmModelConfig;
import com.tightening.entity.Device;
import com.tightening.lifecycle.DataRouter;
import com.tightening.mapper.ArmModelConfigMapper;
import com.tightening.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DeviceRegistry {

    private final Map<Long, ITool> tools = new ConcurrentHashMap<>();
    private final Map<Long, IArm> arms = new ConcurrentHashMap<>();
    private final Map<Long, ISetterSelector> setterSelectors = new ConcurrentHashMap<>();
    private final Map<Long, IArranger> arrangers = new ConcurrentHashMap<>();
    private final Map<Long, Long> gatewayMap = new ConcurrentHashMap<>(); // 子设备→通信盒

    private final DeviceHandlerFactory handlerFactory;
    private final DataRouter dataRouter;
    private final DeviceService deviceService;
    private final ArmModelConfigMapper armModelConfigMapper;

    public DeviceRegistry(DeviceHandlerFactory handlerFactory, DataRouter dataRouter,
                         DeviceService deviceService, ArmModelConfigMapper armModelConfigMapper) {
        this.handlerFactory = handlerFactory;
        this.dataRouter = dataRouter;
        this.deviceService = deviceService;
        this.armModelConfigMapper = armModelConfigMapper;
    }

    // === Tool registry ===

    public ITool getTool(Long deviceId) {
        return tools.get(deviceId);
    }

    public List<ITool> getAllTools() {
        return List.copyOf(tools.values());
    }

    // === Sub-device registry ===

    public IArm getArm(Long deviceId) {
        return arms.get(deviceId);
    }

    public ISetterSelector getSetterSelector(Long deviceId) {
        return setterSelectors.get(deviceId);
    }

    public IArranger getArranger(Long deviceId) {
        return arrangers.get(deviceId);
    }

    public Map<Long, IArm> getAllArms() {
        return Map.copyOf(arms);
    }

    public Map<Long, ISetterSelector> getAllSetterSelectors() {
        return Map.copyOf(setterSelectors);
    }

    public Map<Long, IArranger> getAllArrangers() {
        return Map.copyOf(arrangers);
    }

    // === Event handling ===

    @TransactionalEventListener
    void onDeviceChange(DeviceChangeEvent event) {
        Device device = event.getDevice();
        DeviceType type = device != null ? DeviceType.getType(device.getType()) : null;

        switch (event.getEventType()) {
            case ADD -> {
                if (type == DeviceType.ANENG_GATEWAY) {
                    registerGatewaySubDevices(device);
                } else if (isSubDevice(type)) {
                    registerSubDevice(device, type);
                } else {
                    registerTool(device);
                }
            }
            case UPDATE -> {
                if (type == DeviceType.ANENG_GATEWAY || isSubDevice(type)) {
                    removeSubDeviceReg(event.getDeviceId());
                    if (type == DeviceType.ANENG_GATEWAY) {
                        registerGatewaySubDevices(device);
                    } else {
                        registerSubDevice(device, type);
                    }
                } else {
                    tools.remove(event.getDeviceId());
                    registerTool(device);
                }
            }
            case DELETE -> {
                tools.remove(event.getDeviceId());
                removeSubDeviceReg(event.getDeviceId());
            }
        }
    }

    // === Tool registration ===

    private void registerTool(Device device) {
        DeviceHandler handler = handlerFactory.getHandler(DeviceType.getType(device.getType()));
        if (handler instanceof ToolHandler toolHandler) {
            ToolAdapter toolAdapter = new ToolAdapter(toolHandler, device);
            toolHandler.setToolAdapter(toolAdapter);
            toolAdapter.onTighteningData(dto -> dataRouter.routeTighteningData(device.getId(), dto));
            tools.put(device.getId(), toolAdapter);
            log.debug("Registered ITool for device {} (type={})", device.getId(), device.getType());
        }
    }

    // === Sub-device helpers ===

    private boolean isSubDevice(DeviceType type) {
        return type.isSubDevice();
    }

    private void registerGatewaySubDevices(Device gatewayDevice) {
        Long gatewayId = gatewayDevice.getId();
        if (gatewayId == null) return;

        List<Device> subDevices = deviceService.lambdaQuery()
                .eq(Device::getGatewayDeviceId, gatewayId)
                .list();
        for (Device sub : subDevices) {
            registerSubDevice(sub, DeviceType.getType(sub.getType()));
        }
    }

    private void registerSubDevice(Device device, DeviceType type) {
        if (type == null || device.getGatewayDeviceId() == null) return;

        DeviceHandler handler = getGatewayHandler();
        if (!(handler instanceof AnengGatewayHandler gw)) return;
        if (gw.getStatus(device.getGatewayDeviceId()) != DeviceStatus.CONNECTED) return;

        switch (type) {
            case ARM -> {
                if (device.getArmModelId() == null) {
                    log.warn("Arm device {} has no armModelId, skipping registration", device.getId());
                    return;
                }
                ArmModelConfig model = armModelConfigMapper.selectById(device.getArmModelId());
                if (model == null) {
                    log.warn("ArmModelConfig not found for id={}, skipping arm {}", device.getArmModelId(), device.getId());
                    return;
                }
                ArmAdapter adapter = new ArmAdapter(gw, device.getGatewayDeviceId(), model, device);
                gw.registerArm(device.getGatewayDeviceId(), device.getId(), adapter);
                arms.put(device.getId(), adapter);
                gatewayMap.put(device.getId(), device.getGatewayDeviceId());
                log.info("Registered arm device {} under gateway {}", device.getId(), device.getGatewayDeviceId());
            }
            case SETTER_SELECTOR -> {
                int count = 8; // 默认 8 通道
                if (device instanceof SetterSelector ss && ss.getSetterCount() != null) {
                    count = ss.getSetterCount();
                }
                SetterSelectorAdapter adapter = new SetterSelectorAdapter(gw,
                        device.getGatewayDeviceId(), device, count);
                gw.registerSetterSelector(device.getGatewayDeviceId(), device.getId(), adapter);
                setterSelectors.put(device.getId(), adapter);
                gatewayMap.put(device.getId(), device.getGatewayDeviceId());
                log.info("Registered setter selector device {} under gateway {}", device.getId(), device.getGatewayDeviceId());
            }
            case ARRANGER -> {
                boolean rev = false;
                if (device instanceof Arranger arr && arr.getReverseFirstFour() != null) {
                    rev = arr.getReverseFirstFour();
                }
                ArrangerAdapter adapter = new ArrangerAdapter(gw, device.getGatewayDeviceId(), device, rev);
                gw.registerArranger(device.getGatewayDeviceId(), device.getId(), adapter);
                arrangers.put(device.getId(), adapter);
                gatewayMap.put(device.getId(), device.getGatewayDeviceId());
                log.info("Registered arranger device {} under gateway {}", device.getId(), device.getGatewayDeviceId());
            }
        }
    }

    private void removeSubDeviceReg(long deviceId) {
        arms.remove(deviceId);
        setterSelectors.remove(deviceId);
        arrangers.remove(deviceId);
        Long gwId = gatewayMap.remove(deviceId);
        if (gwId != null) {
            DeviceHandler handler = getGatewayHandler();
            if (handler instanceof AnengGatewayHandler gw) {
                gw.removeSubDevice(deviceId);
            }
        }
    }

    private DeviceHandler getGatewayHandler() {
        return handlerFactory.getHandler(DeviceType.ANENG_GATEWAY);
    }
}
