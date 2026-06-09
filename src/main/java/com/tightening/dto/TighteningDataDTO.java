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
public class TighteningDataDTO extends BaseDTO {
    private long missionRecordId;
    private String workstationName;
    private String toolName;
    private String toolTypeName;
    private String productSideName;
    private int boltSerialNum;
    private String armLocation;
    private int parameterSet;
    private String parameterSetName;

    private long tighteningId;
    private int tighteningStatus;
    private int resultType;
    private int torqueStatus;
    private int angleStatus;
    private int rundownAngleStatus;
    private int torqueValuesUnit;

    private double torqueMinLimit;
    private double torqueMaxLimit;
    private double torqueFinalTarget;
    private double torque;
    private double angleMinLimit;
    private double angleMaxLimit;
    private double angleFinalTarget;
    private double angle;
    private double rundownAngleMinLimit;
    private double rundownAngleMaxLimit;
    private double rundownAngle;

    private String timestamp;

    private int cellId;
    private int channelId;
    private String controllerName;
    private String vin;
    private int jobId;
    private int batchSize;
    private int batchCounter;
    private int batchStatus;

    private int revision;
    private String extraData;
}
