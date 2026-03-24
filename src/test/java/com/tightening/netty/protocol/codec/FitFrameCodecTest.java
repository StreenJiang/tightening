package com.tightening.netty.protocol.codec;

import com.tightening.constant.fit.CommandType;
import com.tightening.netty.protocol.fit.FitFrame;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FitFrameCodecTest {
    private FitFrameCodec codec;
    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        codec = new FitFrameCodec();
        channel = new EmbeddedChannel(new LoggingHandler(LogLevel.DEBUG), codec);
    }

    @AfterEach
    void cleanUp() {
        channel.finish();
    }

    @Test
    void testEncode() {
        FitFrame fitFrame = new FitFrame(CommandType.ENABLE.getCode(), new byte[] { 0x01 });

        // 写入到出站
        channel.writeOutbound(fitFrame);

        // 读取并验证
        ByteBuf buf = channel.readOutbound();
        assertNotNull(buf);
        buf.release();
    }

    @Test
    void testDecode() throws Exception {
        FitFrame fitFrame = new FitFrame(CommandType.PARAMETER_SET.getCode(), new byte[] { 0x05 });

        // 写入到入站
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        codec.encode(null, fitFrame, buf);
        channel.writeInbound(buf);

        FitFrame decodedFrame = channel.readInbound();
        assertNotNull(decodedFrame);
    }
}