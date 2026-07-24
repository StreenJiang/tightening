package com.tightening.device.event;

import com.tightening.dto.CurveDataDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CurveDataSavedEvent extends ApplicationEvent {
    private final CurveDataDTO data;

    public CurveDataSavedEvent(Object source, CurveDataDTO data) {
        super(source);
        this.data = data;
    }
}
