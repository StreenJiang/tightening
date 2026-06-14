package com.tightening.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.EnvironmentPostProcessor;

class DatabaseDirectoryInitializerTest {

    @Test
    void shouldImplementEnvironmentPostProcessor() {
        DatabaseDirectoryInitializer initializer = new DatabaseDirectoryInitializer();
        assertThat(initializer).isInstanceOf(EnvironmentPostProcessor.class);
    }
}
