package com.tightening.lifecycle;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ITool;
import com.tightening.entity.MissionRecord;
import com.tightening.lifecycle.capability.Capability;
import com.tightening.lifecycle.capability.CapabilityResult;
import com.tightening.lifecycle.capability.ErrorAction;
import com.tightening.lifecycle.message.*;
import com.tightening.lifecycle.monitor.PersistentMonitor;
import com.tightening.service.MissionRecordService;
import com.tightening.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
public class LifecycleEngine {

    @FunctionalInterface
    public interface MessageHandler {
        void handle(InboundMessage msg, MissionContext ctx, LifecycleEngine engine);
    }

    private final BlockingQueue<InboundMessage> inbox = new LinkedBlockingQueue<>();
    private final Map<Class<?>, MessageHandler> handlers = new HashMap<>();

    private MissionContext context;
    private final PipelineDefinition pipeline;
    private final MissionRecordService missionRecordService;
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

    public LifecycleEngine(PipelineDefinition pipeline, MissionRecordService missionRecordService,
                           List<Capability> capabilities, List<PersistentMonitor> monitors) {
        this.pipeline = pipeline;
        this.missionRecordService = missionRecordService;
        this.monitors = monitors != null ? monitors : List.of();
        pipeline.registerCapabilities(capabilities).sortByPriority();
        registerDefaultHandlers();
    }

    public void registerHandler(Class<?> msgType, MessageHandler handler) {
        handlers.put(msgType, handler);
    }

    public void onFaulted(Consumer<String> callback) { this.onFaulted = callback; }
    public void onCompleted(Consumer<Long> callback) { this.onCompleted = callback; }

    private void registerDefaultHandlers() {
        registerHandler(InboundCommand.ActivateMission.class, this::handleActivateMission);
        registerHandler(InboundCommand.AdvancePipeline.class, this::handleAdvancePipeline);
        registerHandler(DeviceEvent.TighteningDataReceived.class, this::handleTighteningData);
        registerHandler(EngineInternal.Faulted.class, this::handleFaulted);
        registerHandler(InboundCommand.InterruptMission.class, this::handleInterrupt);
        registerHandler(EngineInternal.MonitorTick.class, this::handleMonitorTick);
    }

    private final Map<PersistentMonitor, Long> monitorLastRun = new java.util.concurrent.ConcurrentHashMap<>();

    void handleMonitorTick(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
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

    void handleActivateMission(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
        var cmd = (InboundCommand.ActivateMission) msg;
        log.info("Engine activating mission: {}", cmd.missionData().getId());

        int boltCount = cmd.bolts().size();
        BoltState[] states = new BoltState[boltCount];
        Arrays.fill(states, BoltState.PENDING);
        ctx.setBoltStates(states);

        ctx.setCurrentStage(Stage.VALIDATION);
        ctx.setCurrentSubState(SubState.VALIDATING);

        postMessage(new InboundCommand.AdvancePipeline());
    }

    void handleAdvancePipeline(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
        if (ctx == null) return;
        advancePipeline();
    }

    void handleTighteningData(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
        if (ctx == null) return;
        var event = (DeviceEvent.TighteningDataReceived) msg;
        log.debug("Tightening data: device={}, tighteningId={}",
            event.deviceId(), event.data().getTighteningId());

        ctx.setCurrentOperationData(event.data());

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

    void handleFaulted(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
        var fault = (EngineInternal.Faulted) msg;
        log.error("Engine faulted: {}", fault.reason());
        if (ctx != null) {
            ctx.setCurrentStage(Stage.FINALIZATION);
            ctx.setCurrentSubState(SubState.FAULTED);
        }
        if (onFaulted != null) onFaulted.accept(fault.reason());
        shutdown();
    }

    void handleInterrupt(InboundMessage msg, MissionContext ctx, LifecycleEngine engine) {
        var cmd = (InboundCommand.InterruptMission) msg;
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
                        tickScheduler.schedule(
                            () -> inbox.offer(new InboundCommand.AdvancePipeline()),
                            100, TimeUnit.MILLISECONDS);
                        return;
                    }
                    case INTERRUPT -> {
                        postMessage(new EngineInternal.Faulted("Interrupted by error in: " + cap.id()));
                        return;
                    }
                }
            }
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
            if (onCompleted != null && context.getMissionRecord() != null) {
                onCompleted.accept(context.getMissionRecord().getId());
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
        if (context.getMissionRecord() != null && context.getMissionRecord().getId() != null) {
            missionRecordService.markFaulted(
                context.getMissionRecord().getId(),
                "Capability failed: " + failedCap.id());
        }
        saveCheckpoint("StageFailure:" + failedCap.id());
        if (onFaulted != null) onFaulted.accept("Capability failed: " + failedCap.id());
        shutdown();
    }

    // === 崩溃恢复 ===

    private void handleActorCrash(Exception e, InboundMessage msg) {
        log.error("Actor thread crashed, message={}", msg, e);
        if (context == null) return;
        if (context.getMissionRecord() != null && context.getMissionRecord().getId() != null) {
            try {
                missionRecordService.markFaulted(
                    context.getMissionRecord().getId(), e.getMessage());
            } catch (Exception markEx) {
                log.error("Failed to mark faulted", markEx);
            }
        }
        try {
            saveCheckpoint("Crash:" + (msg != null ? msg.getClass().getSimpleName() : "unknown"));
        } catch (Exception cpEx) {
            log.error("Failed to save checkpoint", cpEx);
        }
        inbox.offer(new EngineInternal.Faulted(e.getMessage()));
    }

    private void saveCheckpoint(String reason) {
        if (context == null || context.getMissionRecord() == null) return;
        ContextCheckpoint cp = ContextCheckpoint.builder()
            .missionId(context.getProductMissionId())
            .missionRecordId(context.getMissionRecord().getId())
            .stage(context.getCurrentStage())
            .subState(context.getCurrentSubState())
            .currentBoltIndex(context.getCurrentBoltIndex())
            .currentSideIndex(context.getCurrentSideIndex())
            .completedBolts((int) Arrays.stream(context.getBoltStates())
                .filter(s -> s == BoltState.JUDGED_OK || s == BoltState.JUDGED_NG).count())
            .dataStored(context.getCurrentOperationData() == null
                || context.getCurrentOperationData().getMissionRecordId() != null)
            .snapshotReason(reason)
            .timestamp(System.currentTimeMillis())
            .build();
        context.setCheckpoint(cp);
        // 持久化到 DB
        String json = JsonUtils.toJson(cp);
        missionRecordService.updateSnapshot(context.getMissionRecord().getId(), json);
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

    public void start(MissionContext ctx) {
        this.context = ctx;
        this.alive = true;
        actorThread = new Thread(this::actorLoop, "lifecycle-engine-" + ctx.getProductMissionId());
        actorThread.setUncaughtExceptionHandler((t, throwable) -> {
            log.error("Actor thread uncaught exception", throwable);
            // 线程即将死亡，直接执行崩溃逻辑并 shutdown，不投递到 inbox
            if (context != null && context.getMissionRecord() != null && context.getMissionRecord().getId() != null) {
                try { missionRecordService.markFaulted(context.getMissionRecord().getId(), throwable.getMessage()); }
                catch (Exception ex) { log.error("Failed to mark faulted", ex); }
            }
            try { saveCheckpoint("Uncaught:" + throwable.getClass().getSimpleName()); }
            catch (Exception ex) { log.error("Failed to save checkpoint", ex); }
            if (onFaulted != null) onFaulted.accept(throwable.getMessage());
            shutdown();
        });
        actorThread.start();
        inbox.offer(new InboundCommand.ActivateMission(
            ctx.getMissionData(), List.of(), ctx.getBoltConfigs(), List.of()));
    }

    public void postMessage(InboundMessage msg) {
        if (alive) inbox.offer(msg);
    }

    public void interrupt(String reason) {
        if (context != null && context.getCurrentStage() == Stage.FINALIZATION) return;
        postMessage(new InboundCommand.InterruptMission(reason));
    }

    public boolean isAlive() { return alive; }

    public MissionContext getContext() { return context; }

    /** 设置上下文但不启动引擎，由工厂在组装后调用 */
    void initContext(MissionContext ctx) { this.context = ctx; }

    private void shutdown() {
        alive = false;
        stopMonitorTicks();
        if (actorThread != null) actorThread.interrupt();
    }
}
