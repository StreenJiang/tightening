package com.tightening.dto;

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
public class ProductMissionDTO extends BaseDTO {
    private String name;
    private Integer maxNgCount;
    private Integer passwordRequiredAfterNg;
    private Integer enabled;
    private Integer multiDeviceIndependent;
    private Integer skipScrew;
    private Integer isInspection;
    private Integer inspectionScope;
}
