package com.tightening.netty.protocol.util.fit;

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
public class FitDataUtils {

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

        int alarmCode = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        offset += 2;

        int level = data[offset] & 0xFF;
        String levelStr = switch (level) {
            case 0 -> "Info";
            case 1 -> "Warning";
            case 2 -> "Error";
            default -> "Unknown(" + level + ")";
        };
        offset += 1;

        int infoLength = data[offset] & 0xFF;
        offset += 1;

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

        String timestamp = getTimestampStr(data, offset);
        return String.format("报警码:0x%04X, 级别:%s, 信息:%s, 时间:%s",
                             alarmCode, levelStr, infoContent, timestamp);
    }

    static String getTimestampStr(byte[] data, int offset) {
        if (offset + 7 > data.length) {
            throw new IllegalArgumentException("时间戳数据不完整");
        }

        int yearHigh = FitDataUtils.bcdToInt(data[offset]);
        int yearLow = FitDataUtils.bcdToInt(data[offset + 1]);
        int month = FitDataUtils.bcdToInt(data[offset + 2]);
        int day = FitDataUtils.bcdToInt(data[offset + 3]);
        int hour = FitDataUtils.bcdToInt(data[offset + 4]);
        int minute = FitDataUtils.bcdToInt(data[offset + 5]);
        int second = FitDataUtils.bcdToInt(data[offset + 6]);

        int year = yearHigh * 100 + yearLow;
        return String.format("%04d-%02d-%02d %02d:%02d:%02d",
                             year, month, day, hour, minute, second);
    }

    /**
     * 解析7字节BCD码时间戳（年2字节 + 月1字节 + 日1字节 + 时1字节 + 分1字节 + 秒1字节）
     *
     * @param data   原始字节数组
     * @param offset 起始偏移
     * @return 格式化后的时间字符串 yyyy-MM-dd HH:mm:ss
     */
    static String parseBcdTimestamp(byte[] data, int offset) {
        // 年：2字节BCD，如 0x20 0x24 -> 2024
        int year = bcdToInt(data[offset]) * 100 + bcdToInt(data[offset + 1]);
        // 月、日、时、分、秒各1字节BCD
        int month = bcdToInt(data[offset + 2]);
        int day = bcdToInt(data[offset + 3]);
        int hour = bcdToInt(data[offset + 4]);
        int minute = bcdToInt(data[offset + 5]);
        int second = bcdToInt(data[offset + 6]);

        // 构建LocalDateTime并格式化
        LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second);
        return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 将BCD码字节转换为整数（每个字节高4位和低4位分别表示数字）
     *
     * @param b BCD字节
     * @return 整数
     */
    public static int bcdToInt(byte b) {
        int high = (b >> 4) & 0x0F;
        int low = b & 0x0F;
        return high * 10 + low;
    }

    /**
     * 获取当前时间戳的4字节数组（小端模式）
     * 对应协议示例：时间戳=1234567890 → 字节数组 [D2, 02, 96, 49]
     *
     * @return 4字节数组，小端存储
     */
    public static byte[] getCurrentTimestampBytes() {
        long timestamp = System.currentTimeMillis() / 1000; // 秒级时间戳
        return longToLittleEndianBytes(timestamp, 4);
    }

    /**
     * 指定时间戳的4字节数组（小端模式）
     *
     * @param timestamp Unix时间戳（秒）
     * @return 4字节数组
     */
    public static byte[] getTimestampBytes(long timestamp) {
        return longToLittleEndianBytes(timestamp, 4);
    }

    /**
     * 将long转换为小端字节数组
     */
    private static byte[] longToLittleEndianBytes(long value, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) ((value >> (i * 8)) & 0xFF);
        }
        return bytes;
    }

    /**
     * 将小端字节数组转换为long
     */
    public static long bytesToTimestamp(byte[] bytes) {
        long result = 0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }

    public static String getDateStr(byte[] bytes) {
        long timestampSec = bytesToTimestamp(bytes);
        Instant instant = Instant.ofEpochSecond(timestampSec);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 获取当前时间戳字符串
     */
    static String getCurrentTimestampStr() {
        LocalDateTime now = LocalDateTime.now();
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
