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
@TableName("bar_code_matching_rule")
public class BarCodeMatchingRule extends BaseEntity {
    private String name;
    private Long productMissionId;
    private Integer ruleType;
    private String partNumber;
    private Integer expectedLength;
    private Integer keyStartPosition;
    private Integer keyEndPosition;
    private String keyChar;
}
