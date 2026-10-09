package me.leleawa.rotatedworld.api;

/** Flix（Datalog）实现：哪些缓存的服务端区块还有玩家需要。 */
public interface RetentionPolicy {
    /**
     * @param boxes 同一个世界里所有玩家的视野范围，展平成 {@code [minX, maxX, minZ, maxZ] * n}
     * @param xs    每个缓存区块的 x
     * @param zs    每个缓存区块的 z（和 {@code xs} 等长）
     * @return 每个缓存区块是否要保留
     */
    boolean[] retain(int[] boxes, int[] xs, int[] zs);
}
