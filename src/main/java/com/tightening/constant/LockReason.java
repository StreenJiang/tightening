package com.tightening.constant;

import lombok.Getter;

@Getter
public enum LockReason {
    PSET_SENDING("pSetSending"),
    ARRANGER_POSITIONING("arrangerPositioning"),
    SOCKET_SELECTING("socketSelecting"),
    BARCODE_REQUIRED("barcodeRequired"),
    ADMIN_CONFIRM("adminConfirm");

    private final String key;

    LockReason(String key) {
        this.key = key;
    }
}
