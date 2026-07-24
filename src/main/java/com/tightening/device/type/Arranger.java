package com.tightening.device.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Arranger extends SubDevice {
    @JsonProperty("switch_bar_code")
    private String switchBarCode;

    @JsonProperty("switch_position")
    private String switchPosition;

    private Integer channelCount = 8;
    private Boolean reverseFirstFour;
}
