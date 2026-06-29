package com.tightening.lifecycle;

import com.tightening.config.LocalSettings;
import com.tightening.constant.MissionResult;
import com.tightening.device.DeviceRegistry;
import com.tightening.device.contract.ITool;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.message.DeviceEvent;
import com.tightening.lifecycle.message.InboundMessage;
import com.tightening.util.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MissionOrchestrator implements DataRouter {

    private static final int MAX_SELF_LOOPS = 1000;

    private final LifecycleEngineFactory factory;
    private final DeviceRegistry deviceRegistry;
    private final LocalSettings settings;
    private final ApplicationEventPublisher publisher;

    /** missionId -> 活跃引擎 */
    private final Map<Long, LifecycleEngine> activeEngines = new ConcurrentHashMap<>();
    /** deviceId -> missionId（数据路由用） */
    private final Map<Long, Long> deviceToMissionId = new ConcurrentHashMap<>();
    /** 自循环计数 */
    private final Map<Long, Integer> selfLoopCounts = new ConcurrentHashMap<>();

    public MissionOrchestrator(LifecycleEngineFactory factory,
                               @Lazy DeviceRegistry deviceRegistry,
                               LocalSettings settings,
                               ApplicationEventPublisher publisher) {
        this.factory = factory;
        this.deviceRegistry = deviceRegistry;
        this.settings = settings;
        this.publisher = publisher;
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

    // === 引擎生命周期管理 ===

    public LifecycleEngine startMission(ProductMission mission, List<ProductBolt> bolts) {
        int loopCount = selfLoopCounts.getOrDefault(mission.getId(), 0);
        if (loopCount >= MAX_SELF_LOOPS) {
            log.warn("Mission {} reached maxSelfLoops, not restarting", mission.getId());
            selfLoopCounts.remove(mission.getId());
            return null;
        }
        selfLoopCounts.put(mission.getId(), loopCount + 1);

        Map<Long, ITool> devices = deviceRegistry.getAllTools().stream()
                .collect(Collectors.toMap(ITool::id, t -> t));
        devices.keySet().forEach(deviceId -> deviceToMissionId.put(deviceId, mission.getId()));

        boolean shouldSelfLoop = settings.selfLoopEnabled();
        LifecycleEngine engine = factory.createEngine(mission, bolts, devices, shouldSelfLoop);

        engine.onCompleted(recordId -> {
            boolean ok = isMissionOk(engine);
            cleanup(mission.getId());
            if (shouldSelfLoop && ok) {
                log.info("Self-loop: publishing event for mission {}", mission.getId());
                publisher.publishEvent(new MissionCompletedEvent(
                        mission.getId(), mission, bolts, true));
            } else {
                selfLoopCounts.remove(mission.getId());
                log.info("Mission {} completed, recordId={}", mission.getId(), recordId);
            }
        });

        engine.onFaulted(reason -> {
            cleanup(mission.getId());
            selfLoopCounts.remove(mission.getId());
            log.warn("Mission {} faulted: {}", mission.getId(), reason);
        });

        activeEngines.put(mission.getId(), engine);
        engine.startMonitorTicks();
        engine.start(engine.getContext());
        log.info("Mission {} started (selfLoop={}, loopCount={})",
                mission.getId(), shouldSelfLoop, loopCount);
        return engine;
    }

    // === 事件监听：自循环重启（@Async 独立线程，不递归） ===

    @Async
    @EventListener
    void handleRestart(MissionCompletedEvent event) {
        if (!event.ok()) return;
        log.info("Restarting mission {} (loop {})", event.missionId(),
                selfLoopCounts.getOrDefault(event.missionId(), 0));
        startMission(event.mission(), event.bolts());
    }

    // === 内部辅助 ===

    private void cleanup(Long missionId) {
        activeEngines.remove(missionId);
        deviceToMissionId.values().removeIf(v -> v.equals(missionId));
    }

    private boolean isMissionOk(LifecycleEngine engine) {
        MissionContext ctx = engine.getContext();
        if (ctx == null || ctx.getMissionRecord() == null) return false;
        return Integer.valueOf(MissionResult.OK.getCode())
                .equals(ctx.getMissionRecord().getMissionResult());
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
