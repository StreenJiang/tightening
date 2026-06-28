package com.tightening.export;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 标准 Excel 导出 Stub 实现。
 */
@Slf4j
@Component
public class StandardExcelExporter implements Exporter {

    private static final String TYPE = "standard_excel";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ExportResult execute(ExportPayload payload) {
        log.info("[Stub] StandardExcelExporter execute payload: missionRecordId={}, data={}",
                payload.missionRecordId(), payload.data());
        return ExportResult.ok("stub");
    }
}
