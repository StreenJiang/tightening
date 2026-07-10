package com.tightening.lifecycle;

import com.tightening.config.LocalSettings;
import com.tightening.constant.DeviceType;
import com.tightening.device.contract.ITool;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.judgment.JudgmentStrategy;
import org.springframework.lang.Nullable;
import com.tightening.lifecycle.capability.*;
import com.tightening.lifecycle.monitor.DeviceConnectionMonitor;
import com.tightening.lifecycle.monitor.LockStateMonitor;
import com.tightening.lifecycle.monitor.PersistentMonitor;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.ExportTaskService;
import com.tightening.service.MissionRecordService;
import com.tightening.service.TighteningDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LifecycleEngineFactory {

    private final MissionRecordService missionRecordService;
    private final TighteningDataService tighteningDataService;
    private final ExportTaskService exportTaskService;
    private final LocalSettings settings;
    private final Map<DeviceType, JudgmentStrategy> judgmentStrategies;
    private final BarCodeMatchingRuleService barCodeMatchingRuleService;

    public LifecycleEngine createEngine(
            ProductMission mission,
            List<ProductBolt> bolts,
            Map<Long, ITool> deviceMap,
            boolean shouldSelfLoop,
            @Nullable String productCode,
            @Nullable String partsCode) {

        MissionContext ctx = MissionContext.builder()
            .productMissionId(mission.getId())
            .missionData(mission)
            .boltConfigs(bolts)
            .deviceRegistry(deviceMap)
            .shouldSelfLoop(shouldSelfLoop)
            .productCode(productCode)
            .partsCode(partsCode)
            .build();

        PipelineDefinition pipeline = PipelineDefinition.createDefault();

        List<Capability> capabilities = List.of(
            new WorkstationConfigCheck(),
            new PrepareBolts(),
            new CreateMissionRecord(missionRecordService),
            new SendArrangerSignal(),
            new SendSetterSelector(),
            new SendPSet(),
            new BoltBarCodeCheck(),
            new ReceiveData(),
            new ControllerStatusCheck(),
            new TorqueRangeCheck(),
            new AngleRangeCheck(),
            new ExecuteJudgment(judgmentStrategies),
            new StoreData(tighteningDataService),
            new AdvanceBolt(missionRecordService),
            new CancelTasks(),
            new LockTools(),
            new ResetState(),
            new ExportData(exportTaskService, settings)
        );

        List<PersistentMonitor> monitors = List.of(
            new LockStateMonitor(),
            new DeviceConnectionMonitor()
        );

        LifecycleEngine engine = new LifecycleEngine(pipeline, missionRecordService, capabilities, monitors,
                List.of());
        engine.initContext(ctx);

        engine.onFaulted(reason -> { /* 后续接事件发布 */ });
        engine.onCompleted(recordId -> { /* 后续接完成通知 */ });

        return engine;
    }
}
