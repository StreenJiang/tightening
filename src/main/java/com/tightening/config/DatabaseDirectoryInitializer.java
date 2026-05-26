package com.tightening.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

public class DatabaseDirectoryInitializer implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || !url.startsWith("jdbc:sqlite:")) {
            return;
        }
        String filePath = url.substring("jdbc:sqlite:".length());
        Path parentDir = Path.of(filePath).getParent();
        if (parentDir != null) {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create database directory: " + parentDir, e);
            }
        }
    }
}
