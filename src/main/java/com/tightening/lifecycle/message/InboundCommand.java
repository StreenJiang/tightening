package com.tightening.lifecycle.message;

import org.springframework.lang.Nullable;

public sealed interface InboundCommand extends InboundMessage {

    /** 推进管道到下一个子状态 */
    record AdvancePipeline() implements InboundCommand {}

    /** 中断当前 Task */
    record InterruptTask(String reason) implements InboundCommand {}

    /** 触发激活请求 — 携带条码信息，投递到引擎 inbox */
    record TriggerRequest(
        @Nullable String productCode,
        @Nullable String partsCode
    ) implements InboundCommand {}
}
