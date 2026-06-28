package com.tightening.export;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PLC 结果发送 Stub 实现。
 */
@Slf4j
@Component
public class PlcResultSender implements Exporter {

    private static final String TYPE = "send_plc_result";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ExportResult execute(ExportPayload payload) {
        log.info("[Stub] PlcResultSender execute payload: missionRecordId={}, data={}",
                payload.missionRecordId(), payload.data());
        return ExportResult.ok("stub");
    }
}
