package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("arm_model_config")
public class ArmModelConfig extends BaseEntity {
    private String name;
    private Integer xSlaveAddr;
    private Integer xRegister;
    private Integer xCount;
    private Integer ySlaveAddr;
    private Integer yRegister;
    private Integer yCount;
    private Integer zSlaveAddr;
    private Integer zRegister;
    private Integer zCount;
    private String parseStrategy;
}
