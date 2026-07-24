package com.tightening.device.type;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SetterSelector extends SubDevice {
    private Integer setterCount;
}
