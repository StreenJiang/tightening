package com.tightening.device.contract;

public record Coordinates3D(int x, int y, int z) {
    public static final Coordinates3D ZERO = new Coordinates3D(0, 0, 0);
}
