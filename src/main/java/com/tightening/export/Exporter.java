package com.tightening.export;

/**
 * 导出器接口，每种导出类型对应一个实现。
 */
public interface Exporter {

    /**
     * 导出类型标识，如 excel、pdf、csv。
     */
    String type();

    /**
     * 执行导出操作。
     *
     * @param payload 导出任务参数
     * @return 导出结果
     */
    ExportResult execute(ExportPayload payload);
}
