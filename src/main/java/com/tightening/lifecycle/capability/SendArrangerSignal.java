package com.tightening.lifecycle.capability;

import com.tightening.constant.LockReason;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.device.contract.IArranger;
import com.tightening.entity.ProductBolt;
import com.tightening.lifecycle.TaskContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class SendArrangerSignal implements Capability {

    @Override public String id() { return "SendArrangerSignal"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.SWITCH_BOLT; }
    @Override public int priority() { return 0; }

    @Override
    public boolean precondition(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        return bolt != null && bolt.getArrangerDeviceId() != null
                && bolt.getArrangerChannels() != null && !bolt.getArrangerChannels().isBlank();
    }

    @Override
    public CapabilityResult execute(TaskContext ctx) {
        ProductBolt bolt = ctx.currentBolt();
        IArranger arranger = ctx.getArrangerRegistry().get(bolt.getArrangerDeviceId());
        if (arranger == null) {
            log.warn("Arranger not found: deviceId={}", bolt.getArrangerDeviceId());
            return CapabilityResult.Skip;
        }
        if (!arranger.isConnected()) {
            log.warn("Arranger not connected: deviceId={}", bolt.getArrangerDeviceId());
            return CapabilityResult.Fail;
        }

        int[] channels = parseChannels(bolt.getArrangerChannels(), 8);

        ctx.getLockReasons().add(LockReason.ARRANGER_POSITIONING);
        try {
            Boolean ok = arranger.sendPulse(channels, 200)
                    .get(3, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(ok)) {
                log.info("Arranger pulse sent: deviceId={}, channels={}",
                        bolt.getArrangerDeviceId(), bolt.getArrangerChannels());
                return CapabilityResult.Pass;
            }
            return CapabilityResult.Fail;
        } catch (Exception e) {
            log.error("Arranger pulse failed: deviceId={}", bolt.getArrangerDeviceId(), e);
            return CapabilityResult.Fail;
        } finally {
            ctx.getLockReasons().remove(LockReason.ARRANGER_POSITIONING);
        }
    }

    private int[] parseChannels(String channelsStr, int maxChannels) {
        int[] channels = new int[maxChannels];
        if (channelsStr == null || channelsStr.isBlank()) return channels;
        for (String part : channelsStr.split(",")) {
            try {
                int idx = Integer.parseInt(part.trim()) - 1; // 1-based → 0-based
                if (idx >= 0 && idx < maxChannels) channels[idx] = 1;
            } catch (NumberFormatException ignored) {}
        }
        return channels;
    }
}
