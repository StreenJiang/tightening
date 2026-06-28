package com.tightening.config;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        try {
            var factory = new YamlPropertiesFactoryBean();
            factory.setResources(resource.getResource());
            var properties = factory.getObject();
            if (properties == null) {
                properties = new Properties();
            }
            String sourceName = name != null ? name : resource.getResource().getFilename();
            return new PropertiesPropertySource(Objects.requireNonNull(sourceName), properties);
        } catch (IllegalStateException e) {
            if (resource.getResource().getFile() != null
                    && !resource.getResource().getFile().exists()) {
                throw new FileNotFoundException("YAML file not found: "
                        + resource.getResource().getDescription());
            }
            throw e;
        }
    }
}
