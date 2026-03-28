package com.tightening.device.handler;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;

@SpringBootTest  // 加载 Spring 上下文（如需 @Autowired 等）
@DisplayName("HeartbeatHandler 简化测试")
class HeartbeatHandlerTest {

    private static final AttributeKey<Long> DEVICE_ID = AttributeKey.valueOf("deviceId");
    private static final long TEST_DEVICE_ID = 12345L;

    private EmbeddedChannel channel;
    private HeartbeatHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        // 准备测试通道
        channel = new EmbeddedChannel();
        channel.attr(DEVICE_ID).set(TEST_DEVICE_ID);

        // 创建 Handler（心跳逻辑用 lambda 简单模拟）
        handler = new HeartbeatHandler(3, deviceId -> {
            // 模拟心跳：总是成功
            return CompletableFuture.completedFuture(true);
        });

        channel.pipeline().addLast(handler);
        handler.channelActive(channel.pipeline().firstContext());
    }

    @Test
    @DisplayName("心跳成功 → 重试计数重置")
    void testHeartbeatSuccess() throws InterruptedException {
        // 触发空闲事件
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        // 等待异步回调（简单场景睡 100ms 足够）
        Thread.sleep(100);

        // 验证：成功后 retryCount 应为 0
        assertEquals(0, handler.getRetryCount());
        assertFalse(handler.isHeartbeatTriggered());
    }

    @Test
    @DisplayName("心跳失败 → 重试计数累加")
    void testHeartbeatFailed() throws InterruptedException {
        // 替换为失败的心跳逻辑
        handler = new HeartbeatHandler(3, deviceId ->
                CompletableFuture.completedFuture(false));
        channel.pipeline().replace(HeartbeatHandler.class, "heartbeat", handler);

        // 触发 2 次空闲
        for (int i = 0; i < 2; i++) {
            channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
            Thread.sleep(100);
        }

        // 验证：重试计数累加，但未关闭通道
        assertEquals(2, handler.getRetryCount());
        assertTrue(channel.isOpen());
    }

    @Test
    @DisplayName("收到数据 → 重置触发标志")
    void testChannelReadResetsFlag() {
        // 手动设置触发标志（模拟已触发心跳）
        setTriggered(handler, true);

        // 模拟收到业务数据
        channel.writeInbound("test data");

        // 验证：标志被重置
        assertFalse(handler.isHeartbeatTriggered());
    }

    @Test
    @DisplayName("超过最大重试 → 关闭通道")
    void testExceedMaxRetryClosesChannel() throws InterruptedException {
        // 失败的心跳逻辑
        handler = new HeartbeatHandler(2, deviceId ->
                CompletableFuture.completedFuture(false));
        channel.pipeline().replace(HeartbeatHandler.class, "heartbeat", handler);

        // 触发 3 次（超过 maxRetry=2）
        for (int i = 0; i < 3; i++) {
            channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
            Thread.sleep(100);
        }

        // 验证：通道已关闭
        assertFalse(channel.isOpen());
    }

    // ============== 辅助方法 ==============

    /**
     * 简单反射设置 heartbeatTriggered（测试专用）
     */
    private void setTriggered(HeartbeatHandler h, boolean value) {
        try {
            var f = HeartbeatHandler.class.getDeclaredField("heartbeatTriggered");
            f.setAccessible(true);
            f.set(h, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
