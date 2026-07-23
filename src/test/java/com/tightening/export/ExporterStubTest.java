package com.tightening.export;

import com.tightening.service.TighteningDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("内置 Exporter（Stub）")
@ExtendWith(MockitoExtension.class)
class ExporterStubTest {

    @Mock
    private TighteningDataService tighteningDataService;

    @Test
    @DisplayName("StandardExcelExporter type=standard_excel")
    void standardExcelExporterShouldWork() {
        when(tighteningDataService.listByTaskRecordId(1L)).thenReturn(List.of());

        Exporter e = new StandardExcelExporter(tighteningDataService);
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
