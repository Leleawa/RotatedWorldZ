package me.leleawa.rotatedworld.api;

/** {@link SpotFinder} 需要的只读世界视图。Gosu 在 Bukkit 之上实现。 */
public interface BlockProbe {
    /**
     * 方块 (bx, by, bz) 能不能当旋转玩家的地面（客户端的地面是服务端方块的南面）。
     * 能的话返回站在上面时的脚底坐标，否则 null。
     */
    Spot standableOn(long bx, long by, long bz);

    /** 脚底在这个位置时，旋转玩家的身体（服务端里是一个躺着的长方体）是不是在空处。 */
    boolean bodyFree(double x, double y, double z);

    long maxHeight();
}
