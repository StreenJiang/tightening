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
@TableName("task_prerequisite")
public class TaskPrerequisite extends BaseEntity {
    private Long taskId;
    private Long prerequisiteTaskId;
    private Integer prerequisiteType;
    private Long barcodeRuleId;
}
