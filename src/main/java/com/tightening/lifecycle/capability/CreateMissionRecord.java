package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.MissionRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CreateMissionRecord implements Capability {

    private final MissionRecordService missionRecordService;

    @Override public String id() { return "CreateMissionRecord"; }
    @Override public Stage stage() { return Stage.ACTIVATION; }
    @Override public SubState subState() { return SubState.ACTIVATING; }
    @Override public int priority() { return 0; }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        var record = missionRecordService.createRecord(
            ctx.getProductMissionId(), null, 0);
        ctx.setMissionRecord(record);
        log.info("MissionRecord created: id={}", record.getId());
        return CapabilityResult.Pass;
    }
}
