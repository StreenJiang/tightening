package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.MissionContext;
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
    public boolean precondition(MissionContext ctx) {
        return ctx.getCurrentOperationData() != null;
    }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        TighteningData data = ctx.getCurrentOperationData();
        if (ctx.getMissionRecord() != null) {
            data.setMissionRecordId(ctx.getMissionRecord().getId());
        }
        tighteningDataService.save(data);
        ctx.getTighteningDataList().add(data);
        ctx.setCurrentOperationData(null);
        log.info("StoreData: id={}, missionRecordId={}", data.getId(), data.getMissionRecordId());
        return CapabilityResult.Pass;
    }
}
