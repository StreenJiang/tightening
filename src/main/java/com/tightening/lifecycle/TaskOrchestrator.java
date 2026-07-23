package com.tightening.lifecycle;

import com.tightening.constant.SseEventType;
import com.tightening.device.DeviceRegistry;
import com.tightening.device.contract.ITool;
import com.tightening.dto.SseEvent;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.message.DeviceEvent;
import com.tightening.lifecycle.message.InboundCommand;
import com.tightening.lifecycle.message.InboundMessage;
import com.tightening.service.SseService;
import com.tightening.util.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TaskOrchestrator implements DataRouter {

    private final LifecycleEngineFactory factory;
    private final DeviceRegistry deviceRegistry;
    private final SseService sseService;

    /** taskId -> 活跃引擎 */
    private final Map<Long, LifecycleEngine> activeEngines = new ConcurrentHashMap<>();
    /** deviceId -> taskId（数据路由用） */
    private final Map<Long, Long> deviceToTaskId = new ConcurrentHashMap<>();

    public TaskOrchestrator(LifecycleEngineFactory factory,
                               @Lazy DeviceRegistry deviceRegistry,
                               SseService sseService) {
        this.factory = factory;
        this.deviceRegistry = deviceRegistry;
        this.sseService = sseService;
    }

    // === DataRouter 接口实现 ===

    @Override
    public void routeTighteningData(long deviceId, TighteningDataDTO dto) {
        Long taskId = deviceToTaskId.get(deviceId);
        if (taskId == null) {
            log.warn("No active task for deviceId={}, dropping tightening data", deviceId);
            return;
        }
        LifecycleEngine engine = activeEngines.get(taskId);
        if (engine == null) {
            log.warn("Engine for taskId={} not alive, dropping tightening data", taskId);
            return;
        }
        TighteningData data = Converter.dto2Entity(dto, TighteningData::new);
        engine.postMessage(new DeviceEvent.TighteningDataReceived(data, deviceId));
    }

    // === 触发阶段入口 ===

    public LifecycleEngine trigger(ProductTask task, List<ProductBolt> bolts,
                                    String productCode, String partsCode) {
        Long taskId = task.getId();
        if (activeEngines.containsKey(taskId)) {
            log.warn("Task {} already active", taskId);
            return null;
        }

        Map<Long, ITool> devices = deviceRegistry.getAllTools().stream()
                .collect(Collectors.toMap(ITool::id, t -> t));
        LifecycleEngine engine = factory.createEngine(
                task, bolts, devices,
                productCode, partsCode);

        engine.onTriggered(mId -> {
            engine.getContext().getDeviceRegistry().keySet()
                    .forEach(deviceId -> deviceToTaskId.put(deviceId, mId));
            engine.startMonitorTicks();
        });

        engine.onCompleted(recordId -> cleanup(taskId));

        engine.onFaulted(reason -> {
            cleanup(taskId);
            log.warn("Task {} trigger faulted: {}", taskId, reason);
        });

        engine.onTighteningJudged(data -> {
            TighteningDataDTO dto = Converter.entity2Dto(data, TighteningDataDTO::new);
            sseService.emit(new SseEvent(SseEventType.TIGHTENING_DATA, dto, LocalDateTime.now()));
        });

        LifecycleEngine prev = activeEngines.putIfAbsent(taskId, engine);
        if (prev != null) {
            engine.shutdown();
            log.warn("Concurrent trigger for task {}, rejected", taskId);
            return null;
        }
        engine.start(engine.getContext());
        engine.postMessage(new InboundCommand.TriggerRequest(productCode, partsCode));
        log.info("Task {} trigger posted", taskId);
        return engine;
    }

    // === 内部辅助 ===

    private void cleanup(Long taskId) {
        activeEngines.remove(taskId);
        deviceToTaskId.values().removeIf(v -> v.equals(taskId));
    }

    public Optional<LifecycleEngine> getActiveEngine(Long taskId) {
        return Optional.ofNullable(activeEngines.get(taskId));
    }

    public void postMessage(Long taskId, InboundMessage msg) {
        LifecycleEngine engine = activeEngines.get(taskId);
        if (engine != null) {
            engine.postMessage(msg);
        } else {
            log.debug("No active engine for taskId={}", taskId);
        }
    }
}
