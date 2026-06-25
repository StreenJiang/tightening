package com.tightening.netty.protocol.handler.atlas;

import com.tightening.constant.atlas.AtlasCommandType;
import com.tightening.constant.atlas.AtlasErrorCode;
import com.tightening.device.handler.ToolHandler;
import com.tightening.netty.protocol.codec.atlas.AtlasFrame;
import com.tightening.netty.protocol.util.atlas.AtlasDataUtils;
import com.tightening.netty.protocol.util.atlas.AtlasTighteningDataParser;
import com.tightening.dto.CurveDataDTO;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.netty.protocol.util.atlas.AtlasCurveDataParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

@Slf4j
public class AtlasPFSeriesInBoundHandler extends SimpleChannelInboundHandler<AtlasFrame> {
    private final ToolHandler deviceHandler;

    public AtlasPFSeriesInBoundHandler(ToolHandler deviceHandler) {
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
                    deviceHandler.addResultFuture(deviceHandler.generateKey(cmdType.getMid(), deviceId), true);
                    break;
                case CONNECT_ACK:
                    log.info("Connect succeed...");
                    deviceHandler.addResultFuture(deviceHandler.generateKey(cmdType.getMid(), deviceId), true);
                    break;
                case NEGATIVE_ACK:
                    Integer errCodeInt = AtlasDataUtils.parseAsciiInt(data, 4, 2);
                    if (errCodeInt != null) {
                        AtlasErrorCode errorCode = AtlasErrorCode.fromCode(errCodeInt).orElse(null);
                        if (errorCode != null) {
                            String key = deviceHandler.generateKey(AtlasCommandType.NEGATIVE_ACK.getMid(), deviceId);
                            deviceHandler.addErrorMsgFuture(key, errorCode.getDescription());
                        }
                    } else {
                        log.warn("Negative ack error code not found, deviceId={}", deviceId);
                    }

                    // 最后再处理结果以保证结果拿到后错误信息一定已经存在
                    handlePositiveOrNegativeResult(data, deviceId, false);
                    break;
                case POSITIVE_ACK:
                    handlePositiveOrNegativeResult(data, deviceId, true);
                    break;
                case TIGHTEN_DATA:
                    TighteningDataDTO dto = AtlasTighteningDataParser.parse(
                            msg.getData(), msg.getRevision());
                    deviceHandler.handleTighteningData(dto, ctx.channel());
                    break;
                case CURVE_DATA:
                    CurveDataDTO curveDto = AtlasCurveDataParser.parse(
                            msg.getData(), msg.getAttachedData(), msg.getRevision());
                    deviceHandler.handleCurveData(curveDto, ctx.channel());
                    break;
                default:
                    break;
            }
        } else {
            log.warn("");
        }
    }

    private void handlePositiveOrNegativeResult(byte[] data, long deviceId, boolean result) {
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
                        deviceHandler.addResultFuture(key, result);
                        break;
                    case DISABLE:
                    case ENABLE:
                    case PARAMETER_SET:
                        deviceHandler.addResultFuture(key, result);
                        break;
                }
            }
        }
    }
}
