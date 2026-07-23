package com.tightening.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExportPayload 和 ExportResult 记录")
class ExportResultTest {

    // ========== ExportPayload ==========

    @Test
    @DisplayName("通过构造器创建 ExportPayload，字段值正确")
    void shouldCreateExportPayloadWithAllFields() {
        Map<String, Object> data = new HashMap<>();
        data.put("format", "xlsx");
        data.put("template", "standard");

        ExportPayload payload = new ExportPayload(42L, "excel", data);

        assertThat(payload.taskRecordId()).isEqualTo(42L);
        assertThat(payload.type()).isEqualTo("excel");
        assertThat(payload.data()).containsEntry("format", "xlsx");
    }

    @Test
    @DisplayName("ExportPayload 支持空 data")
    void shouldSupportNullData() {
        ExportPayload payload = new ExportPayload(1L, "pdf", null);
        assertThat(payload.data()).isNull();
    }

    @Test
    @DisplayName("ExportPayload 支持空 taskRecordId")
    void shouldSupportNullTaskRecordId() {
        ExportPayload payload = new ExportPayload(null, "csv", Map.of());
        assertThat(payload.taskRecordId()).isNull();
    }

    @Test
    @DisplayName("ExportPayload equals 和 hashCode 基于所有字段")
    void shouldImplementEqualsAndHashCode() {
        Map<String, Object> data = Map.of("key", "value");
        ExportPayload p1 = new ExportPayload(1L, "excel", data);
        ExportPayload p2 = new ExportPayload(1L, "excel", data);
        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    @DisplayName("ExportPayload toString 包含字段")
    void shouldIncludeFieldsInToString() {
        ExportPayload payload = new ExportPayload(42L, "excel", Map.of());
        String str = payload.toString();
        assertThat(str).contains("42");
        assertThat(str).contains("excel");
    }

    // ========== ExportResult ==========

    @Test
    @DisplayName("ok() 工厂方法返回成功结果")
    void okFactoryShouldReturnSuccess() {
        ExportResult result = ExportResult.ok();
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("OK");
    }

    @Test
    @DisplayName("ok(message) 工厂方法返回成功结果并携带自定义消息")
    void okWithMessageShouldReturnSuccess() {
        ExportResult result = ExportResult.ok("导出完成");
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("导出完成");
    }

    @Test
    @DisplayName("fail() 工厂方法返回失败结果")
    void failFactoryShouldReturnFailure() {
        ExportResult result = ExportResult.fail("导出失败");
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("导出失败");
    }

    @Test
    @DisplayName("ExportResult equals 和 hashCode 基于所有字段")
    void exportResultShouldImplementEqualsAndHashCode() {
        ExportResult r1 = ExportResult.ok("msg");
        ExportResult r2 = ExportResult.ok("msg");
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("不同结果应不等")
    void differentResultsShouldNotBeEqual() {
        ExportResult ok = ExportResult.ok("done");
        ExportResult fail = ExportResult.fail("error");
        assertThat(ok).isNotEqualTo(fail);
    }
}
