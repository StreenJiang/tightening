package com.tightening.netty.protocol.handler.atlas;

import com.tightening.constant.atlas.AtlasCommandType;
import com.tightening.constant.atlas.AtlasErrorCode;
import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.netty.protocol.codec.atlas.AtlasFrame;
import com.tightening.netty.protocol.util.AtlasDataUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

import java.util.Optional;

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
                    Integer errCodeInt = AtlasDataUtils.parseAsciiInt(data, 4, 2);
                    AtlasErrorCode errorCode = AtlasErrorCode.fromCode(errCodeInt).orElse(null);
                    if (errorCode != null) {
                        String key = deviceHandler.generateKey(AtlasCommandType.NEGATIVE_ACK.getMid(),
                                                               deviceId);
                        deviceHandler.addErrorMsgFuture(key, errorCode.getDescription());
                    }
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
        Integer value = AtlasDataUtils.parseAsciiInt(data, 0, 4);
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
