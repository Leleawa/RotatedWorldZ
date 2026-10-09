package me.leleawa.rotatedworld.api;

/** 服务端坐标里的一个位置（玩家脚底）。 */
public final class Spot {
    public final double x;
    public final double y;
    public final double z;

    public Spot(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public String toString() {
        return String.format("(%.1f, %.1f, %.1f)", x, y, z);
    }
}
