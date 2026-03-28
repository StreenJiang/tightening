package com.tightening.netty.protocol.handler.atlas;

import com.tightening.device.handler.impl.TCPDeviceHandler;
import com.tightening.netty.protocol.handler.DeviceInitHandler;

public class AtlasSeriesInitHandler extends DeviceInitHandler {
    public AtlasSeriesInitHandler(TCPDeviceHandler deviceHandler) {
        super(deviceHandler);
    }
}
