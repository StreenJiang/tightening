package com.tightening.util;

import com.tightening.entity.BarCodeMatchingRule;

public final class BarcodeMatcher {

    private BarcodeMatcher() {}

    /**
     * 对条码做位置匹配。
     * keyStartPosition/keyEndPosition 均未配置 → 无位置约束，放行；
     * expectedLength 非空 → 先检查长度。
     */
    public static boolean matches(BarCodeMatchingRule rule, String code) {
        if (code == null) return false;
        if (rule.getExpectedLength() != null && code.length() != rule.getExpectedLength()) return false;
        if (rule.getKeyStartPosition() == null || rule.getKeyEndPosition() == null) return true;
        int end = Math.min(rule.getKeyEndPosition(), code.length());
        int start = Math.min(rule.getKeyStartPosition(), end);
        return code.substring(start, end).equals(rule.getKeyChar());
    }
}
