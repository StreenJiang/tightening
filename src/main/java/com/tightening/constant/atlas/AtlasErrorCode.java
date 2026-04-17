package com.tightening.constant.atlas;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum AtlasErrorCode {

    // TODO: need i18n here for String description
    NO_ERROR(0, "No Error"),
    INVALID_DATA(1, "Invalid data"),
    PARAMETER_SET_ID_NOT_PRESENT(2, "Parameter set ID not present"),
    PARAMETER_SET_CANNOT_BE_SET(3, "Parameter set can not be set."),
    PARAMETER_SET_NOT_RUNNING(4, "Parameter set not running"),
    VIN_UPLOAD_SUBSCRIPTION_ALREADY_EXISTS(6, "VIN upload subscription already exists"),
    VIN_UPLOAD_SUBSCRIPTION_DOES_NOT_EXIST(7, "VIN upload subscription does not exists"),
    VIN_INPUT_SOURCE_NOT_GRANTED(8, "VIN input source not granted"),
    LAST_TIGHTENING_RESULT_SUBSCRIPTION_ALREADY_EXISTS(9,
                                                       "Last tightening result subscription already exists"),
    LAST_TIGHTENING_RESULT_SUBSCRIPTION_DOES_NOT_EXIST(10,
                                                       "Last tightening result subscription does not exist"),
    ALARM_SUBSCRIPTION_ALREADY_EXISTS(11, "Alarm subscription already exists"),
    ALARM_SUBSCRIPTION_DOES_NOT_EXIST(12, "Alarm subscription does not exist"),
    PARAMETER_SET_SELECTION_SUBSCRIPTION_ALREADY_EXISTS(13,
                                                        "Parameter set selection subscription already exists"),
    PARAMETER_SET_SELECTION_SUBSCRIPTION_DOES_NOT_EXIST(14,
                                                        "Parameter set selection subscription does not exist"),
    TIGHTENING_ID_REQUESTED_NOT_FOUND(15, "Tightening ID requested not found"),
    CONNECTION_REJECTED_PROTOCOL_BUSY(16, "Connection rejected protocol busy"),
    JOB_ID_NOT_PRESENT(17, "Job ID not present"),
    JOB_INFO_SUBSCRIPTION_ALREADY_EXISTS(18, "Job info subscription already exists"),
    JOB_INFO_SUBSCRIPTION_DOES_NOT_EXIST(19, "Job info subscription does not exist"),
    JOB_CANNOT_BE_SET(20, "Job can not be set"),
    JOB_NOT_RUNNING(21, "Job not running"),
    NOT_POSSIBLE_TO_EXECUTE_DYNAMIC_JOB_REQUEST(22, "Not possible to execute dynamic Job request"),
    JOB_BATCH_DECREMENT_FAILED(23, "Job batch decrement failed"),
    NOT_POSSIBLE_TO_CREATE_PSET(24, "Not possible to create Pset"),
    PROGRAMMING_CONTROL_NOT_GRANTED(25, "Programming control not granted"),
    WRONG_TOOL_TYPE_TO_PSET_DOWNLOAD_CONNECTED(26, "Wrong tool type to Pset download connected"),
    TOOL_IS_INACCESSIBLE(27, "Tool is inaccessible"),
    JOB_ABORTION_IS_IN_PROGRESS(28, "Job abortion is in progress"),
    CONTROLLER_IS_NOT_A_SYNC_MASTER_STATION_CONTROLLER(30,
                                                       "Controller is not a sync Master/station controller"),
    MULTI_SPINDLE_STATUS_SUBSCRIPTION_ALREADY_EXISTS(31, "Multi-spindle status subscription already exists"),
    MULTI_SPINDLE_STATUS_SUBSCRIPTION_DOES_NOT_EXIST(32, "Multi-spindle status subscription does not exist"),
    MULTI_SPINDLE_RESULT_SUBSCRIPTION_ALREADY_EXISTS(33, "Multi-spindle result subscription already exists"),
    MULTI_SPINDLE_RESULT_SUBSCRIPTION_DOES_NOT_EXIST(34, "Multi-spindle result subscription does not exist"),
    OTHER_MASTER_CLIENT_ALREADY_CONNECTED(35, "Other master client already connected"),
    JOB_LINE_CONTROL_INFO_SUBSCRIPTION_ALREADY_EXISTS(40,
                                                      "Job line control info subscription already exists"),
    JOB_LINE_CONTROL_INFO_SUBSCRIPTION_DOES_NOT_EXIST(41,
                                                      "Job line control info subscription does not exist"),
    IDENTIFIER_INPUT_SOURCE_NOT_GRANTED(42, "Identifier input source not granted"),
    MULTIPLE_IDENTIFIERS_WORK_ORDER_SUBSCRIPTION_ALREADY_EXISTS(43,
                                                                "Multiple identifiers work order subscription already exists"),
    MULTIPLE_IDENTIFIERS_WORK_ORDER_SUBSCRIPTION_DOES_NOT_EXIST(44,
                                                                "Multiple identifiers work order subscription does not exist"),
    STATUS_EXTERNAL_MONITORED_INPUTS_SUBSCRIPTION_ALREADY_EXISTS(50,
                                                                 "Status external monitored inputs subscription already exists"),
    STATUS_EXTERNAL_MONITORED_INPUTS_SUBSCRIPTION_DOES_NOT_EXIST(51,
                                                                 "Status external monitored inputs subscription does not exist"),
    IO_DEVICE_NOT_CONNECTED(52, "IO device not connected"),
    FAULTY_IO_DEVICE_ID(53, "Faulty IO device ID"),
    TOOL_TAG_ID_UNKNOWN(54, "Tool Tag ID unknown"),
    TOOL_TAG_ID_SUBSCRIPTION_ALREADY_EXISTS(55, "Tool Tag ID subscription already exists"),
    TOOL_TAG_ID_SUBSCRIPTION_DOES_NOT_EXIST(56, "Tool Tag ID subscription does not exist"),
    TOOL_MOTOR_TUNING_FAILED(57, "Tool Motor tuning failed"),
    NO_ALARM_PRESENT(58, "No alarm present"),
    TOOL_CURRENTLY_IN_USE(59, "Tool currently in use"),
    NO_HISTOGRAM_AVAILABLE(60, "No histogram available"),
    PAIRING_FAILED(61, "Pairing failed"),
    PAIRING_DENIED(62, "Pairing denied"),
    PAIRING_OR_PAIRING_ABORTION_ATTEMPT_ON_WRONG_TOOLTYPE(63,
                                                          "Pairing or Pairing abortion attempt on wrong tooltype"),
    PAIRING_ABORTION_DENIED(64, "Pairing abortion denied"),
    PAIRING_ABORTION_FAILED(65, "Pairing abortion failed"),
    PAIRING_DISCONNECTION_FAILED(66, "Pairing disconnection failed"),
    PAIRING_IN_PROGRESS_OR_ALREADY_DONE(67, "Pairing in progress or already done"),
    PAIRING_DENIED_NO_PROGRAM_CONTROL(68, "Pairing denied. No Program Control"),
    UNSUPPORTED_EXTRA_DATA_REVISION(69, "Unsupported extra data revision"),
    CALIBRATION_FAILED(70, "Calibration failed"),
    SUBSCRIPTION_ALREADY_EXISTS(71, "Subscription already exists"),
    SUBSCRIPTION_DOES_NOT_EXIST(72, "Subscription does not exists"),
    SUBSCRIBED_MID_UNSUPPORTED(73,
                               "Subscribed MID unsupported, -answer if trying to subscribe on a non-existing MID"),
    SUBSCRIBED_MID_REVISION_UNSUPPORTED(74,
                                        "Subscribed MID Revision unsupported, -answer if trying to subscribe on unsupported MID Revision."),
    REQUESTED_MID_UNSUPPORTED(75,
                              "Requested MID unsupported -answer if trying to request on a non-existing MID"),
    REQUESTED_MID_REVISION_UNSUPPORTED(76,
                                       "Requested MID Revision unsupported -response when trying to request unsupported MID Revision"),
    REQUESTED_ON_SPECIFIC_DATA_NOT_SUPPORTED(77,
                                             "Requested on specific data not supported -response when trying to request data that is not supported"),
    SUBSCRIPTION_ON_SPECIFIC_DATA_NOT_SUPPORTED(78,
                                                "Subscription on specific data not supported -answer if trying to subscribe for unsupported data"),
    COMMAND_FAILED(79, "Command failed"),
    AUDI_EMERGENCY_STATUS_SUBSCRIPTION_EXISTS(80, "Audi emergency status subscription exists"),
    AUDI_EMERGENCY_STATUS_SUBSCRIPTION_DOES_NOT_EXIST(81,
                                                      "Audi emergency status subscription does not exist"),
    AUTOMATIC_MANUAL_MODE_SUBSCRIBE_ALREADY_EXISTS(82, "Automatic/Manual mode subscribe already exist"),
    AUTOMATIC_MANUAL_MODE_SUBSCRIBE_DOES_NOT_EXIST(83, "Automatic/Manual mode subscribe does not exist"),
    THE_RELAY_FUNCTION_SUBSCRIPTION_ALREADY_EXISTS(84, "The relay function subscription already exists"),
    THE_RELAY_FUNCTION_SUBSCRIPTION_DOES_NOT_EXIST(85, "The relay function subscription does not exist"),
    THE_SELECTOR_SOCKET_INFO_SUBSCRIPTION_ALREADY_EXISTS(86,
                                                         "The selector socket info subscription already exist"),
    THE_SELECTOR_SOCKET_INFO_SUBSCRIPTION_DOES_NOT_EXIST(87,
                                                         "The selector socket info subscription does not exist"),
    THE_DIGIN_INFO_SUBSCRIPTION_ALREADY_EXISTS(88, "The digin info subscription already exist"),
    THE_DIGIN_INFO_SUBSCRIPTION_DOES_NOT_EXIST(89, "The digin info subscription does not exist"),
    LOCK_AT_BATCH_DONE_SUBSCRIPTION_ALREADY_EXISTS(90, "Lock at batch done subscription already exist"),
    LOCK_AT_BATCH_DONE_SUBSCRIPTION_DOES_NOT_EXIST(91, "Lock at batch done subscription does not exist"),
    OPEN_PROTOCOL_COMMANDS_DISABLED(92, "Open protocol commands disabled"),
    OPEN_PROTOCOL_COMMANDS_DISABLED_SUBSCRIPTION_ALREADY_EXISTS(93,
                                                                "Open protocol commands disabled subscription already exists"),
    OPEN_PROTOCOL_COMMANDS_DISABLED_SUBSCRIPTION_DOES_NOT_EXIST(94,
                                                                "Open protocol commands disabled subscription does not exist"),
    REJECT_REQUEST_POWER_MACS_IS_IN_MANUAL_MODE(95, "Reject request, Power MACS is in manual mode"),
    REJECT_CONNECTION_CLIENT_ALREADY_CONNECTED(96, "Reject connection, Client already connected"),
    MID_REVISION_UNSUPPORTED(97, "MID revision unsupported"),
    CONTROLLER_INTERNAL_REQUEST_TIMEOUT(98, "Controller internal request timeout"),
    UNKNOWN_MID(99, "Unknown MID");

    @Getter
    private final int code;
    @Getter
    private final String description;

    // Lookup maps for efficient reverse lookups
    private static final Map<Integer, AtlasErrorCode> BY_CODE = new HashMap<>();

    static {
        for (AtlasErrorCode errorCode : values()) {
            BY_CODE.put(errorCode.code, errorCode);
        }
    }

    AtlasErrorCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Finds an ErrorCode by its numeric code.
     *
     * @param code the error code number
     * @return Optional containing the ErrorCode if found, empty otherwise
     */
    public static Optional<AtlasErrorCode> fromCode(int code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    /**
     * Finds an ErrorCode by its numeric code, throwing an exception if not found.
     *
     * @param code the error code number
     * @return the ErrorCode
     * @throws IllegalArgumentException if code not found
     */
    public static AtlasErrorCode fromCodeOrThrow(int code) {
        return fromCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown error code: " + code));
    }

    /**
     * Returns a formatted string representation.
     *
     * @return formatted error information
     */
    @Override
    public String toString() {
        return String.format("[%02d] %s", code, description);
    }
}
