package com.tightening.lifecycle.message;

import com.tightening.entity.BoltDeviceBinding;
import com.tightening.entity.ProductBolt;
import com.tightening.entity.ProductMission;
import com.tightening.entity.ProductSide;

import java.util.List;

public sealed interface InboundCommand extends InboundMessage {

    /** 激活 Mission — 生命周期开始 */
    record ActivateMission(
        ProductMission missionData,
        List<ProductSide> sides,
        List<ProductBolt> bolts,
        List<BoltDeviceBinding> bindings
    ) implements InboundCommand {}

    /** 推进管道到下一个子状态 */
    record AdvancePipeline() implements InboundCommand {}

    /** 自循环 — 新的一轮开始 */
    record SelfLoop() implements InboundCommand {}

    /** 中断当前 Mission */
    record InterruptMission(String reason) implements InboundCommand {}
}
