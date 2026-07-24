package com.tightening.export;

import org.springframework.context.ApplicationEvent;

/**
 * 导出任务创建事件。ExportTaskService 写入 outbox 后发布，
 * ExportWorker 异步监听并触发处理。
 */
public class ExportTaskCreatedEvent extends ApplicationEvent {

    public ExportTaskCreatedEvent(Object source) {
        super(source);
    }
}
