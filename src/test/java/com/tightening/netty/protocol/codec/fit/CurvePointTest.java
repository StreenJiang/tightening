package com.tightening.netty.protocol.codec.fit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurvePointTest {

    @Test
    void constructAndGet() {
        CurvePoint point = new CurvePoint(0.1f, 5.0f, 10.0f);
        assertThat(point.getTime()).isEqualTo(0.1f);
        assertThat(point.getTorque()).isEqualTo(5.0f);
        assertThat(point.getAngle()).isEqualTo(10.0f);
    }

    @Test
    void setMethods() {
        CurvePoint point = new CurvePoint(0f, 0f, 0f);
        point.setTime(1.0f);
        point.setTorque(2.0f);
        point.setAngle(3.0f);
        assertThat(point.getTime()).isEqualTo(1.0f);
        assertThat(point.getTorque()).isEqualTo(2.0f);
        assertThat(point.getAngle()).isEqualTo(3.0f);
    }

    @Test
    void toString_containsFormattedValues() {
        CurvePoint point = new CurvePoint(0.5f, 10.5f, 45.0f);
        String result = point.toString();
        assertThat(result).contains("0.5000");
        assertThat(result).contains("10.50");
        assertThat(result).contains("45.00");
    }

    @Test
    void equalsAndHashCode() {
        CurvePoint point1 = new CurvePoint(0.1f, 5.0f, 10.0f);
        CurvePoint point2 = new CurvePoint(0.1f, 5.0f, 10.0f);
        assertThat(point1).isEqualTo(point2);
        assertThat(point1).hasSameHashCodeAs(point2);
    }
}
