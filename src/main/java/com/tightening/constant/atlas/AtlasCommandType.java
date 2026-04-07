package com.tightening.constant.atlas;

import lombok.Getter;

/**
 * 指令类型枚举
 * 对应协议中的命令字及指令名称
 */
@Getter
public enum AtlasCommandType {

    // TODO: need i18n here for String description
    CONNECT(1, "连接设备"),
    CONNECT_ACK(2, "连接设备_成功反馈"),
    DISCONNECT(3, "结束连接"),
    NEGATIVE_ACK(4, "失败反馈"),
    POSITIVE_ACK(5, "成功反馈"),
    SUBSCRIBE_OTHERS(8, "订阅指定数据"),
    PARAMETER_SET(18, "程序号"),
    DISABLE(42, "锁止信号"),
    ENABLE(43, "使能信号"),
    SUBSCRIBE_DATA(60, "订阅拧紧数据"),
    BARCODE(150, "发送条码"),
    HEARTBEAT(9999, "心跳请求"),

    TIGHTEN_DATA(61, "拧紧数据"),
    CURVE_DATA(900, "拧紧曲线数据"),
    ;

    private final int mid;       // MID（4位ASCII整数）
    private final String name;   // 指令名称

    AtlasCommandType(int mid, String name) {
        this.mid = mid;
        this.name = name;
    }

    /**
     * 根据命令值获取对应的枚举实例
     *
     * @param mid 命令字
     * @return 匹配的 CommandType，若不存在则返回 null
     */
    public static AtlasCommandType fromMid(int mid) {
        for (AtlasCommandType type : values()) {
            if (type.mid == mid) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("%04d (%s)", mid, name);
    }
}
