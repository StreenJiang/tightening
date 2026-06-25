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
@TableName("product_bolt")
public class ProductBolt extends BaseEntity {
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
