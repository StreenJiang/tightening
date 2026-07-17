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
@TableName("mission_record")
public class MissionRecord extends BaseEntity {
    private Long productMissionId;
    private String productCode;
    private Integer isRework;
    private Integer missionResult;
    private String partsCode;
    private String contextSnapshot;
    private String faultMessage;
}
