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
@TableName("bolt_parts_barcode")
public class BoltPartsBarcode extends BaseEntity {
    private Long productBoltId;
    private Long barCodeMatchingRuleId;
}
