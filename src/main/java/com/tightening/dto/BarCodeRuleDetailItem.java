package com.tightening.dto;

import com.tightening.constant.BarCodeRuleType;
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
public class BarCodeRuleDetailItem extends BaseDTO {
    private String name;
    private BarCodeRuleType ruleType;
    private String partNumber;
    private Integer expectedLength;
    private String segments;
    private Integer seq;
    private String clientRef;
}
