package com.tightening.netty.protocol.codec.fit;

import com.tightening.device.handler.impl.FitSeriesHandler;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.netty.protocol.handler.fit.FitSeriesInBoundHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class FitFrameCodecTest {
    private FitFrameCodec codec;
    private EmbeddedChannel channel;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private ByteBufAllocator alloc;
    @Mock
    private FitSeriesHandler fitSeriesHandler;

    @BeforeEach
    void setup() {
        codec = new FitFrameCodec();
        channel = new EmbeddedChannel(
                new LoggingHandler(LogLevel.DEBUG),
                codec,
                new FitSeriesInBoundHandler(fitSeriesHandler)
        );

        when(ctx.alloc()).thenReturn(alloc);
    }

    @AfterEach
    void cleanUp() {
        channel.finish();
    }

    @Test
    void testEncode() {
        FitFrame fitFrame = FitFrame.enableTool();

        // 写入到出站
        channel.writeOutbound(fitFrame);

        // 读取并验证
        ByteBuf buf = channel.readOutbound();
        assertNotNull(buf);
        buf.release();
    }

    @Test
    void testDecode() throws Exception {
        String hexData = """
                AA 55 83 F8 00 7D B1 01 00 26 00 01 00 00 00 00 00 00 00 00 00 00 00 00
                80 6F 12 83 3A 00 00 00 00 00 00 00 80 6F 12 03 3B 00 00 00 00 00 00 00
                80 A6 9B 44 3B 00 00 00 00 00 00 00 80 6F 12 83 3B 00 00 00 00 00 00 00
                80 0A D7 A3 3B 00 00 00 00 CD CC CC BD A6 9B C4 3B 00 00 00 00 CD CC CC
                BD 42 60 E5 3B 00 00 00 00 CD CC CC BD 6F 12 03 3C 00 00 00 00 CD CC 4C
                BE BC 74 13 3C 00 00 00 00 9A 99 99 BE 0A D7 23 3C 00 00 00 00 9A 99 99
                BE 58 39 34 3C 00 00 00 00 CD CC CC BE A6 9B 44 3C 00 00 00 00 00 00 00
                BF F4 FD 54 3C 00 00 00 00 9A 99 19 BF 42 60 65 3C 00 00 00 00 33 33 33
                BF 8F C2 75 3C 00 00 00 00 CD CC 4C BF 6F 12 83 3C 00 00 00 00 66 66 66
                BF 96 43 8B 3C 00 00 00 00 00 00 80 BF BC 74 93 3C 00 00 00 00 CD CC 8C
                BF E3 A5 9B 3C 00 00 00 00 66 66 A6 BF 55 AA
                """;

        // 写入到入站
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        buf.writeBytes(hexToBytes(hexData));
        when(alloc.buffer()).thenReturn(buf);

        channel.attr(TCPDeviceHandler.DEVICE_ID).set(100L);
        channel.writeInbound(buf);

        assertNotNull(buf);
    }

    /**
     * 将十六进制字符串（可含空格/换行）转换为 byte[]
     *
     * @param hex 十六进制字符串，如 "AA 55 83 F8 ..."
     * @return 转换后的字节数组
     */
    private static byte[] hexToBytes(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("Hex string cannot be null");
        }
        // 1. 移除所有空白字符（空格、换行、制表符等）
        String cleaned = hex.replaceAll("\\s+", "");

        // 2. 校验长度是否为偶数（每2个字符表示1个字节）
        if (cleaned.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string length must be even");
        }

        byte[] bytes = new byte[cleaned.length() / 2];
        for (int i = 0; i < cleaned.length(); i += 2) {
            int high = Character.digit(cleaned.charAt(i), 16);
            int low = Character.digit(cleaned.charAt(i + 1), 16);

            if (high == -1 || low == -1) {
                throw new IllegalArgumentException("Invalid hex character at index: " + i);
            }
            // 3. 组合高低4位，并强转为 byte（Java byte 为有符号 -128~127，属正常现象）
            bytes[i / 2] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
