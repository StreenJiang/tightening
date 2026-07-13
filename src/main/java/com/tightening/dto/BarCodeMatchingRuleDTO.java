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
public class BarCodeMatchingRuleDTO extends BaseDTO {
    private String name;
    private Long productMissionId;
    private Integer ruleType;
    private String partNumber;
    private Integer expectedLength;
    private String segments;
}
