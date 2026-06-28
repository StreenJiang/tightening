package com.tightening.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExporterRegistry")
class ExporterRegistryTest {

    @Test
    @DisplayName("get 通过 type 返回注册的 Exporter")
    void shouldGetRegisteredExporter() {
        ExporterRegistry registry = new ExporterRegistry(List.of(
                new FakeExporter("type_a"),
                new FakeExporter("type_b")));
        assertThat(registry.get("type_a")).isNotNull();
        assertThat(registry.get("type_a").type()).isEqualTo("type_a");
    }

    @Test
    @DisplayName("未注册的 type 抛异常")
    void shouldThrowForUnknownType() {
        assertThatThrownBy(() -> new ExporterRegistry(List.of()).get("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    @DisplayName("register 手动注册")
    void shouldRegisterManually() {
        ExporterRegistry registry = new ExporterRegistry(List.of());
        registry.register(new FakeExporter("manual"));
        assertThat(registry.get("manual")).isNotNull();
    }

    private record FakeExporter(String type) implements Exporter {
        @Override
        public ExportResult execute(ExportPayload payload) { return ExportResult.ok("done"); }
    }
}
