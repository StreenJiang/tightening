package com.tightening.dto;

import com.tightening.constant.PrerequisiteType;
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
public class PrerequisiteSaveItem extends BaseDTO {
    private Long prerequisiteMissionId;
    private PrerequisiteType prerequisiteType;
}
