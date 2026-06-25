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
public class ProductBoltDTO extends BaseDTO {
    private Long productSideId;
    private Integer boltSerialNum;
    private String boltName;
    private Long parameterSetId;
    private Double torqueMin;
    private Double torqueMax;
    private Double angleMin;
    private Double angleMax;
    private String armLocation;
    private Double locationXPercent;
    private Double locationYPercent;
    private Integer enabled;
}
