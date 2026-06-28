package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Setter
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("export_task")
public class ExportTask extends BaseEntity {
    private String type;
    private Long missionRecordId;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private Integer maxRetries;
    private String errorMessage;
    private LocalDateTime completedAt;
}
