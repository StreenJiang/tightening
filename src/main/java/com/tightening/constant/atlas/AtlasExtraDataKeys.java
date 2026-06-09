package com.tightening.constant.atlas;

public final class AtlasExtraDataKeys {
    private AtlasExtraDataKeys() {}

    // === Rev 2+ ===
    public static final String STRATEGY = "strategy";
    public static final String STRATEGY_OPTIONS = "strategyOptions";
    public static final String CURRENT_MONITORING_STATUS = "currentMonitoringStatus";
    public static final String SELF_TAP_STATUS = "selfTapStatus";
    public static final String PV_MONITOR_STATUS = "pvMonitorStatus";
    public static final String PV_COMPENSATE_STATUS = "pvCompensateStatus";
    public static final String TIGHTENING_ERROR_STATUS = "tighteningErrorStatus";
    public static final String CURRENT_MONITORING_MIN = "currentMonitoringMin";
    public static final String CURRENT_MONITORING_MAX = "currentMonitoringMax";
    public static final String CURRENT_MONITORING_VALUE = "currentMonitoringValue";
    public static final String SELF_TAP_MIN = "selfTapMin";
    public static final String SELF_TAP_MAX = "selfTapMax";
    public static final String SELF_TAP_TORQUE = "selfTapTorque";
    public static final String PV_MONITOR_MIN = "pvMonitorMin";
    public static final String PV_MONITOR_MAX = "pvMonitorMax";
    public static final String PREVAIL_TORQUE = "prevailTorque";
    public static final String JOB_SEQUENCE_NUMBER = "jobSequenceNumber";
    public static final String SYNC_TIGHTENING_ID = "syncTighteningId";
    public static final String TOOL_SERIAL_NUMBER = "toolSerialNumber";

    // === Rev 4+ ===
    public static final String IDENTIFIER_RESULT_PART_2 = "identifierResultPart2";
    public static final String IDENTIFIER_RESULT_PART_3 = "identifierResultPart3";
    public static final String IDENTIFIER_RESULT_PART_4 = "identifierResultPart4";

    // === Rev 5+ ===
    public static final String CUSTOMER_TIGHTENING_ERROR_CODE = "customerTighteningErrorCode";

    // === Rev 6+ ===
    public static final String PV_COMPENSATE_VALUE = "pvCompensateValue";
    public static final String TIGHTENING_ERROR_STATUS_2 = "tighteningErrorStatus2";

    // === Rev 7+ ===
    public static final String COMPENSATED_ANGLE = "compensatedAngle";
    public static final String FINAL_ANGLE_DECIMAL = "finalAngleDecimal";

    // === Rev 998 ===
    public static final String TOTAL_STAGES = "totalStages";
    public static final String COMPLETED_STAGES = "completedStages";
    public static final String STAGE_RESULTS = "stageResults";
}
