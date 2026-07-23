package com.tightening.lifecycle.capability;

import com.tightening.config.LocalSettings;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.TaskRecord;
import com.tightening.lifecycle.TaskContext;
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
    public boolean precondition(TaskContext ctx) {
        return ctx.getTaskRecord() != null && ctx.getTaskRecord().getId() != null;
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        TaskRecord record = ctx.getTaskRecord();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", ctx.getProductTaskId());
        payload.put("taskRecordId", record.getId());
        payload.put("taskResult", record.getTaskResult());
        payload.put("productCode", record.getProductCode() != null ? record.getProductCode() : "");
        payload.put("isRework", record.getIsRework() != null ? record.getIsRework() : 0);
        payload.put("timestamp", java.time.LocalDateTime.now().toString());
        payload.put("boltCount", ctx.totalBolts());
        payload.put("tighteningDataCount", ctx.getTighteningDataList().size());

        String json = JsonUtils.toJson(payload);
        for (String type : settings.exportTypes()) {
            exportTaskService.createTask(type, record.getId(), json);
            log.info("ExportData: created {} task for taskRecordId={}", type, record.getId());
        }
        return CapabilityResult.Pass;
    }
}
