package me.leleawa.rotatedworld.kernel;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一个被旋转玩家的状态。会被三个线程访问：
 *  - netty 事件循环：改写数据包；outOff / generation 只在这里修改，保证和线上的包顺序一致
 *  - 玩家的调度器线程（Paper 上是主线程，Folia 上是玩家所在区域的线程）：区块柱的发送和卸载、浮动原点的重新定位
 *  - 构建线程：读取缓存
 *
 * <p>字段是 public 的：这是 Kotlin 数据包层和 Scala 视野引擎之间共享的一份记录。
 */
public final class PlayerState {
    /** 服务端的传送 id 从 0 往上递增，假传送用一段负数，不会冲突。 */
    public static final int FAKE_ID_BASE = -1_000_000;

    public final UUID uuid;
    public final User user;
    public final Executor encoder;
    /** 编码队列：区块包在这里序列化（不占 netty 线程），并保证同一个玩家的发包顺序。 */
    public final SerialExecutor serial;

    /** 服务端 -> 客户端方向使用的 offset。只在 netty 事件循环里修改。 */
    public volatile int outOff;
    /** 客户端 -> 服务端方向使用的 offset。客户端确认对应的传送之后才切换。 */
    public volatile int inOff;
    /** 每次 outOff 变化或者换世界都 +1，用来丢弃按旧 offset 组装好的区块包。 */
    public volatile int generation;
    /** 小于这个 generation 的区块包在编码队列里直接丢掉（重新定位时设置，不用再编码注定作废的包）。 */
    public volatile int minValidGen;

    public volatile boolean initialized;
    /** 进服 / 换世界后要检查一次客户端身体有没有卡在方块里。 */
    public volatile boolean needsSafeSpot;
    public int safeSpawnWaitTicks;
    public volatile String worldKey;
    public volatile int minY = -64;
    public volatile int maxY = 320;

    /** 传送 id -> 客户端确认后要切换到的 inOff。 */
    public final Map<Integer, Integer> pendingOffsets = new ConcurrentHashMap<>();
    private final AtomicInteger fakeTeleportIds = new AtomicInteger(FAKE_ID_BASE);

    /** netty 线程设置，主线程下一 tick 清空已发送区块柱并重发。 */
    public volatile boolean resetColumns = true;
    /** 主线程发起了重新定位，等 netty 线程真正执行之前主线程不要再发区块。 */
    public volatile boolean recenterInFlight;

    // 以下只在玩家自己的调度器线程上访问（Folia 上玩家换区域时线程会变，所以用并发容器）
    public final Set<Long> sentColumns = ConcurrentHashMap.newKeySet();
    /** 本 tick 还能请求多少个服务端区块。 */
    public int requestBudget;
    /** 最近一次 tick 的视野（给全局清理任务用）。 */
    public volatile int viewX = Integer.MIN_VALUE;
    public volatile int viewZBase;
    public int centerX = Integer.MIN_VALUE;
    public int centerZ = Integer.MIN_VALUE;
    /**
     * 客户端竖直方向（= 服务端 z）的速度，格/包（客户端每 tick 发一个包），平滑过。
     * 用客户端的移动包算，不用服务端 tick 采样：Folia 的区域 tick 不均匀，采样会抖。
     */
    public volatile double verticalSpeed;
    public double lastPacketZ = Double.NaN;
    /** 服务端最近一次接受这个玩家移动的时间（PlayerMoveEvent），用来判断"移动过快"是不是卡顿造成的。 */
    public volatile long lastAcceptedMoveNanos = System.nanoTime();
    /** 高速状态再保持多少 tick（迟滞，避免下落途中在高速 / 慢速之间来回切）。 */
    public int fastTicks;
    public int fastDir;

    /** 客户端当前追踪的实体（服务端坐标 x, y, z, yaw, pitch），重新定位时要按新 offset 全部重发。 */
    public final Map<Integer, double[]> entities = new ConcurrentHashMap<>();

    // 当前的服务端视角，用于换算相对旋转的传送
    public volatile float serverYaw;
    public volatile float serverPitch;

    // 摔落伤害（netty 线程），高度用服务端 z，和 offset 无关
    public double fallDistance;
    public double lastHeight = Double.NaN;
    public volatile boolean flying;
    public volatile boolean allowFallDamage;

    /**
     * 服务端把"侧着走"误判成起跳，会在服务端速度里留下 y=0.42，
     * 受伤时这个过期速度会被同步给客户端，旋转后变成往旁边弹。
     * 无击退的伤害之后一小段时间内丢掉发给自己的速度包。
     */
    public volatile long dropSelfVelocityUntil;

    public PlayerState(UUID uuid, User user, Executor encoder) {
        this.uuid = uuid;
        this.user = user;
        this.encoder = encoder;
        this.serial = new SerialExecutor(encoder);
    }

    public int nextFakeTeleportId() {
        return fakeTeleportIds.decrementAndGet();
    }

    public boolean isFakeTeleport(int id) {
        return id <= FAKE_ID_BASE;
    }

    /** 客户端高度窗口的中心。 */
    public int center() {
        return (minY + maxY) / 2;
    }

    public void runInEventLoop(Runnable task) {
        ChannelHelper.runInEventLoop(user.getChannel(), task);
    }

    /** 在当前线程把包序列化成 ByteBuf。 */
    public Object[] encode(PacketWrapper<?> packet) {
        return PacketEvents.getAPI().getProtocolManager().transformWrappers(packet, user.getChannel(), true);
    }

    public void sendEncoded(Object[] bufs) {
        PacketEvents.getAPI().getProtocolManager().sendPacketsSilently(user.getChannel(), bufs);
    }

    /**
     * 按提交顺序发送：先在编码线程序列化，再交给 netty 线程写出。
     * gen 不为 null 时，如果 generation 已经变了（offset 换过）就丢弃。
     */
    public void sendOrdered(Integer gen, List<? extends PacketWrapper<?>> packets) {
        serial.execute(() -> {
            if (gen != null && (generation != gen || gen < minValidGen)) return;
            List<Object> bufs = new ArrayList<>();
            for (PacketWrapper<?> p : packets) Collections.addAll(bufs, encode(p));
            runInEventLoop(() -> {
                if (gen != null && generation != gen) {
                    bufs.forEach(ByteBufHelper::release);
                } else {
                    sendEncoded(bufs.toArray());
                }
            });
        });
    }

    /** 换世界 / 重生：客户端会清空所有区块和实体。在 netty 线程调用。 */
    public void resetForNewWorld(String worldKey) {
        this.worldKey = worldKey;
        initialized = false;
        entities.clear();
        pendingOffsets.clear();
        generation++;
        resetColumns = true;
        recenterInFlight = false;
        fallDistance = 0.0;
        lastHeight = Double.NaN;
    }
}
