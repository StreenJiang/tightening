package com.tightening.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalSettings 配置")
class LocalSettingsTest {

    @Test
    @DisplayName("compact constructor 为 exportTypes 提供默认值")
    void shouldDefaultExportTypes() {
        LocalSettings settings = new LocalSettings(false, null);
        assertThat(settings.exportTypes()).containsExactly("standard_excel", "txt");
    }

    @Test
    @DisplayName("selfLoopEnabled 默认 false")
    void shouldDefaultSelfLoopToFalse() {
        LocalSettings settings = new LocalSettings(false, null);
        assertThat(settings.selfLoopEnabled()).isFalse();
    }
}
