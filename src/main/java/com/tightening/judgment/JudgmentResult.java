package com.tightening.judgment;

public record JudgmentResult(boolean isOk, String reason) {
    public static JudgmentResult ok() {
        return new JudgmentResult(true, "OK");
    }

    public static JudgmentResult ng(String reason) {
        return new JudgmentResult(false, reason);
    }
}
