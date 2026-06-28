package com.tightening.export;

import java.util.Map;

/**
 * 导出任务 payload，描述一次导出请求的内容。
 *
 * @param missionRecordId 关联的任务记录 ID
 * @param type            导出类型（如 excel, pdf, csv）
 * @param data            导出自定义参数
 */
public record ExportPayload(Long missionRecordId, String type, Map<String, Object> data) {
}
