package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ITool;
import com.tightening.entity.ProductBolt;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendPSet implements Capability {

    @Override public String id() { return "SendPSet"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 2; }

    @Override
    public boolean precondition(MissionContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        return bolt != null && bolt.getParameterSetId() != null;
    }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        ITool tool = resolveTool(ctx);
        if (tool == null) {
            log.warn("No tool found for bolt {}", bolt.getBoltSerialNum());
            return CapabilityResult.Fail;
        }
        tool.sendPSet(bolt.getParameterSetId().intValue())
            .whenComplete((ok, ex) -> {
                if (ex != null || !Boolean.TRUE.equals(ok)) {
                    log.warn("SendPSet failed for bolt {}: ok={}", bolt.getBoltSerialNum(), ok, ex);
                } else {
                    ctx.setCurrentPSet(bolt.getParameterSetId().intValue());
                }
            });
        log.info("PSet {} sent for bolt {}", bolt.getParameterSetId(), bolt.getBoltSerialNum());
        return CapabilityResult.Pass;
    }

    private ITool resolveTool(MissionContext ctx) {
        return ctx.getDeviceRegistry().values().stream()
            .findFirst().orElse(null);
    }
}
