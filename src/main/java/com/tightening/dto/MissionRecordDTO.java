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
public class MissionRecordDTO extends BaseDTO {
    private Long productMissionId;
    private String productCode;
    private Integer isRework;
    private Integer missionResult;
}
