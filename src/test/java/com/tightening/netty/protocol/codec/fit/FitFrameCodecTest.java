package com.tightening.netty.protocol.codec.fit;

import com.tightening.constant.fit.FitCommandType;
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

    @BeforeEach
    void setup() {
        codec = new FitFrameCodec();
        channel = new EmbeddedChannel(new LoggingHandler(LogLevel.DEBUG), codec);

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
        FitFrame fitFrame = FitFrame.sendPSet(5);

        // 写入到入站
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        when(alloc.buffer()).thenReturn(buf);

        codec.encode(ctx, fitFrame, new ArrayList<>());
        channel.writeInbound(buf);

        FitFrame decodedFrame = channel.readInbound();
        assertNotNull(decodedFrame);
    }
}