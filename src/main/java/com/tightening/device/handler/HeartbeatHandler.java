package com.tightening.device.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

/**
 * 心跳检测 Handler
 *
 * <p>功能：
 * <ul>
 *   <li>检测指定时间内无数据交互（读+写）</li>
 *   <li>超时后自动触发心跳发送</li>
 *   <li>支持心跳失败重试和断开连接策略</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * HeartbeatHandler heartbeatHandler = new HeartbeatHandler(
 *     3,   // 最大重试次数
 *     deviceId -> sendHeartbeat(deviceId)  // 心跳发送逻辑
 * );
 *
 * bootstrap.pipeline()
 *     .addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS))
 *     .addLast(heartbeatHandler);
 * }</pre>
 */
@Slf4j
public class HeartbeatHandler extends ChannelDuplexHandler {

    @Getter
    private final int maxRetryCount;
    @Getter
    private volatile int retryCount = 0;
    @Getter
    private volatile boolean heartbeatTriggered = false;
    private final Function<Long, CompletableFuture<Boolean>> heartbeatFunc;

    /**
     * 构造函数
     *
     * @param maxRetryCount 最大重试次数
     * @param heartbeatFunc 心跳发送逻辑（返回 CompletableFuture&lt;Boolean&gt;）
     */
    public HeartbeatHandler(int maxRetryCount, Function<Long, CompletableFuture<Boolean>> heartbeatFunc) {
        if (maxRetryCount < 0) {
            throw new IllegalArgumentException("maxRetryCount must be >= 0");
        }
        if (heartbeatFunc == null) {
            throw new IllegalArgumentException("heartbeatSupplier must not be null");
        }

        this.maxRetryCount = maxRetryCount;
        this.heartbeatFunc = heartbeatFunc;

        log.info("HeartbeatHandler initialized: maxRetry={}", maxRetryCount);
    }

    /**
     * 安全获取 deviceId，若未设置则返回 null
     */
    private Long getDeviceId(ChannelHandlerContext ctx) {
        return ctx.channel().attr(DEVICE_ID).get();
    }

    /**
     * 获取用于日志的标识：优先 deviceId，其次 channel id
     */
    private String getLogId(ChannelHandlerContext ctx) {
        Long deviceId = getDeviceId(ctx);
        if (deviceId != null && deviceId != 0L) {
            return String.valueOf(deviceId);
        }
        return ctx.channel().id().asShortText();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        log.debug("HeartbeatHandler added: logId={}", getLogId(ctx));
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        log.debug("HeartbeatHandler removed: logId={}", getLogId(ctx));
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel active, heartbeat monitoring started: logId={}", getLogId(ctx));
        retryCount = 0;
        heartbeatTriggered = false;
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel inactive, heartbeat monitoring stopped: logId={}", getLogId(ctx));
        retryCount = 0;
        heartbeatTriggered = false;
        super.channelInactive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.WRITER_IDLE) {
                log.debug("IdleStateEvent triggered (WRITER_IDLE): logId={}, retryCount={}",
                          getLogId(ctx), retryCount);
                onIdleTimeout(ctx);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 空闲超时处理逻辑
     */
    private void onIdleTimeout(ChannelHandlerContext ctx) {
        String logId = getLogId(ctx);

        // 防止重复触发
        if (heartbeatTriggered) {
            log.debug("Heartbeat already triggered, skipping: logId={}", logId);
            return;
        }

        // 检查是否超过最大重试次数
        if (retryCount >= maxRetryCount) {
            log.warn("Heartbeat max retry exceeded, closing channel: logId={}, retryCount={}",
                     logId, retryCount);
            ctx.close();
            return;
        }

        heartbeatTriggered = true;
        retryCount++;

        log.info("Sending heartbeat (attempt {}/{}): logId={}",
                 retryCount, maxRetryCount, logId);

        // 执行心跳发送
        sendHeartbeatWithCallback(ctx);
    }

    /**
     * 发送心跳并处理回调
     */
    private void sendHeartbeatWithCallback(ChannelHandlerContext ctx) {
        CompletableFuture<Boolean> future;
        try {
            long deviceId = ctx.channel().attr(DEVICE_ID).get();
            future = heartbeatFunc.apply(deviceId);
        } catch (Exception e) {
            log.error("Heartbeat supplier threw exception: logId={}", getLogId(ctx), e);
            onHeartbeatComplete(ctx, false, e);
            return;
        }

        // 设置超时保护（避免 Supplier 返回的 Future 永不完成）
        future.orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((success, ex) -> {
                    // 确保在 EventLoop 线程中处理
                    if (ctx.channel().eventLoop().inEventLoop()) {
                        processHeartbeatResult(ctx, success, ex);
                    } else {
                        ctx.channel().eventLoop().execute(() -> processHeartbeatResult(ctx, success, ex));
                    }
                });
    }

    /**
     * 处理心跳结果（EventLoop 线程内执行）
     */
    private void processHeartbeatResult(ChannelHandlerContext ctx,
                                        Boolean success,
                                        Throwable ex) {
        String logId = getLogId(ctx);
        if (ex != null) {
            log.warn("Heartbeat failed with exception: logId={}, retryCount={}",
                     logId, retryCount, ex);
            onHeartbeatComplete(ctx, false, ex);
        } else if (Boolean.TRUE.equals(success)) {
            log.debug("Heartbeat succeeded: logId={}, retryCount={}",
                      logId, retryCount);
            onHeartbeatComplete(ctx, true, null);
        } else {
            log.warn("Heartbeat returned false: logId={}, retryCount={}",
                     logId, retryCount);
            onHeartbeatComplete(ctx, false, null);
        }
    }

    /**
     * 心跳完成后的处理
     *
     * @param ctx     上下文
     * @param success 是否成功
     * @param ex      异常（如果有）
     */
    private void onHeartbeatComplete(ChannelHandlerContext ctx,
                                     boolean success,
                                     Throwable ex) {
        heartbeatTriggered = false;
        String logId = getLogId(ctx);

        if (success) {
            // 心跳成功，重置重试计数
            retryCount = 0;
            log.debug("Heartbeat success, retryCount reset: logId={}", logId);
        } else {
            // 心跳失败，检查是否需要继续重试
            if (retryCount >= maxRetryCount) {
                log.error("Heartbeat failed after {} attempts, closing channel: logId={}",
                          retryCount, logId, ex);
                ctx.close();
            } else {
                log.warn("Heartbeat failed, will retry on next idle timeout: logId={}, retryCount={}",
                         logId, retryCount);
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 收到数据，重置心跳触发标志（允许下次空闲时再次触发）
        heartbeatTriggered = false;
        log.trace("Channel read, heartbeat trigger reset: logId={}", getLogId(ctx));
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // 发送数据，重置心跳触发标志
        heartbeatTriggered = false;
        log.trace("Channel write, heartbeat trigger reset: logId={}", getLogId(ctx));
        super.write(ctx, msg, promise);
    }
}
