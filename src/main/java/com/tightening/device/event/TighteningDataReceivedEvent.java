package com.tightening.device.event;

import com.tightening.dto.TighteningDataDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TighteningDataReceivedEvent extends ApplicationEvent {
    private final long deviceId;
    private final TighteningDataDTO data;

    public TighteningDataReceivedEvent(Object source, long deviceId, TighteningDataDTO data) {
        super(source);
        this.deviceId = deviceId;
        this.data = data;
    }
}
