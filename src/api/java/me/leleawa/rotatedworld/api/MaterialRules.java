package me.leleawa.rotatedworld.api;

/**
 * Flix 实现：方块变体之间的命名规则。全部是纯函数；返回 "" 表示没有这种变体。
 */
public interface MaterialRules {
    /** 立在地上的方块 -> 它的墙上变体 ({@code SOUL_TORCH -> SOUL_WALL_TORCH}). */
    String wallVariant(String material);

    /** 挂在墙上的方块 -> 它的地面变体 ({@code OAK_WALL_SIGN -> OAK_SIGN}). */
    String floorVariant(String material);

    /**
     * 方块状态类型（小写，例如 {@code red_bed}）-> 客户端渲染它需要的方块实体类型名（{@code bed}）。
     */
    String blockEntityType(String stateType);
}
