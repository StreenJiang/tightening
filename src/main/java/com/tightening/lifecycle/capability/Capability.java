package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.MissionContext;

public interface Capability {
    String id();
    Stage stage();
    SubState subState();
    int priority();

    default boolean precondition(MissionContext ctx) {
        return true;
    }

    CapabilityResult execute(MissionContext ctx);

    default ErrorAction onError(MissionContext ctx, Exception e) {
        return ErrorAction.FAIL_STAGE;
    }
}
