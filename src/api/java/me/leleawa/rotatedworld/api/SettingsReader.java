package me.leleawa.rotatedworld.api;

import java.util.Map;

/** Clojure 实现：把原始配置值变成 {@link Settings}。 */
public interface SettingsReader {
    /**
     * @param values             {@code FileConfiguration.getValues(true)}：带点的键（"safe-spawn.radius"）到值
     * @param serverViewDistance 服务端的 view-distance，客户端视野半径不能超过它
     */
    Settings read(Map<String, Object> values, int serverViewDistance);
}
