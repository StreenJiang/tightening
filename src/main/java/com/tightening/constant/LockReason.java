package com.tightening.constant;

import lombok.Getter;

@Getter
public enum LockReason {
    PSET_SENDING("pSetSending", "程序号下发中"),
    ARRANGER_POSITIONING("arrangerPositioning", "送钉中"),
    SOCKET_SELECTING("socketSelecting", "套筒选择中"),
    ADMIN_CONFIRM("adminConfirm", "需管理员确认");

    private final String key;
    private final String displayName;

    LockReason(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }
}
