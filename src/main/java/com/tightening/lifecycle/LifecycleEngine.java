package com.tightening.lifecycle;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.constant.WorkplaceStatus;
import com.tightening.device.contract.ITool;
import com.tightening.entity.TaskRecord;
import com.tightening.entity.TighteningData;
import com.tightening.i18n.BusinessException;

import com.tightening.lifecycle.capability.Capability;
import com.tightening.lifecycle.capability.CapabilityResult;
import com.tightening.lifecycle.capability.ErrorAction;
import com.tightening.lifecycle.capability.TriggerCapability;
import com.tightening.lifecycle.message.*;
import com.tightening.lifecycle.monitor.PersistentMonitor;
import com.tightening.service.TaskRecordService;
import com.tightening.service.WorkplaceStatusService;
import com.tightening.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
public class LifecycleEngine {

    @FunctionalInterface
    public interface MessageHandler {
        void handle(InboundMessage msg, TaskContext ctx, LifecycleEngine engine);
    }

    private final BlockingQueue<InboundMessage> inbox = new LinkedBlockingQueue<>(1024);
    private final Map<Class<?>, MessageHandler> handlers = new HashMap<>();

    private TaskContext context;
    private final PipelineDefinition pipeline;
    private final TaskRecordService taskRecordService;
    private final List<PersistentMonitor> monitors;

    private volatile boolean alive = false;
    private Thread actorThread;
    private final ScheduledExecutorService tickScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lifecycle-tick");
        t.setDaemon(true);
        return t;
    });

    private Consumer<String> onFaulted;
    private Consumer<Long> onCompleted;
    private Consumer<Long> onTriggered;
    private Consumer<TighteningData> onTighteningJudged;

    private final List<TriggerCapability> triggerCaps;
    private final WorkplaceStatusService wsService;

    public LifecycleEngine(PipelineDefinition pipeline, TaskRecordService taskRecordService,
                           List<Capability> capabilities, List<PersistentMonitor> monitors,
                           List<TriggerCapability> triggerCapabilities,
                           WorkplaceStatusService wsService) {
        this.pipeline = pipeline;
        this.taskRecordService = taskRecordService;
        this.monitors = monitors != null ? monitors : List.of();
        this.triggerCaps = triggerCapabilities != null ? triggerCapabilities : List.of();
        this.wsService = wsService;
        pipeline.registerCapabilities(capabilities).sortByPriority();
        registerDefaultHandlers();
    }

    public void registerHandler(Class<?> msgType, MessageHandler handler) {
        handlers.put(msgType, handler);
    }

    public void onFaulted(Consumer<String> callback) { this.onFaulted = callback; }
    public void onCompleted(Consumer<Long> callback) { this.onCompleted = callback; }
    public void onTriggered(Consumer<Long> callback) { this.onTriggered = callback; }
    public void onTighteningJudged(Consumer<TighteningData> cb) { this.onTighteningJudged = cb; }

    private void registerDefaultHandlers() {
        registerHandler(InboundCommand.TriggerRequest.class, this::handleTriggerRequest);
        registerHandler(InboundCommand.AdvancePipeline.class, this::handleAdvancePipeline);
        registerHandler(DeviceEvent.TighteningDataReceived.class, this::handleTighteningData);
        registerHandler(EngineInternal.Faulted.class, this::handleFaulted);
        registerHandler(InboundCommand.InterruptTask.class, this::handleInterrupt);
        registerHandler(EngineInternal.MonitorTick.class, this::handleMonitorTick);
    }

    private final Map<PersistentMonitor, Long> monitorLastRun = new java.util.concurrent.ConcurrentHashMap<>();

    void handleMonitorTick(InboundMessage msg, TaskContext ctx, LifecycleEngine engine) {
        if (ctx == null) return;
        long now = System.currentTimeMillis();
        for (PersistentMonitor m : monitors) {
            long last = monitorLastRun.getOrDefault(m, 0L);
            if (now - last < m.intervalMs()) continue;
            monitorLastRun.put(m, now);
            try {
                m.execute(ctx);
            } catch (Exception e) {
                log.warn("Monitor {} error", m.getClass().getSimpleName(), e);
            }
        }
    }

    // === Actor 主循环 ===

    private void actorLoop() {
        while (alive) {
            InboundMessage msg;
            try {
                msg = inbox.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                MessageHandler handler = handlers.get(msg.getClass());
                if (handler != null) {
                    handler.handle(msg, context, this);
                } else {
                    log.warn("Unknown message type: {}", msg.getClass().getSimpleName());
                }
            } catch (Exception e) {
                handleActorCrash(e, msg);
            }
        }
    }

    // === 消息 Handler ===

    void handleTriggerRequest(InboundMessage msg, TaskContext ctx, LifecycleEngine engine) {
        var cmd = (InboundCommand.TriggerRequest) msg;
        log.info("Trigger request: productCode={}, partsCode={}", cmd.productCode(), cmd.partsCode());

        ctx.setProductCode(cmd.productCode());
        ctx.setPartsCode(cmd.partsCode());

        CapabilityResult triggerResult = executeTriggerPipeline(ctx);
        if (triggerResult == CapabilityResult.Fail) {
            log.warn("Trigger pipeline failed");
            if (onFaulted != null) onFaulted.accept("Trigger validation failed");
            shutdown();
            return;
        }

        if (triggerResult == CapabilityResult.Interrupt) {
            // SkipScrew fast track — 不绑定设备，直接创建 OK TaskRecord 进 FINALIZATION
            log.info("SkipScrew fast track — entering FINALIZATION");
            startSkipScrewLifecycle(ctx);
            return;
        }

        log.info("Trigger passed, entering lifecycle");
        wsService.transitionTo(WorkplaceStatus.ACTIVATED, Set.of());
        if (onTriggered != null) onTriggered.accept(ctx.getProductTaskId());
        startNormalLifecycle(ctx);
    }

    private CapabilityResult executeTriggerPipeline(TaskContext ctx) {
        for (TriggerCapability cap : triggerCaps) {
            if (!cap.precondition(ctx)) continue;
            try {
                CapabilityResult result = cap.execute(ctx);
                switch (result) {
                    case Pass, Skip -> {}
                    case Fail -> { return CapabilityResult.Fail; }
                    case Interrupt -> { return CapabilityResult.Interrupt; }
                }
            } catch (Exception e) {
                ErrorAction action = cap.onError(ctx, e);
                log.error("Trigger capability {} error: {}", cap.id(), e.getMessage());
                if (action == ErrorAction.FAIL_STAGE) return CapabilityResult.Fail;
            }
        }
        return CapabilityResult.Pass;
    }


    private void startNormalLifecycle(TaskContext ctx) {
        int boltCount = ctx.getBoltConfigs().size();
        BoltState[] states = new BoltState[boltCount];
        Arrays.fill(states, BoltState.PENDING);
        ctx.setBoltStates(states);
        ctx.setCurrentStage(Stage.VALIDATION);
        ctx.setCurrentSubState(SubState.VALIDATING);
        postMessage(new InboundCommand.AdvancePipeline());
    }

    private void startSkipScrewLifecycle(TaskContext ctx) {
        // 创建 OK TaskRecord — createRecord 默认设 taskResult=NG，需后续 markAsOk
        var record = taskRecordService.createRecord(
                ctx.getProductTaskId(), ctx.getProductCode(), ctx.getPartsCode(), 0);
        taskRecordService.markAsOk(record.getId());
        ctx.setTaskRecord(record);
        ctx.setCurrentStage(Stage.FINALIZATION);
        ctx.setCurrentSubState(SubState.CLEANING_TASKS);
        postMessage(new InboundCommand.AdvancePipeline());
    }

    void handleAdvancePipeline(InboundMessage msg, TaskContext ctx, LifecycleEngine engine) {
        if (ctx == null) return;
        advancePipeline();
    }

    void handleTighteningData(InboundMessage msg, TaskContext ctx, LifecycleEngine engine) {
        if (ctx == null) return;
        var event = (DeviceEvent.TighteningDataReceived) msg;
        var data = event.data();
        log.debug("Tightening data: device={}, tighteningId={}",
            event.deviceId(), data.getTighteningId());

        // 不回传 vin/parameterSet/timestamp 的协议 → 系统回填
        if (data.getVin() == null || data.getVin().isEmpty()) {
            data.setVin(ctx.getProductCode());
        }
        if (data.getParameterSet() <= 0) {
            Integer pset = ctx.getCurrentPSet();
            if (pset != null) data.setParameterSet(pset);
        }
        if (data.getTimestamp() == null || data.getTimestamp().isEmpty()) {
            data.setTimestamp(LocalDateTime.now().toString());
        }

        ctx.setCurrentOperationData(data);

        // 解析设备类型供 ExecuteJudgment 使用
        ITool tool = ctx.getDeviceRegistry().get(event.deviceId());
        if (tool != null) {
            ctx.setCurrentDeviceType(tool.type());
        }

        if (ctx.getCurrentBoltIndex() >= 0 && ctx.getCurrentBoltIndex() < ctx.getBoltStates().length) {
            ctx.getBoltStates()[ctx.getCurrentBoltIndex()] = BoltState.TIGHTENING;
        }

        // 从当前 TIGHTENING_RECEIVED 等待点开始推进管道
        advancePipeline();
    }

    void handleFaulted(InboundMessage msg, TaskContext ctx, LifecycleEngine engine) {
        var fault = (EngineInternal.Faulted) msg;
        log.error("Engine faulted: {}", fault.reason());
        if (ctx != null) {
            ctx.setCurrentStage(Stage.FINALIZATION);
            ctx.setCurrentSubState(SubState.FAULTED);
        }
        if (onFaulted != null) onFaulted.accept(fault.reason());
        shutdown();
    }

    void handleInterrupt(InboundMessage msg, TaskContext ctx, LifecycleEngine engine) {
        var cmd = (InboundCommand.InterruptTask) msg;
        log.warn("Engine interrupted: {}", cmd.reason());
        if (ctx != null) {
            ctx.setInterruptRequested(true);
            ctx.setInterruptReason(cmd.reason());
        }
    }

    // === 管道推进 ===

    private void advancePipeline() {
        if (context == null) return;
        if (context.isInterruptRequested()) {
            postMessage(new EngineInternal.Faulted("Interrupted: " + context.getInterruptReason()));
            return;
        }

        Stage stage = context.getCurrentStage();
        SubState subState = context.getCurrentSubState();

        log.debug("Advancing: stage={}, subState={}, bolt={}/{}",
            stage, subState, context.getCurrentBoltIndex() + 1, context.totalBolts());

        List<Capability> caps = pipeline.getCapabilities(stage, subState);
        for (Capability cap : caps) {
            if (!cap.precondition(context)) {
                log.debug("Capability {} precondition not met, skipping", cap.id());
                continue;
            }
            try {
                CapabilityResult result = cap.execute(context);
                switch (result) {
                    case Pass -> {}
                    case Fail -> { handleStageFailure(cap); return; }
                    case Skip -> log.debug("Capability {} skipped", cap.id());
                    case Interrupt -> {
                        postMessage(new EngineInternal.Faulted("Interrupted by: " + cap.id()));
                        return;
                    }
                }
            } catch (Exception e) {
                ErrorAction action = cap.onError(context, e);
                log.error("Capability {} threw: {}", cap.id(), e.getMessage(), e);
                switch (action) {
                    case FAIL_CAPABILITY, FAIL_STAGE -> { handleStageFailure(cap); return; }
                    case RETRY_LATER -> {
                        tickScheduler.schedule(() -> {
                            boolean accepted = inbox.offer(new InboundCommand.AdvancePipeline());
                            if (!accepted) {
                                log.warn("Retry AdvancePipeline dropped (inbox full)");
                            }
                        }, 100, TimeUnit.MILLISECONDS);
                        return;
                    }
                    case INTERRUPT -> {
                        postMessage(new EngineInternal.Faulted("Interrupted by error in: " + cap.id()));
                        return;
                    }
                }
            }
        }

        if (onTighteningJudged != null
                && stage == Stage.OPERATION && subState == SubState.JUDGING
                && context.getJudgeResult() != null
                && context.getCurrentOperationData() != null) {
            onTighteningJudged.accept(context.getCurrentOperationData());
        }

        // Capability 可能重定向管道（如 AdvanceBolt → FINALIZATION）
        if (context.getCurrentStage() != stage || context.getCurrentSubState() != subState) {
            stage = context.getCurrentStage();
            subState = context.getCurrentSubState();
            log.debug("Pipeline redirected by Capability to: {}/{}", stage, subState);
            saveCheckpoint("PostCapRedirect-" + stage + "/" + subState);
            if (!pipeline.isWaitingPoint(stage, subState)) {
                postMessage(new InboundCommand.AdvancePipeline());
            }
            return;
        }

        // 推进到下一子状态
        PipelineDefinition.Transition next = pipeline.getNext(stage, subState);

        // 终点检测：不变则终止
        if (next.nextStage() == stage && next.nextSubState() == subState) {
            log.info("Pipeline reached terminal state: {}/{}", stage, subState);
            if (onCompleted != null && context.getTaskRecord() != null) {
                onCompleted.accept(context.getTaskRecord().getId());
            }
            shutdown();
            return;
        }

        context.setCurrentStage(next.nextStage());
        context.setCurrentSubState(next.nextSubState());
        log.debug("Advanced to {}/{}", next.nextStage(), next.nextSubState());

        saveCheckpoint("Post-" + stage + "/" + subState);

        if (!pipeline.isWaitingPoint(next.nextStage(), next.nextSubState())) {
            postMessage(new InboundCommand.AdvancePipeline());
        } else {
            log.debug("Pipeline waiting at: {}/{}", next.nextStage(), next.nextSubState());
        }
    }

    private void handleStageFailure(Capability failedCap) {
        log.error("Stage failure at: {}", failedCap.id());
        context.setCurrentStage(Stage.FINALIZATION);
        context.setCurrentSubState(SubState.FAULTED);
        if (context.getTaskRecord() != null && context.getTaskRecord().getId() != null) {
            taskRecordService.markFaulted(
                context.getTaskRecord().getId(),
                BusinessException.toErrorString("capability.failed", failedCap.id()));
        }
        saveCheckpoint("StageFailure:" + failedCap.id());
        if (onFaulted != null) onFaulted.accept("Capability failed: " + failedCap.id());
        shutdown();
    }

    // === 崩溃恢复 ===

    private void handleActorCrash(Exception e, InboundMessage msg) {
        log.error("Actor thread crashed, message={}", msg, e);
        if (context == null) return;
        if (context.getTaskRecord() != null && context.getTaskRecord().getId() != null) {
            try {
                String faultMsg = BusinessException.toPersistenceString(e);
                taskRecordService.markFaulted(
                    context.getTaskRecord().getId(), faultMsg);
            } catch (Exception markEx) {
                log.error("Failed to mark faulted", markEx);
            }
        }
        try {
            saveCheckpoint("Crash:" + (msg != null ? msg.getClass().getSimpleName() : "unknown"));
        } catch (Exception cpEx) {
            log.error("Failed to save checkpoint", cpEx);
        }
        boolean accepted = inbox.offer(new EngineInternal.Faulted(e.getMessage()));
        if (!accepted) {
            log.error("Faulted message dropped (inbox full) — engine may hang: {}", e.getMessage());
        }
    }

    private void saveCheckpoint(String reason) {
        if (context == null || context.getTaskRecord() == null) return;
        ContextCheckpoint cp = ContextCheckpoint.builder()
            .taskId(context.getProductTaskId())
            .taskRecordId(context.getTaskRecord().getId())
            .stage(context.getCurrentStage())
            .subState(context.getCurrentSubState())
            .currentBoltIndex(context.getCurrentBoltIndex())
            .currentSideIndex(context.getCurrentSideIndex())
            .completedBolts((int) Arrays.stream(context.getBoltStates())
                .filter(s -> s == BoltState.JUDGED_OK || s == BoltState.JUDGED_NG).count())
            .dataStored(context.getCurrentOperationData() == null
                || context.getCurrentOperationData().getTaskRecordId() != null)
            .snapshotReason(reason)
            .timestamp(System.currentTimeMillis())
            .build();
        context.setCheckpoint(cp);
        // 持久化到 DB
        String json = JsonUtils.toJson(cp);
        taskRecordService.updateSnapshot(context.getTaskRecord().getId(), json);
    }

    // === MonitorTick ===

    public void startMonitorTicks() {
        tickScheduler.scheduleAtFixedRate(
            () -> inbox.offer(new EngineInternal.MonitorTick()),
            0, 50, TimeUnit.MILLISECONDS);
    }

    public void stopMonitorTicks() {
        tickScheduler.shutdownNow();
    }

    // === 生命周期控制 ===

    public void start(TaskContext ctx) {
        this.context = ctx;
        this.alive = true;
        actorThread = new Thread(this::actorLoop, "lifecycle-engine-" + ctx.getProductTaskId());
        actorThread.setUncaughtExceptionHandler((t, throwable) -> {
            log.error("Actor thread uncaught exception", throwable);
            // 线程即将死亡，直接执行崩溃逻辑并 shutdown，不投递到 inbox
            if (context != null && context.getTaskRecord() != null && context.getTaskRecord().getId() != null) {
                String faultMsg = BusinessException.toPersistenceString(throwable);
                try { taskRecordService.markFaulted(context.getTaskRecord().getId(), faultMsg); }
                catch (Exception ex) { log.error("Failed to mark faulted", ex); }
            }
            try { saveCheckpoint("Uncaught:" + throwable.getClass().getSimpleName()); }
            catch (Exception ex) { log.error("Failed to save checkpoint", ex); }
            if (onFaulted != null) onFaulted.accept(throwable.getMessage());
            shutdown();
        });
        actorThread.start();
    }

    public void postMessage(InboundMessage msg) {
        if (!alive) return;
        boolean accepted = inbox.offer(msg);
        if (!accepted) {
            log.warn("Inbox FULL (capacity={}), dropping message: {}",
                    inbox.size() + inbox.remainingCapacity(),
                    msg.getClass().getSimpleName());
        }
    }

    public void interrupt(String reason) {
        if (context != null && context.getCurrentStage() == Stage.FINALIZATION) return;
        postMessage(new InboundCommand.InterruptTask(reason));
    }

    public boolean isAlive() { return alive; }

    public TaskContext getContext() { return context; }

    /** 设置上下文但不启动引擎，由工厂在组装后调用 */
    void initContext(TaskContext ctx) { this.context = ctx; }

    void shutdown() {
        alive = false;
        stopMonitorTicks();
        if (actorThread != null) actorThread.interrupt();
        wsService.reset();
    }
}
