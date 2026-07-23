package com.tightening.lifecycle;

import com.tightening.config.LocalSettings;
import com.tightening.constant.DeviceType;
import com.tightening.device.contract.ITool;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductTask;
import com.tightening.judgment.JudgmentStrategy;
import org.springframework.lang.Nullable;
import com.tightening.lifecycle.capability.*;
import com.tightening.lifecycle.monitor.LockStateMonitor;
import com.tightening.lifecycle.monitor.PersistentMonitor;
import com.tightening.entity.BoltPartsBarcode;
import com.tightening.service.BarCodeMatchingRuleService;
import com.tightening.service.BoltPartsBarcodeService;
import com.tightening.service.ExportTaskService;
import com.tightening.service.TaskRecordService;
import com.tightening.service.TighteningDataService;
import com.tightening.service.WorkplaceStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LifecycleEngineFactory {

    private final TaskRecordService taskRecordService;
    private final TighteningDataService tighteningDataService;
    private final ExportTaskService exportTaskService;
    private final LocalSettings settings;
    private final Map<DeviceType, JudgmentStrategy> judgmentStrategies;
    private final BarCodeMatchingRuleService barCodeMatchingRuleService;
    private final BoltPartsBarcodeService partsBarcodeService;
    private final WorkplaceStatusService workplaceStatusService;

    public LifecycleEngine createEngine(
            ProductTask task,
            List<ProductBolt> bolts,
            Map<Long, ITool> deviceMap,
            @Nullable String productCode,
            @Nullable String partsCode) {

        List<Long> boltIds = bolts.stream().map(ProductBolt::getId).toList();
        Map<Long, Long> barcodeMap = boltIds.isEmpty() ? Map.of() : partsBarcodeService.lambdaQuery()
                .in(BoltPartsBarcode::getProductBoltId, boltIds)
                .list().stream()
                .collect(Collectors.toMap(BoltPartsBarcode::getProductBoltId, BoltPartsBarcode::getBarCodeMatchingRuleId));

        TaskContext ctx = TaskContext.builder()
            .productTaskId(task.getId())
            .taskData(task)
            .boltConfigs(bolts)
            .deviceRegistry(deviceMap)
            .productCode(productCode)
            .partsCode(partsCode)
            .boltBarcodeRuleIds(barcodeMap)
            .build();

        PipelineDefinition pipeline = PipelineDefinition.createDefault();

        List<Capability> capabilities = List.of(
            new WorkstationConfigCheck(),
            new PrepareBolts(),
            new CreateTaskRecord(taskRecordService),
            new SendArrangerSignal(),
            new SendSetterSelector(),
            new SendPSet(),
            new BoltBarCodeCheck(),
            new ReceiveData(),
            new TorqueRangeCheck(),
            new AngleRangeCheck(),
            new ExecuteJudgment(judgmentStrategies),
            new StoreData(tighteningDataService),
            new AdvanceBolt(taskRecordService),
            new CancelTasks(),
            new LockTools(),
            new ResetState(),
            new ExportData(exportTaskService, settings)
        );

        List<PersistentMonitor> monitors = List.of(
            new LockStateMonitor(workplaceStatusService)
        );

        List<TriggerCapability> triggerCaps = List.of(
            new ProductBarCodeCheck(barCodeMatchingRuleService),
            new PartsBarCodeMatching(barCodeMatchingRuleService),
            new SkipScrewCheck()
        );

        LifecycleEngine engine = new LifecycleEngine(pipeline, taskRecordService, capabilities, monitors,
                triggerCaps, workplaceStatusService);
        engine.initContext(ctx);

        engine.onFaulted(reason -> { /* 后续接事件发布 */ });
        engine.onCompleted(recordId -> { /* 后续接完成通知 */ });

        return engine;
    }
}
