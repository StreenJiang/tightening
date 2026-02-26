package com.tightening.device.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Setter
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class Arranger extends TCPDevice {
    @JsonProperty("switch_bar_code")
    private String switchBarCode;

    @JsonProperty("switch_position")
    private String switchPosition;
}
