package com.tightening.netty.protocol.codec.fit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurveDataSamplesTest {

    @Test
    void construct() {
        CurveDataSamples samples = new CurveDataSamples(42);
        assertThat(samples.getTighteningId()).isEqualTo(42);
        assertThat(samples.size()).isZero();
    }

    @Test
    void addPoint_curvePoint() {
        CurveDataSamples samples = new CurveDataSamples(1);
        samples.addPoint(new CurvePoint(0.1f, 5f, 10f));
        assertThat(samples.size()).isEqualTo(1);
        assertThat(samples.getPoints()).hasSize(1);
    }

    @Test
    void addPoint_floatOverload() {
        CurveDataSamples samples = new CurveDataSamples(2);
        samples.addPoint(0.2f, 15f, 90f);
        assertThat(samples.size()).isEqualTo(1);
        assertThat(samples.getPoints().getFirst().getTorque()).isEqualTo(15f);
    }

    @Test
    void addMultiplePoints() {
        CurveDataSamples samples = new CurveDataSamples(3);
        samples.addPoint(0.1f, 5f, 10f);
        samples.addPoint(0.2f, 15f, 90f);
        samples.addPoint(0.3f, 25f, 180f);
        assertThat(samples.size()).isEqualTo(3);
        assertThat(samples.getPoints().getFirst().getTime()).isEqualTo(0.1f);
        assertThat(samples.getPoints().get(1).getTime()).isEqualTo(0.2f);
        assertThat(samples.getPoints().getLast().getTime()).isEqualTo(0.3f);
    }

    @Test
    void size_whenNoPoints() {
        CurveDataSamples samples = new CurveDataSamples(0);
        assertThat(samples.size()).isZero();
    }
}
