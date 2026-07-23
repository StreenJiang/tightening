package com.tightening.lifecycle.capability;

import com.tightening.constant.LockReason;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductBolt;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BoltBarCodeCheck implements Capability {
    @Override public String id() { return "BoltBarCodeCheck"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 3; }

    @Override
    public boolean precondition(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        if (bolt == null) return false;
        return ctx.getBoltBarcodeRuleIds().containsKey(bolt.getId());
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        Long ruleId = ctx.getBoltBarcodeRuleIds().get(bolt.getId());

        if (ctx.getPartsCode() == null || ctx.getPartsCode().isEmpty()) {
            ctx.getLockReasons().add(LockReason.BARCODE_REQUIRED);
            log.debug("Bolt {} requires barcode scan (ruleId={})", bolt.getSerialNum(), ruleId);
            return CapabilityResult.Pass;
        }

        ctx.getLockReasons().remove(LockReason.BARCODE_REQUIRED);
        log.debug("Bolt {} barcode check passed", bolt.getSerialNum());
        return CapabilityResult.Pass;
    }
}
