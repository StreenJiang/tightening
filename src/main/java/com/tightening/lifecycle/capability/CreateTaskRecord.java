package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.TaskRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CreateTaskRecord implements Capability {

    private final TaskRecordService taskRecordService;

    @Override public String id() { return "CreateTaskRecord"; }
    @Override public Stage stage() { return Stage.ACTIVATION; }
    @Override public SubState subState() { return SubState.ACTIVATING; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        var record = taskRecordService.createRecord(
            ctx.getProductTaskId(), ctx.getProductCode(), ctx.getPartsCode(), 0);
        ctx.setTaskRecord(record);
        log.info("TaskRecord created: id={}", record.getId());
        return CapabilityResult.Pass;
    }
}
