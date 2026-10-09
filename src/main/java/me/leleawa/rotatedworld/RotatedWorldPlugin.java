package me.leleawa.rotatedworld;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.biome.Biomes;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import me.leleawa.rotatedworld.api.ScriptedRules;
import me.leleawa.rotatedworld.api.Services;
import me.leleawa.rotatedworld.api.Settings;
import me.leleawa.rotatedworld.api.SettingsReader;
import me.leleawa.rotatedworld.api.Spot;
import me.leleawa.rotatedworld.blocks.BlockRotation;
import me.leleawa.rotatedworld.command.RotateCommand;
import me.leleawa.rotatedworld.engine.ChunkStore;
import me.leleawa.rotatedworld.engine.ViewManager;
import me.leleawa.rotatedworld.janitor.CacheJanitor;
import me.leleawa.rotatedworld.janitor.KawaJuror;
import me.leleawa.rotatedworld.kernel.BlockRotator;
import me.leleawa.rotatedworld.kernel.Host;
import me.leleawa.rotatedworld.kernel.PlayerState;
import me.leleawa.rotatedworld.kernel.PluginContext;
import me.leleawa.rotatedworld.net.PacketHandler;
import me.leleawa.rotatedworld.probe.WorldProbe;
import me.leleawa.rotatedworld.script.ScriptLayers;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 插件入口：启动各语言层并把它们接起来，同时作为 {@link Host} 给下层提供服务。
 *
 * <pre>
 *  Frege   坐标代数（旋转的唯一定义 + QuickCheck 性质）
 *  Flix    方块命名规则、缓存回收（Datalog）
 *  Clojure 配置解析（数据驱动的 schema）
 *  Haxe    光照 nibble 数组的旋转（可移植的纯算法）
 *  Fantom  安全位置的搜索策略
 *  Scala 3 区块缓存与视野引擎（性能核心）
 *  Gosu    世界探测（Bukkit 方块 / 碰撞箱）
 *  Groovy  /rotate 命令
 *  Kawa    缓存清理（Scheme）
 *  Xtend   BlockData 旋转规则
 *  Kotlin  数据包改写（netty）、Bukkit 事件
 *  Java    api / kernel 契约、插件入口
 *
 *  运行时加载的脚本层（见 ScriptLayers）：
 *  Kotlin Script 重新定位决策 / JRuby 区块柱发送顺序 / JavaScript 卡顿放行 / Jython 击退 / Lua 摔落伤害 /
 *  BeanShell status 输出 / Common Lisp 检查放行策略 / Prolog 命令语法，
 *  以及八个脚本加 Kawa 一起组成的摔落伤害陪审团
 * </pre>
 */
public final class RotatedWorldPlugin extends JavaPlugin implements Host {
    private volatile Settings settings;
    private SettingsReader settingsReader;
    private BlockRotator rotation;
    private ChunkStore store;
    private PacketHandler packetHandler;
    private ViewManager viewManager;
    private PlayerListener listener;
    private ScriptLayers.Scripts scripts;
    private ExecutorService worker;
    private ExecutorService encoder;

    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> overrides = new ConcurrentHashMap<>();
    private final Set<UUID> pendingSafeSpawn = ConcurrentHashMap.newKeySet();
    private final Set<Integer> rotatedEntityIds = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 纯计算层：Flix 和 Fantom 把自己的实现交给 Services，Clojure 是一个普通的 Java 类
        FlixBridge.start();
        FantomBridge.start(getDataFolder().toPath());
        settingsReader = ClojureBridge.settingsReader();
        loadSettings();
        scripts = ScriptLayers.load(getLogger(), getDataFolder().toPath(), java.util.List.of(new KawaJuror()));

        rotation = new BlockRotation(Services.materialRules());
        store = new ChunkStore(rotation);
        worker = pool("builder", Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 4));
        encoder = pool("encoder", 2);
        packetHandler = new PacketHandler(this);
        viewManager = new ViewManager(this, store, scripts.recenter(), scripts.spiral());
        listener = new PlayerListener(this);

        PacketEvents.getAPI().getEventManager().registerListener(packetHandler);
        getServer().getPluginManager().registerEvents(listener, this);
        RotateCommand rotate = new RotateCommand(this);
        getCommand("rotate").setExecutor((sender, command, label, args) ->
                PluginContext.call(() -> rotate.onCommand(sender, command, label, args)));
        // 清掉没有玩家需要的缓存：Kawa 收集视野范围，Flix 的 Datalog 决定留哪些
        Runnable janitor = janitorTask(this, store, Services.retentionPolicy());
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, t -> janitor.run(), 100L, 100L);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isEnabled(p.getUniqueId())) {
                p.getScheduler().run(this, t -> p.kick(Component.text("RotatedWorld 已加载，请重新进入服务器")), null);
            }
        }
        getLogger().info("RotatedWorld 已启用（默认" + (settings.enabledByDefault() ? "开启" : "关闭") + "旋转）");
    }

    @Override
    public void onDisable() {
        if (packetHandler != null) PacketEvents.getAPI().getEventManager().unregisterListener(packetHandler);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!states.containsKey(p.getUniqueId())) continue;
            try {
                listener.removeScale(p);
                p.kick(Component.text("RotatedWorld 已卸载，请重新进入服务器"));
            } catch (Throwable t) {
                // Folia 上关服时不一定在玩家所在的线程，踢不掉就算了，modifier 下次进服会清掉
            }
        }
        states.clear();
        if (worker != null) worker.shutdownNow();
        if (encoder != null) encoder.shutdownNow();
    }

    /**
     * Kawa 的缓存清理，包装成调度器任务。Kawa 的运行时按名字找自己的类时用线程上下文类加载器，
     * 而全局调度线程的是服务器的（看不见插件），所以每次都先换成插件的。
     */
    static Runnable janitorTask(Host host, ChunkStore store, me.leleawa.rotatedworld.api.RetentionPolicy retention) {
        return PluginContext.wrap(new CacheJanitor(host, store, retention));
    }

    private static ExecutorService pool(String name, int threads) {
        AtomicInteger index = new AtomicInteger();
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "RotatedWorld-" + name + "-" + index.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    private void loadSettings() {
        reloadConfig();
        settings = ClojureBridge.read(settingsReader, getConfig().getValues(true), Bukkit.getViewDistance());
    }

    // ---------------------------------------------------------------- 给 Kotlin 层用的

    public BlockRotator getRotation() {
        return rotation;
    }

    public ChunkStore getStore() {
        return store;
    }

    public ViewManager getViewManager() {
        return viewManager;
    }

    public PlayerListener getListener() {
        return listener;
    }

    public ScriptLayers.Scripts getScripts() {
        return scripts;
    }

    public Set<Integer> getRotatedEntityIds() {
        return rotatedEntityIds;
    }

    /** netty 线程：取（必要时创建）状态。 */
    public PlayerState stateOf(User user) {
        UUID uuid = user.getUUID();
        if (uuid == null || !isEnabled(uuid)) return null;
        PlayerState existing = states.get(uuid);
        if (existing != null) return existing;
        return states.computeIfAbsent(uuid, id -> {
            rotation.useBiomeRegistry(user.getRegistryOr(Biomes.getRegistry(), user.getClientVersion()));
            store.resetAirSection();
            return new PlayerState(id, user, encoder);
        });
    }

    /** 玩家下线。 */
    public void forget(UUID player) {
        states.remove(player);
    }

    // ---------------------------------------------------------------- Host

    @Override
    public Plugin plugin() {
        return this;
    }

    @Override
    public java.util.logging.Logger logger() {
        return getLogger();
    }

    @Override
    public Settings settings() {
        return settings;
    }

    @Override
    public void reloadSettings() {
        loadSettings();
    }

    @Override
    public Executor worker() {
        return worker;
    }

    @Override
    public boolean isEnabled(UUID player) {
        Boolean override = overrides.get(player);
        return override != null ? override : settings.enabledByDefault();
    }

    @Override
    public PlayerState stateOf(Player player) {
        return isEnabled(player.getUniqueId()) ? states.get(player.getUniqueId()) : null;
    }

    @Override
    public Collection<PlayerState> allStates() {
        return states.values();
    }

    @Override
    public Set<UUID> pendingSafeSpawn() {
        return pendingSafeSpawn;
    }

    @Override
    public boolean isRotatedEntity(int entityId) {
        return rotatedEntityIds.contains(entityId);
    }

    @Override
    public void resyncEntities(PlayerState state) {
        packetHandler.resyncEntities(state);
    }

    @Override
    public int cachedChunks() {
        return store.size();
    }

    @Override
    public ScriptedRules.CommandGrammar commandGrammar() {
        return scripts.commandGrammar();
    }

    @Override
    public ScriptedRules.StatusLine statusLine() {
        return scripts.statusLine();
    }

    @Override
    public void applyRotation(Player p, boolean enable) {
        overrides.put(p.getUniqueId(), enable);
        if (enable) {
            listener.applyScale(p);
            rotatedEntityIds.add(p.getEntityId());
        } else {
            listener.removeScale(p);
            rotatedEntityIds.remove(p.getEntityId());
            states.remove(p.getUniqueId());
        }
    }

    // ---------------------------------------------------------------- 防卡方块：Gosu 看世界，Fantom 决定去哪

    @Override
    public boolean moveToStandableSpot(Player p) {
        return PluginContext.call(() -> standable(p));
    }

    private boolean standable(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) return false;
        Location loc = p.getLocation();
        int radius = settings.safeSpawnRadius();
        Spot spot = Services.spotFinder().standable(new WorldProbe(p.getWorld()), loc.getX(), loc.getY(), loc.getZ(), radius);
        if (spot == null) {
            if (settings.debug()) getLogger().info(p.getName() + " 附近 " + radius + " 格内找不到能站稳的位置");
            return unstuck(p);
        }
        teleport(p, loc, spot);
        if (settings.debug()) getLogger().info(p.getName() + " 旋转视角出生点：" + spot);
        return true;
    }

    // Gosu（世界探测）和 Fantom（搜索策略）的运行时都在玩家线程上被调用
    @Override
    public boolean moveToSafeSpot(Player p) {
        return PluginContext.call(() -> unstuck(p));
    }

    private boolean unstuck(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) return false;
        Location loc = p.getLocation();
        Spot spot = Services.spotFinder().unstuck(new WorldProbe(p.getWorld()), loc.getX(), loc.getY(), loc.getZ());
        if (spot == null) return false;
        teleport(p, loc, spot);
        if (settings.debug()) getLogger().info(p.getName() + " 卡在方块里，移到 " + spot);
        return true;
    }

    private static void teleport(Player p, Location from, Spot spot) {
        p.teleportAsync(new Location(from.getWorld(), spot.x, spot.y, spot.z, from.getYaw(), from.getPitch()));
    }
}
