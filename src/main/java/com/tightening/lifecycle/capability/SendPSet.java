package com.tightening.lifecycle.capability;

import com.tightening.constant.LockReason;
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
        ctx.getLockReasons().add(LockReason.PSET_SENDING);
        tool.sendPSet(bolt.getParameterSetId().intValue())
            .whenComplete((ok, ex) -> {
                try {
                    if (ex != null || !Boolean.TRUE.equals(ok)) {
                        log.warn("SendPSet failed for bolt {}: ok={}", bolt.getBoltSerialNum(), ok, ex);
                    } else {
                        ctx.setCurrentPSet(bolt.getParameterSetId().intValue());
                    }
                } finally {
                    ctx.getLockReasons().remove(LockReason.PSET_SENDING);
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
