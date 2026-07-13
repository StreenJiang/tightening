package com.tightening.lifecycle.capability;

import com.tightening.constant.DeviceType;
import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.entity.TighteningData;
import com.tightening.lifecycle.MissionContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReceiveData implements Capability {

    @Override public String id() { return "ReceiveData"; }
    @Override public Stage stage() { return Stage.OPERATION; }
    @Override public SubState subState() { return SubState.TIGHTENING_RECEIVED; }
    @Override public int priority() { return 0; }

    @Override
    public boolean precondition(MissionContext ctx) {
        if (ctx.getCurrentDeviceType() == DeviceType.SUDONG_X7) return false;
        return true;
    }

    @Override
    public CapabilityResult execute(MissionContext ctx) {
        TighteningData data = ctx.getCurrentOperationData();
        if (data == null) {
            log.warn("ReceiveData FAIL: no current operation data");
            return CapabilityResult.Fail;
        }
        if (data.getTighteningId() <= 0) {
            log.warn("ReceiveData FAIL: invalid tighteningId={}", data.getTighteningId());
            return CapabilityResult.Fail;
        }
        log.debug("ReceiveData PASS: tighteningId={}", data.getTighteningId());
        return CapabilityResult.Pass;
    }
}
