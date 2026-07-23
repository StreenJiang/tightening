package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Setter
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("task_record")
public class TaskRecord extends BaseEntity {
    private Long productTaskId;
    private String productCode;
    private Integer isRework;
    private Integer taskResult;
    private String partsCode;
    private String contextSnapshot;
    private String faultMessage;
}
