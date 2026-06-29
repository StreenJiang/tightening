package com.tightening.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@PropertySource(
        value = "file:${user.home}/tightening_system/settings.yml",
        factory = YamlPropertySourceFactory.class,
        ignoreResourceNotFound = true)
@ConfigurationProperties(prefix = "tightening")
public record LocalSettings(boolean selfLoopEnabled, List<String> exportTypes) {

    public LocalSettings {
        if (exportTypes == null || exportTypes.isEmpty()) {
            exportTypes = List.of("standard_excel");
        }
    }
}
