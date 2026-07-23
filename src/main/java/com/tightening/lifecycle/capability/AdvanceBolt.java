package com.tightening.lifecycle.capability;

import com.tightening.constant.BoltState;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.judgment.JudgmentResult;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.TaskRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
@RequiredArgsConstructor
public class AdvanceBolt implements Capability {

    private final TaskRecordService taskRecordService;

    @Override public String id() { return "AdvanceBolt"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.ADVANCING; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        JudgmentResult jr = ctx.getJudgeResult();
        if (jr != null) {
            BoltState state = jr.isOk() ? BoltState.JUDGED_OK : BoltState.JUDGED_NG;
            int idx = ctx.getCurrentBoltIndex();
            if (idx >= 0 && idx < ctx.getBoltStates().length) {
                ctx.getBoltStates()[idx] = state;
            }
            log.info("Bolt {} result: {}", idx + 1, state);

            boolean allOk = Arrays.stream(ctx.getBoltStates()).allMatch(s -> s == BoltState.JUDGED_OK);
            if (allOk && ctx.getTaskRecord() != null) {
                taskRecordService.markAsOk(ctx.getTaskRecord().getId());
                log.info("TaskRecord {} marked OK", ctx.getTaskRecord().getId());
            }
        }

        if (ctx.hasMoreBolts()) {
            ctx.setCurrentBoltIndex(ctx.getCurrentBoltIndex() + 1);
            ctx.setBoltUnlockOverride(false);
            ctx.setCurrentOperationData(null);
            ctx.setJudgeResult(null);
            log.info("Advancing to bolt {}/{}", ctx.getCurrentBoltIndex() + 1, ctx.totalBolts());
            return CapabilityResult.Pass;
        }

        log.info("All bolts completed for task {}", ctx.getProductTaskId());
        ctx.setCurrentStage(Stage.FINALIZATION);
        ctx.setCurrentSubState(SubState.CLEANING_TASKS);
        return CapabilityResult.Pass;
    }
}
