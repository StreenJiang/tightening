package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AngleRangeCheck implements Capability {

    @Override public String id() { return "AngleRangeCheck"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.JUDGING; }
    @Override public int priority() { return 2; }

    @Override
    public boolean precondition(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        return bolt != null
                && bolt.getAngleMin() != null && bolt.getAngleMax() != null
                && ctx.getCurrentOperationData() != null;
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        TighteningData data = ctx.getCurrentOperationData();

        double min = bolt.getAngleMin();
        double max = bolt.getAngleMax();
        double actual = data.getAngle();
        boolean inRange = actual >= min && actual <= max;

        ctx.getExtras().put("angleInRange", inRange);
        ctx.getExtras().put("angleMin", min);
        ctx.getExtras().put("angleMax", max);
        ctx.getExtras().put("angleActual", actual);

        log.debug("AngleRangeCheck: actual={} range=[{}, {}] inRange={}", actual, min, max, inRange);
        return CapabilityResult.Pass;
    }
}
