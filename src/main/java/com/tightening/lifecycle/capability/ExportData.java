package com.tightening.lifecycle.capability;

import com.tightening.config.LocalSettings;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.MissionRecord;
import com.tightening.lifecycle.MissionContext;
import com.tightening.service.ExportTaskService;
import com.tightening.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ExportData implements Capability {

    private final ExportTaskService exportTaskService;
    private final LocalSettings settings;

    @Override public String id() { return "ExportData"; }
    @Override public Stage stage() { return Stage.FINALIZATION; }
    @Override public SubState subState() { return SubState.EXPORTING; }
    @Override public int priority() { return 0; }

    @Override
    public boolean precondition(MissionContext ctx) {
        return ctx.getMissionRecord() != null && ctx.getMissionRecord().getId() != null;
    }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        MissionRecord record = ctx.getMissionRecord();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("missionId", ctx.getProductMissionId());
        payload.put("missionRecordId", record.getId());
        payload.put("missionResult", record.getMissionResult());
        payload.put("boltCount", ctx.totalBolts());
        payload.put("tighteningDataCount", ctx.getTighteningDataList().size());

        String json = JsonUtils.toJson(payload);
        for (String type : settings.exportTypes()) {
            exportTaskService.createTask(type, record.getId(), json);
            log.info("ExportData: created {} task for missionRecordId={}", type, record.getId());
        }
        return CapabilityResult.Pass;
    }
}
