package me.leleawa.rotatedworld.api;

/** Fantom 实现：找安全位置的搜索策略。纯算法，对世界的了解全部来自 probe。 */
public interface SpotFinder {
    /** {@code radius} 格内最近的能站稳的位置（一圈一圈往外找），没有就返回 null。 */
    Spot standable(BlockProbe probe, double x, double y, double z, long radius);

    /** 身体在 (x, y, z) 没卡住时返回 null；否则返回最近的空位，找不到也返回 null。 */
    Spot unstuck(BlockProbe probe, double x, double y, double z);
}
