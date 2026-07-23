package com.tightening.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tightening.constant.InspectionScope;

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
@TableName("product_task")
public class ProductTask extends BaseEntity {
    private String name;
    private Integer maxNgCount;
    private Integer passwordRequiredNgCount;
    private Integer enabled;
    private Integer multiDeviceIndependent;
    private Integer skipScrew;
    private Integer isInspection;
    private InspectionScope inspectionScope = InspectionScope.NONE;
}
