package com.tightening.lifecycle;

/**
 * 锁消息。source 决定优先级：MANUAL_LOCK / MANUAL_UNLOCK 最高，覆盖其他所有来源。
 */
public record LockMessage(String source, String reason) {
    public boolean isManual() {
        return "MANUAL_LOCK".equals(source) || "MANUAL_UNLOCK".equals(source);
    }
}
