package com.tightening.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
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
