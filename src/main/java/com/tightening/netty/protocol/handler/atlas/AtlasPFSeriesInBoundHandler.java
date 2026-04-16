package com.tightening.netty.protocol.handler.atlas;

import com.tightening.constant.atlas.AtlasCommandType;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.netty.protocol.codec.atlas.AtlasFrame;
import com.tightening.netty.protocol.util.AtlasDataUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

@Slf4j
public class AtlasPFSeriesInBoundHandler extends SimpleChannelInboundHandler<AtlasFrame> {
    private final TCPDeviceHandler deviceHandler;

    public AtlasPFSeriesInBoundHandler(TCPDeviceHandler deviceHandler) {
        this.deviceHandler = deviceHandler;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, AtlasFrame msg) throws Exception {
        AtlasCommandType cmdType = AtlasCommandType.fromMid(msg.getMid());
        if (cmdType != null) {
            long deviceId = ctx.channel().attr(DEVICE_ID).get();
            byte[] data = msg.getData();

            switch (cmdType) {
                case HEARTBEAT:
                    log.info("Heart beating from server...");
                    deviceHandler.addResultFuture(deviceHandler.generateKey(cmdType.getMid(), deviceId),
                                                  true);
                    break;
                case NEGATIVE_ACK:
                    handleResult(data, deviceId, false);
                    // TODO: 0004 失败报文还包含了一个长度=2的 error code，可以考虑把这个内容加进来
                    break;
                case POSITIVE_ACK:
                    handleResult(data, deviceId, true);
                    break;
                case TIGHTEN_DATA:
                    log.debug("tightening data");
                    break;
                case CURVE_DATA:
                    log.debug("curve data");
                    break;
                default:
                    break;
            }
        } else {
            log.warn("");
        }
    }

    private void handleResult(byte[] data, long deviceId, boolean resultOk) {
        Integer value = AtlasDataUtils.parseAsciiInt(data, 0, 3);
        if (value != null) {
            AtlasCommandType mid = AtlasCommandType.fromMid(value);
            if (mid != null) {
                String key = deviceHandler.generateKey(mid.getMid(), deviceId);
                switch (mid) {
                    case SUBSCRIBE_DATA:
                        // TODO: 这里需要仔细再确认返回值内容，然后完善正确的逻辑
                        break;
                    case CONNECT:
                        key = deviceHandler.generateKey(AtlasCommandType.CONNECT_ACK, deviceId);
                    case DISABLE:
                    case ENABLE:
                    case PARAMETER_SET:
                        deviceHandler.addResultFuture(key, resultOk);
                        break;
                }
            }
        }
    }
}
