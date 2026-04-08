package com.tightening.netty.protocol.util;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
public class AtlasDataUtils {

    /**
     * 工具方法：ASCII 数字字段 → Integer
     *
     * @param data   源数据
     * @param offset 索引偏移量
     * @param length 数据长度
     * @return
     */
    public static Integer parseAsciiInt(byte[] data, int offset, int length) {
        String raw = new String(data, offset, length, StandardCharsets.US_ASCII).trim();
        return raw.isEmpty() ? null : Integer.parseInt(raw);
    }

    /**
     * Integer → ASCII 数字字段（右对齐，0填充）
     *
     * @param value  数值（null 表示空值）
     * @param length 字段总长度
     * @return 定长 ASCII 字符串，如：123 → "000123"
     */
    public static String encodeIntField(Integer value, int length) {
        if (value == null) {
            return " ".repeat(length);  // 空值用空格填充
        }
        return String.format("%0" + length + "d", value);
    }

    /**
     * String → ASCII 字符串字段（左对齐，空格填充）
     *
     * @param value  字符串（null 表示空值）
     * @param length 字段总长度
     * @return 定长 ASCII 字符串，如："ABC" → "ABC    "
     */
    public static String encodeStringField(String value, int length) {
        if (value == null) {
            return " ".repeat(length);  // 空值用空格填充
        }
        if (value.length() > length) {
            value = value.substring(0, length);  // 截断超长部分
        }
        return String.format("%-" + length + "s", value);
    }

    /**
     * Double → ASCII 浮点数字段
     *
     * @param value    数值
     * @param length   总长度（包含小数点）
     * @param decimals 小数位数
     * @return 定长 ASCII 字符串，如：12.34 → "0012.34"
     */
    public static String encodeDoubleField(Double value, int length, int decimals) {
        if (value == null) {
            return " ".repeat(length);
        }
        return String.format("%" + length + "." + decimals + "f", value);
    }

    /**
     * 格式化数值为 ASCII 十进制字符串
     * null 值用空格填充指定长度
     *
     * @param value  值
     * @param length 值的长度
     * @return byte格式数据
     */
    public static byte[] formatAscii(Integer value, int length) {
        if (value == null) {
            // null 用空格填充
            byte[] spaces = new byte[length];
            java.util.Arrays.fill(spaces, (byte) ' ');
            return spaces;
        }

        String str = String.valueOf(value);
        if (str.length() > length) {
            throw new IllegalArgumentException(
                    "Value " + value + " exceeds field length " + length);
        }

        // 右对齐，左侧用 '0' 填充
        String formatted = String.format("%0" + length + "d", value);
        return formatted.getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] decodeData(ByteBuf msg, int remainingLength) {
        if (msg.readableBytes() < remainingLength) {
            return null;
        }

        int searchStart = msg.readerIndex();
        int searchEnd = searchStart + remainingLength + 1;
        int nullIndex = msg.indexOf(searchStart, searchEnd, (byte) 0);

        if (nullIndex == -1) {
            return null;
        }

        // 只读取结束符之前的，因为特殊情况下，结束符后面可能还有额外数据，也会被计算入 remainingLength
        int dataLength = nullIndex - searchStart;
        byte[] data = new byte[dataLength];
        msg.readBytes(data);

        // 消费结束符
        msg.readByte();
        return data;
    }

    public static byte[] decodeCurveData(ByteBuf msg, int curveDataLength) {
        if (msg.readableBytes() < curveDataLength) {
            return null; // 数据不完整，等待
        }

        byte[] data = new byte[curveDataLength];
        msg.readBytes(data);
        return data;
    }

    /**
     * 安全解析定长 ASCII 数字字段（兼容空格填充/全空格情况）
     */
    public static Integer parseAsciiInt(ByteBuf buf, int length) {
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        String str = new String(bytes, StandardCharsets.US_ASCII).trim();
        return str.isEmpty() ? null : Integer.valueOf(str);
    }

    public static int parseAsciiInt(byte[] data) {
        String str = new String(data, StandardCharsets.US_ASCII).trim();
        return str.isEmpty() ? 0 : Integer.parseInt(str);
    }
}
