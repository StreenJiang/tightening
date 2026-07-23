package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.TaskContext;
import com.tightening.service.TighteningDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StoreData implements Capability {

    private final TighteningDataService tighteningDataService;

    @Override public String id() { return "StoreData"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.STORING; }
    @Override public int priority() { return 0; }

    @Override
    public boolean precondition(TaskContext ctx) {
        return ctx.getCurrentOperationData() != null;
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        TighteningData data = ctx.getCurrentOperationData();
        if (ctx.getTaskRecord() != null) {
            data.setTaskRecordId(ctx.getTaskRecord().getId());
        }
        tighteningDataService.save(data);
        ctx.getTighteningDataList().add(data);
        ctx.setCurrentOperationData(null);
        log.info("StoreData: id={}, taskRecordId={}", data.getId(), data.getTaskRecordId());
        return CapabilityResult.Pass;
    }
}
