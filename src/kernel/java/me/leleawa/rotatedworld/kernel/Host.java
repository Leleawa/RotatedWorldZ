package me.leleawa.rotatedworld.kernel;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import me.leleawa.rotatedworld.api.Settings;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 插件提供给比它先编译的层（Scala 引擎、Groovy 命令……）的服务。由 Java 插件主类实现。
 */
public interface Host {
    Plugin plugin();

    Logger logger();

    Settings settings();

    /** 重新读取 config.yml。 */
    void reloadSettings();

    /** 构建旋转区块用的线程池。 */
    Executor worker();

    boolean isEnabled(UUID player);

    /** 玩家线程：只取已存在的状态。 */
    PlayerState stateOf(Player player);

    Collection<PlayerState> allStates();

    /** 等着找落脚点的玩家（重生 / 进服后），在玩家自己的 tick 里处理。 */
    Set<UUID> pendingSafeSpawn();

    /** 被旋转的在线玩家的实体 id（别人看他们时也要用同样的碰撞箱映射）。 */
    boolean isRotatedEntity(int entityId);

    /** 客户端当前追踪的所有实体按新 offset 重发一次位置。在 netty 事件循环里调用。 */
    void resyncEntities(PlayerState state);

    /** 身体卡在方块里就传送到附近的空位。玩家线程。返回是否传送了。 */
    boolean moveToSafeSpot(Player player);

    /** 找一个旋转视角下能站稳的位置并传送过去。玩家线程。返回是否传送了。 */
    boolean moveToStandableSpot(Player player);

    /** 切换玩家的旋转状态（状态、碰撞箱缩放）。调用方负责让客户端重载世界。 */
    void applyRotation(Player player, boolean enable);

    /** 缓存的旋转区块数量。 */
    int cachedChunks();

    /** /rotate 的参数语法（Prolog 脚本）。 */
    me.leleawa.rotatedworld.api.ScriptedRules.CommandGrammar commandGrammar();

    /** /rotate status 的输出格式（BeanShell 脚本）。 */
    me.leleawa.rotatedworld.api.ScriptedRules.StatusLine statusLine();
}
