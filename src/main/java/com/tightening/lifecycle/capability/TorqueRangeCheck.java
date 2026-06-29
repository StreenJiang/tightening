package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TorqueRangeCheck implements Capability {

    @Override public String id() { return "TorqueRangeCheck"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.JUDGING; }
    @Override public int priority() { return 1; }

    @Override
    public boolean precondition(MissionContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        return bolt != null
                && bolt.getTorqueMin() != null && bolt.getTorqueMax() != null
                && ctx.getCurrentOperationData() != null;
    }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        TighteningData data = ctx.getCurrentOperationData();

        double min = bolt.getTorqueMin();
        double max = bolt.getTorqueMax();
        double actual = data.getTorque();
        boolean inRange = actual >= min && actual <= max;

        ctx.getExtras().put("torqueInRange", inRange);
        ctx.getExtras().put("torqueMin", min);
        ctx.getExtras().put("torqueMax", max);
        ctx.getExtras().put("torqueActual", actual);

        log.debug("TorqueRangeCheck: actual={} range=[{}, {}] inRange={}", actual, min, max, inRange);
        return CapabilityResult.Pass;
    }
}
