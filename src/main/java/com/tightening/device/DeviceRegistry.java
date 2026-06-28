package com.tightening.device;

import com.tightening.constant.DeviceType;
import com.tightening.device.contract.ITool;
import com.tightening.device.contract.ToolAdapter;
import com.tightening.device.event.DeviceChangeEvent;
import com.tightening.device.handler.DeviceHandler;
import com.tightening.device.handler.DeviceHandlerFactory;
import com.tightening.device.handler.ToolHandler;
import com.tightening.entity.Device;
import com.tightening.lifecycle.DataRouter;
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
    private final DeviceHandlerFactory handlerFactory;
    private final DataRouter dataRouter;

    public DeviceRegistry(DeviceHandlerFactory handlerFactory, DataRouter dataRouter) {
        this.handlerFactory = handlerFactory;
        this.dataRouter = dataRouter;
    }

    public ITool getTool(Long deviceId) {
        return tools.get(deviceId);
    }

    public List<ITool> getAllTools() {
        return List.copyOf(tools.values());
    }

    @TransactionalEventListener
    void onDeviceChange(DeviceChangeEvent event) {
        switch (event.getEventType()) {
            case ADD    -> registerTool(event.getDevice());
            case UPDATE -> {
                tools.remove(event.getDeviceId());
                registerTool(event.getDevice());
            }
            case DELETE -> tools.remove(event.getDeviceId());
        }
    }

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
}
