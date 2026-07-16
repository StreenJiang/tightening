package com.tightening.lifecycle;

import com.tightening.device.DeviceRegistry;
import com.tightening.device.contract.ITool;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.message.DeviceEvent;
import com.tightening.lifecycle.message.InboundCommand;
import com.tightening.lifecycle.message.InboundMessage;
import com.tightening.util.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MissionOrchestrator implements DataRouter {

    private final LifecycleEngineFactory factory;
    private final DeviceRegistry deviceRegistry;

    /** missionId -> 活跃引擎 */
    private final Map<Long, LifecycleEngine> activeEngines = new ConcurrentHashMap<>();
    /** deviceId -> missionId（数据路由用） */
    private final Map<Long, Long> deviceToMissionId = new ConcurrentHashMap<>();

    public MissionOrchestrator(LifecycleEngineFactory factory,
                               @Lazy DeviceRegistry deviceRegistry) {
        this.factory = factory;
        this.deviceRegistry = deviceRegistry;
    }

    // === DataRouter 接口实现 ===

    @Override
    public void routeTighteningData(long deviceId, TighteningDataDTO dto) {
        Long missionId = deviceToMissionId.get(deviceId);
        if (missionId == null) {
            log.warn("No active mission for deviceId={}, dropping tightening data", deviceId);
            return;
        }
        LifecycleEngine engine = activeEngines.get(missionId);
        if (engine == null) {
            log.warn("Engine for missionId={} not alive, dropping tightening data", missionId);
            return;
        }
        TighteningData data = Converter.dto2Entity(dto, TighteningData::new);
        engine.postMessage(new DeviceEvent.TighteningDataReceived(data, deviceId));
    }

    // === 触发阶段入口 ===

    public LifecycleEngine trigger(ProductMission mission, List<ProductBolt> bolts,
                                    String productCode, String partsCode) {
        Long missionId = mission.getId();
        if (activeEngines.containsKey(missionId)) {
            log.warn("Mission {} already active", missionId);
            return null;
        }

        Map<Long, ITool> devices = deviceRegistry.getAllTools().stream()
                .collect(Collectors.toMap(ITool::id, t -> t));
        LifecycleEngine engine = factory.createEngine(
                mission, bolts, devices,
                productCode, partsCode);

        engine.onTriggered(mId -> {
            engine.getContext().getDeviceRegistry().keySet()
                    .forEach(deviceId -> deviceToMissionId.put(deviceId, mId));
            engine.startMonitorTicks();
        });

        engine.onCompleted(recordId -> cleanup(missionId));

        engine.onFaulted(reason -> {
            cleanup(missionId);
            log.warn("Mission {} trigger faulted: {}", missionId, reason);
        });

        LifecycleEngine prev = activeEngines.putIfAbsent(missionId, engine);
        if (prev != null) {
            engine.shutdown();
            log.warn("Concurrent trigger for mission {}, rejected", missionId);
            return null;
        }
        engine.start(engine.getContext());
        engine.postMessage(new InboundCommand.TriggerRequest(productCode, partsCode));
        log.info("Mission {} trigger posted", missionId);
        return engine;
    }

    // === 内部辅助 ===

    private void cleanup(Long missionId) {
        activeEngines.remove(missionId);
        deviceToMissionId.values().removeIf(v -> v.equals(missionId));
    }

    public Optional<LifecycleEngine> getActiveEngine(Long missionId) {
        return Optional.ofNullable(activeEngines.get(missionId));
    }

    public void postMessage(Long missionId, InboundMessage msg) {
        LifecycleEngine engine = activeEngines.get(missionId);
        if (engine != null) {
            engine.postMessage(msg);
        } else {
            log.debug("No active engine for missionId={}", missionId);
        }
    }
}
