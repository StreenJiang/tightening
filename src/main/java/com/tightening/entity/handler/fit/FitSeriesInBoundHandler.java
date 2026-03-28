package com.tightening.entity.handler.fit;

import com.tightening.constant.fit.FitCommandType;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.netty.protocol.fit.FitDataUtils;
import com.tightening.netty.protocol.fit.FitFrame;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import static com.tightening.constant.fit.FitConstants.COMMAND_OK;
import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

@Slf4j
public class FitSeriesInBoundHandler extends SimpleChannelInboundHandler<FitFrame> {
    private final TCPDeviceHandler deviceHandler;

    public FitSeriesInBoundHandler(TCPDeviceHandler deviceHandler) {
        this.deviceHandler = deviceHandler;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FitFrame msg) throws Exception {
        FitCommandType cmdType = FitCommandType.fromCode(msg.getCmdType());
        byte[] data = msg.getData();

        if (cmdType != null) {
            long deviceId = ctx.channel().attr(DEVICE_ID).get();
            String key = deviceHandler.generateKey(cmdType.getCode(), deviceId);

            switch (cmdType) {
                case HEARTBEAT_ACK:
                    break;
                case PARAMETER_SET:
                case ENABLE_DISABLE:
                    byte datum = data[0];
                    deviceHandler.addResultFuture(key, datum == COMMAND_OK);
                    break;
                case TIGHTEN_FINAL:
                    TighteningDataDTO tighteningDataDTO = FitDataUtils.parseTighteningData(data);
                    System.out.println();
                    break;
                case CURVE:
                    break;
                case ALARM:
                    String alarmMsg = parseAlarmData(data);
                    log.info("Alarm message: {}", alarmMsg);
                    break;
                default:
                    break;
            }
        } else {
            log.warn("");
        }
    }

    /**
     * 解析报警信息数据区内容
     * 数据区格式：[报警码(2)][级别(1)][信息长度(1)][信息内容(GKB)][时间戳(7)]
     *
     * @param data 数据区字节数组
     * @return 解析后的字符串描述
     */
    public static String parseAlarmData(byte[] data) {
        if (data == null || data.length < 11) { // 最小长度：2+1+1+0+7=11
            throw new IllegalArgumentException("数据区长度不足，最小需要11字节");
        }

        int offset = 0;

        // 1. 解析报警码（2字节，大端）
        int alarmCode = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        offset += 2;

        // 2. 解析级别（1字节）
        int level = data[offset] & 0xFF;
        String levelStr = switch (level) {
            case 0 -> "Info";
            case 1 -> "Warning";
            case 2 -> "Error";
            default -> "Unknown(" + level + ")";
        };
        offset += 1;

        // 3. 解析信息长度（1字节）
        int infoLength = data[offset] & 0xFF;
        offset += 1;

        // 4. 解析信息内容（GBK编码）
        String infoContent = "";
        if (infoLength > 0) {
            if (offset + infoLength > data.length) {
                throw new IllegalArgumentException("信息长度超出数据区范围");
            }
            try {
                infoContent = new String(data, offset, infoLength, "GBK");
            } catch (Exception e) {
                infoContent = "<解析失败>";
            }
            offset += infoLength;
        }

        // 5. 解析时间戳（7字节，BCD码）
        String timestamp = getTimestampStr(data, offset);

        // 组装结果
        return String.format("报警码:0x%04X, 级别:%s, 信息:%s, 时间:%s",
                             alarmCode, levelStr, infoContent, timestamp);
    }

    private static String getTimestampStr(byte[] data, int offset) {
        if (offset + 7 > data.length) {
            throw new IllegalArgumentException("时间戳数据不完整");
        }

        int yearHigh = FitDataUtils.bcdToInt(data[offset]);       // 年高2位
        int yearLow = FitDataUtils.bcdToInt(data[offset + 1]);    // 年低2位
        int month = FitDataUtils.bcdToInt(data[offset + 2]);
        int day = FitDataUtils.bcdToInt(data[offset + 3]);
        int hour = FitDataUtils.bcdToInt(data[offset + 4]);
        int minute = FitDataUtils.bcdToInt(data[offset + 5]);
        int second = FitDataUtils.bcdToInt(data[offset + 6]);

        int year = yearHigh * 100 + yearLow;
        return String.format("%04d-%02d-%02d %02d:%02d:%02d",
                             year, month, day, hour, minute, second);
    }
}
