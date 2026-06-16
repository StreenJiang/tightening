package com.tightening.netty.protocol.handler.fit;

import com.tightening.constant.fit.FitCommandType;
import com.tightening.device.handler.ToolHandler;
import com.tightening.dto.CurveDataDTO;
import com.tightening.dto.TighteningDataDTO;
import com.tightening.netty.protocol.util.fit.FitDataUtils;
import com.tightening.netty.protocol.util.fit.FitCurveDataParser;
import com.tightening.netty.protocol.util.fit.FitTighteningDataParser;
import com.tightening.netty.protocol.codec.fit.FitFrame;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

import static com.tightening.constant.fit.FitConstants.COMMAND_OK;
import static com.tightening.device.handler.impl.TCPDeviceHandler.DEVICE_ID;

@Slf4j
public class FitSeriesInBoundHandler extends SimpleChannelInboundHandler<FitFrame> {
    private final ToolHandler deviceHandler;

    public FitSeriesInBoundHandler(ToolHandler deviceHandler) {
        this.deviceHandler = deviceHandler;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FitFrame msg) throws Exception {
        FitCommandType cmdType = FitCommandType.fromCode(msg.getCmdType());
        if (cmdType != null) {
            long deviceId = ctx.channel().attr(DEVICE_ID).get();
            String key = deviceHandler.generateKey(cmdType.getCode(), deviceId);
            byte[] data = msg.getData();

            switch (cmdType) {
                case HEARTBEAT_ACK:
                    byte[] timestampBytes = Arrays.copyOfRange(data, 4, 8);
                    log.info("Heart beating from server at {}", FitDataUtils.getDateStr(timestampBytes));
                    deviceHandler.addResultFuture(key, true);
                    break;
                case PARAMETER_SET:
                case ENABLE_DISABLE:
                    byte datum = data[0];
                    deviceHandler.addResultFuture(key, datum == COMMAND_OK);
                    break;
                case TIGHTEN_FINAL:
                    TighteningDataDTO tighteningDataDTO = FitTighteningDataParser.parse(data);
                    // TODO: 这里需要对 tighteningDataDTO 中的任务（配方）相关的数据进行补充，包括 mission record
                    deviceHandler.handleTighteningData(tighteningDataDTO, ctx.channel());
                    // TODO: 这里需要 SSE 给前端发送拧紧数据，必须包含结果
                    break;
                case CURVE:
                    CurveDataDTO curveDataDTO = FitCurveDataParser.parse(data);
                    // TODO: 这里需要对 curveDataDTO 中的任务（配方）相关的数据进行补充，包括 mission record
                    deviceHandler.handleCurveData(curveDataDTO, ctx.channel());
                    // TODO: 这里需要 SSE 给前端发送拧紧数据，必须包含结果
                    break;
                case ALARM:
                    deviceHandler.handleAlarm(FitDataUtils.parseAlarmData(data), deviceId);
                    break;
                default:
                    break;
            }
        } else {
            log.warn("");
        }
    }
}
