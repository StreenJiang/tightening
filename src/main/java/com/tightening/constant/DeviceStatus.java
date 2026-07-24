package com.tightening.constant;

public enum DeviceStatus {
    CONNECTING,
    CONNECTED,
    DEGRADED,       // TCP 通但部分子设备异常
    DISCONNECTED,
    NONE
}
