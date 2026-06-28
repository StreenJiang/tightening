package com.tightening.export;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exporter 注册中心。
 * <p>
 * 自动装配所有 Exporter Bean，同时支持手动注册。
 * 线程安全。
 */
@Component
public class ExporterRegistry {

    private final Map<String, Exporter> exporters = new ConcurrentHashMap<>();

    public ExporterRegistry(List<Exporter> exporterList) {
        exporterList.forEach(e -> exporters.put(e.type(), e));
    }

    /**
     * 根据导出类型获取 Exporter。
     *
     * @param type 导出类型
     * @return Exporter
     * @throws IllegalArgumentException 当 type 未注册时抛出
     */
    public Exporter get(String type) {
        Exporter exporter = exporters.get(type);
        if (exporter == null) {
            throw new IllegalArgumentException("Unknown exporter type: " + type);
        }
        return exporter;
    }

    /**
     * 手动注册 Exporter。
     *
     * @param exporter 导出器实例
     */
    public void register(Exporter exporter) {
        exporters.put(exporter.type(), exporter);
    }
}
