package com.tightening.netty.protocol.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
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

    /**
     * 标准格式：20字节头部 + 数据 + '\0'结束符
     */
    public static byte[] decodeStandard(ByteBuf msg, int totalLength) {
        int dataLength = totalLength - 20;
        if (dataLength < 0) {
            throw new IllegalArgumentException("Invalid length: " + totalLength);
        }
        if (msg.readableBytes() < dataLength + 1) {
            return null;
        }

        byte[] data = null;
        if (dataLength > 0) {
            data = new byte[dataLength];
            msg.readBytes(data);
        }

        // 消费结束符
        if (msg.readByte() != 0) {
            log.warn("Expected terminator 0x00, got 0x{}",
                     Integer.toHexString(msg.getByte(msg.readerIndex() - 1) & 0xFF));
        }
        return data;
    }

    /**
     * CURVE_DATA 特殊格式：
     * [主数据...][5字节ASCII长度][\0][扩展数据...]
     */
    public static byte[] decodeCurveData(ChannelHandlerContext ctx, ByteBuf msg, int declaredTotalLength) {
        int declaredDataLength = declaredTotalLength - 20;
        if (declaredDataLength < 0) {
            throw new IllegalArgumentException("Invalid length field: " + declaredTotalLength);
        }

        // 定位 '\0'
        int searchStart = msg.readerIndex();
        int searchEnd = searchStart + declaredDataLength;
        int nullAbsIndex = msg.indexOf(searchStart, searchEnd, (byte) 0);

        if (nullAbsIndex == -1) {
            return null; // 未找到分隔符，数据不足
        }

        int nullRelPos = nullAbsIndex - searchStart;
        if (nullRelPos < 5) {
            throw new IllegalArgumentException("CURVE_DATA: < 5 bytes before terminator");
        }

        // 提取最后 5 字节 ASCII 长度
        byte[] lenAscii = new byte[5];
        msg.getBytes(nullAbsIndex - 5, lenAscii);
        int trailingLen = Integer.parseInt(new String(lenAscii, StandardCharsets.US_ASCII).trim());

        // 检查是否足够：nullRelPos(主数据+5字节长度) + 1(\0) + trailingLen
        int neededBytes = nullRelPos + 1 + trailingLen;
        if (msg.readableBytes() < neededBytes) {
            return null; // 扩展数据未到达，等待
        }

        // 数据完整，开始读取
        int mainDataLen = nullRelPos - 5;
        ByteBuf combined = ctx.alloc().buffer(mainDataLen + trailingLen);
        try {
            if (mainDataLen > 0) {
                combined.writeBytes(msg, mainDataLen);
            }

            msg.readByte(); // 跳过 '\0'

            if (trailingLen > 0) {
                combined.writeBytes(msg, trailingLen);
            }

            byte[] result = new byte[combined.readableBytes()];
            combined.getBytes(combined.readerIndex(), result);
            return result;
        } finally {
            combined.release(); // 确保池化内存释放
        }
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
}
