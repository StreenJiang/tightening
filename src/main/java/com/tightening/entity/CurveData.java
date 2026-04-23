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
@TableName("curve_data")
public class CurveData extends BaseEntity {
    private long missionRecordId;
    private String workstationName;
    private String productSideName;
    private int boltSerialNum;
    private int parameterSet;

    private int tighteningId;
    private String timestamp;
    private int dataType;
    private String dataSamples;
}
