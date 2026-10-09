package me.leleawa.rotatedworld.api;

/** config.yml，已经校验并限制过范围（见 Clojure 层的 {@code SettingsParser}）。 */
public record Settings(
        boolean enabledByDefault,
        int viewRadius,
        int recenterThreshold,
        int prefetchChunks,
        double fastSpeed,
        int fastEdgeDistance,
        int fastTargetMargin,
        int prefetchAheadChunks,
        int columnsPerTick,
        int chunkRequestsPerTick,
        boolean debug,
        boolean safeSpawnOnRespawn,
        boolean safeSpawnOnJoin,
        int safeSpawnRadius) {}
