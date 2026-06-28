package com.tightening.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("内置 Exporter（Stub）")
class ExporterStubTest {

    @Test
    @DisplayName("StandardExcelExporter type=standard_excel")
    void standardExcelExporterShouldWork() {
        Exporter e = new StandardExcelExporter();
        assertThat(e.type()).isEqualTo("standard_excel");
        assertThat(e.execute(new ExportPayload(1L, "standard_excel", Map.of())).success()).isTrue();
    }

    @Test
    @DisplayName("OuterDatabaseStorer type=outer_db_store")
    void outerDatabaseStorerShouldWork() {
        Exporter e = new OuterDatabaseStorer();
        assertThat(e.type()).isEqualTo("outer_db_store");
        assertThat(e.execute(new ExportPayload(1L, "outer_db_store", Map.of())).success()).isTrue();
    }

    @Test
    @DisplayName("PlcResultSender type=send_plc_result")
    void plcResultSenderShouldWork() {
        Exporter e = new PlcResultSender();
        assertThat(e.type()).isEqualTo("send_plc_result");
        assertThat(e.execute(new ExportPayload(1L, "send_plc_result", Map.of())).success()).isTrue();
    }
}
