package com.tightening.lifecycle.capability;

import com.tightening.constant.Stage;
import com.tightening.constant.SubState;
import com.tightening.lifecycle.TaskContext;

public interface Capability {
    String id();
    Stage stage();
    SubState subState();
    int priority();

    default boolean precondition(TaskContext ctx) {
        return true;
    }

    CapabilityResult execute(TaskContext ctx);

    default ErrorAction onError(TaskContext ctx, Exception e) {
        return ErrorAction.FAIL_STAGE;
    }
}
