package com.tightening.lifecycle.capability;

import com.tightening.constant.LockReason;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.ISetterSelector;
import com.tightening.entity.ProductBolt;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class SendSetterSelector implements Capability {

    @Override public String id() { return "SendSetterSelector"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 1; }

    @Override
    public boolean precondition(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        return bolt != null && bolt.getSetterSelectorId() != null
                && bolt.getSetterPosition() != null;
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        ISetterSelector setter = ctx.getSetterSelectorRegistry().get(bolt.getSetterSelectorId());
        if (setter == null) {
            log.warn("SetterSelector not found: deviceId={}", bolt.getSetterSelectorId());
            return CapabilityResult.Skip;
        }
        if (!setter.isConnected()) {
            log.warn("SetterSelector not connected: deviceId={}", bolt.getSetterSelectorId());
            return CapabilityResult.Fail;
        }

        int position = bolt.getSetterPosition();
        ctx.getLockReasons().add(LockReason.SOCKET_SELECTING);
        try {
            Boolean ok = setter.writePosition(position)
                    .get(3, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(ok)) {
                log.info("SetterSelector position {} set: deviceId={}",
                        position, bolt.getSetterSelectorId());
                return CapabilityResult.Pass;
            }
            return CapabilityResult.Fail;
        } catch (Exception e) {
            log.error("SetterSelector write failed: deviceId={}", bolt.getSetterSelectorId(), e);
            return CapabilityResult.Fail;
        } finally {
            ctx.getLockReasons().remove(LockReason.SOCKET_SELECTING);
        }
    }
}
