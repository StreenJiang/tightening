package com.tightening.netty.protocol.codec.fit;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 曲线数据样本实体类
 */
@Data
public class CurveDataSamples implements Serializable {

    private int tighteningId;
    private List<CurvePoint> points;

    public CurveDataSamples(int tighteningId) {
        this.tighteningId = tighteningId;
    }

    public int size() {
        return points != null ? points.size() : 0;
    }

    public void addPoint(CurvePoint point) {
        if (this.points == null) {
            this.points = new ArrayList<>();
        }
        this.points.add(point);
    }

    public void addPoint(Float time, Float torque, Float angle) {
        addPoint(new CurvePoint(time, torque, angle));
    }
}
