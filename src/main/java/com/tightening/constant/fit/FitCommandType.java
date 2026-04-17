package com.tightening.constant.fit;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 指令类型枚举
 * 对应协议中的命令字及指令名称
 */
@Getter
public enum FitCommandType {

    // TODO: need i18n here for String description
    PARAMETER_SET((byte) 0x01, "程序号"),
    ENABLE_DISABLE((byte) 0x02, "使能/锁止信号"),
    START((byte) 0x03, "发送启动信号"),
    BARCODE((byte) 0x04, "发送条码"),
    SUBSCRIBE((byte) 0x05, "订阅指令"),
    HEARTBEAT_REQ((byte) 0x07, "心跳请求"),
    QUERY((byte) 0x08, "查询信息"),

    TIGHTEN_FINAL((byte) 0x81, "最终拧紧数据"),
    TIGHTEN_MULTI((byte) 0x82, "多步拧紧数据"),
    CURVE((byte) 0x83, "拧紧曲线数据"),
    ALARM((byte) 0x84, "报警信息"),
    STATUS((byte) 0x85, "设备运行状态"),
    HEARTBEAT_ACK((byte) 0x86, "心跳应答"),
    QUERY_RESP((byte) 0x87, "查询结果响应");

    private final byte code;      // 命令字（十六进制值）
    private final String name;   // 指令名称

    private static final Map<Byte, FitCommandType> BY_CODE = new HashMap<>();

    static {
        for (FitCommandType type : values()) {
            BY_CODE.put(type.code, type);
        }
    }

    FitCommandType(byte code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 根据命令字字节值获取对应的枚举实例（字节参数）
     *
     * @param code 命令字（byte 类型）
     * @return 匹配的 CommandType，若不存在则返回 null
     */
    public static FitCommandType fromCode(byte code) {
        return BY_CODE.get(code);
    }

    @Override
    public String toString() {
        return String.format("0x%02X (%s)", code, name);
    }
}
