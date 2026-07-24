package com.tightening.lifecycle;

@FunctionalInterface
public interface PipelineEventListener {
    void onEvent(String type, Object data);
}
