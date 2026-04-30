package com.tightening.netty.protocol.codec.fit;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CurvePoint {
    private Float time;
    private Float torque;
    private Float angle;

    @Override
    public String toString() {
        return String.format("CurvePoint[time=%.4f, torque=%.2f, angle=%.2f]", time, torque, angle);
    }
}
