package com.tightening.export;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 外部数据库存储 Stub 实现。
 */
@Slf4j
@Component
public class OuterDatabaseStorer implements Exporter {

    private static final String TYPE = "outer_db_store";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ExportResult execute(ExportPayload payload) {
        log.info("[Stub] OuterDatabaseStorer execute payload: taskRecordId={}, data={}",
                payload.taskRecordId(), payload.data());
        return ExportResult.ok("stub");
    }
}
