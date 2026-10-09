package me.leleawa.rotatedworld

import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity
import io.papermc.paper.event.player.PlayerFailMoveEvent
import me.leleawa.rotatedworld.net.Mapping
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.abs

/**
 * Bukkit 事件：服务端按没旋转的世界做的各种检查（移动、飞行、摔落、击退），对旋转玩家逐个放行或改写。
 * 普通玩家完全不受影响。
 */
class PlayerListener(private val plugin: RotatedWorldPlugin) : Listener {

    private val settings get() = plugin.settings()
    private val scaleKey = NamespacedKey(plugin, "scale")

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val p = e.player
        if (plugin.isEnabled(p.uniqueId)) {
            applyScale(p)
            plugin.rotatedEntityIds.add(p.entityId)
            if (settings.safeSpawnOnJoin()) plugin.pendingSafeSpawn().add(p.uniqueId)
        } else {
            removeScale(p)
        }
        // 不管当前开没开旋转都挂上，没开的时候 tick 里直接跳过（/rotate 可以随时切换）
        plugin.viewManager.start(p)
    }

    /**
     * 服务端按没旋转的地形模拟移动，对旋转玩家放行哪些失败由 Common Lisp 决定（rules.lisp），
     * 要不要按卡顿放行"移动过快"由 JavaScript 决定（rules.js）。普通玩家完全不受影响。
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onFailMove(e: PlayerFailMoveEvent) {
        val st = plugin.stateOf(e.player) ?: return
        when (plugin.scripts.eventPolicy().failedMove(e.failReason.name)) {
            "allow" -> {
                e.isAllowed = true
                e.logWarning = false
            }
            "ask-javascript" -> {
                // 是不是卡顿造成的，由 JavaScript 判断（rules.js）
                val elapsed = System.nanoTime() - st.lastAcceptedMoveNanos
                val from = e.from
                val to = e.to
                if (plugin.scripts.moveAllowance().allowTooQuick(elapsed, to.x - from.x, to.y - from.y, to.z - from.z)) {
                    e.isAllowed = true
                    e.logWarning = false
                    if (settings.debug()) plugin.logger.info("${e.player.name} 移动过快已放行（卡顿 %.0f ms，竖直 %.1f 格）".format(elapsed / 1e6, abs(to.z - from.z)))
                }
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(e: PlayerMoveEvent) {
        plugin.stateOf(e.player)?.lastAcceptedMoveNanos = System.nanoTime()
    }

    /**
     * 旋转玩家站在客户端的地面上时，服务端看他是贴着墙悬空的，会被当成飞行踢出。
     * 只对旋转玩家取消，取消哪些原因由 Common Lisp 决定（rules.lisp），服务器不需要全局开 allow-flight。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onKick(e: PlayerKickEvent) {
        if (plugin.stateOf(e.player) != null && plugin.scripts.eventPolicy().forgiveKick(e.cause.name)) e.isCancelled = true
    }

    @EventHandler
    fun onRespawn(e: PlayerRespawnEvent) {
        if (settings.safeSpawnOnRespawn() && plugin.isEnabled(e.player.uniqueId)) plugin.pendingSafeSpawn().add(e.player.uniqueId)
        if (settings.debug()) plugin.logger.info("PlayerRespawnEvent: ${e.player.name}")
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        val p = e.player
        plugin.pendingSafeSpawn().remove(p.uniqueId)
        removeScale(p)
        plugin.rotatedEntityIds.remove(p.entityId)
        plugin.forget(p.uniqueId)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDamage(e: EntityDamageEvent) {
        if (e.cause != EntityDamageEvent.DamageCause.FALL) return
        val p = e.entity as? Player ?: return
        val st = plugin.stateOf(p) ?: return
        // vanilla 按服务端 y 算摔落，而服务端 y 在客户端里是水平方向，所以全部取消，自己算
        if (!st.allowFallDamage) e.isCancelled = true
    }

    /**
     * 服务端里玩家的速度是过期的（侧着走会被误判成起跳，留下 y=0.42），受伤时会被同步给客户端。
     *  - 有击退的伤害：在击退计算之前把速度清零，击退只剩攻击方向
     *  - 无击退的伤害：丢掉接下来发给自己的速度包
     * 伤害事件在原版计算击退之前触发，所以这里清零来得及。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamageVelocity(e: EntityDamageEvent) {
        val p = e.entity as? Player ?: return
        val st = plugin.stateOf(p) ?: return
        // 服务端算出来的速度（过期值 + 服务端坐标系里的击退）全部丢掉
        st.dropSelfVelocityUntil = System.currentTimeMillis() + 200
        p.velocity = Vector()

        // 被实体打：在客户端坐标系里重新算击退，由 Python 算（rules.py）
        val damager = (e as? EntityDamageByEntityEvent)?.damager ?: return
        val off = st.outOff
        val a = damager.location
        val v = p.location
        val ca = if (plugin.isRotatedEntity(damager.entityId)) Mapping.selfToClient(a.x, a.y, a.z, off)
            else Mapping.pointToClient(a.x, a.y, a.z, off)
        val cv = Mapping.selfToClient(v.x, v.y, v.z, off)
        val k = plugin.scripts.knockback().velocity(cv.x, cv.z, ca.x, ca.z, Math.random(), Math.random())
        val vel = Vector3d(k[0], k[1], k[2])
        val selfId = p.entityId
        st.runInEventLoop { st.user.sendPacketSilently(WrapperPlayServerEntityVelocity(selfId, vel)) }
    }

    /** netty 线程按客户端高度算出的摔落，回到玩家线程上造成伤害。伤害多少由九种语言组成的陪审团投票决定。 */
    fun scheduleFallDamage(uuid: UUID, fall: Double) {
        val p = Bukkit.getPlayer(uuid) ?: return
        p.scheduler.run(plugin, { _ ->
            val st = plugin.stateOf(p) ?: return@run
            val dmg = plugin.scripts.fallJury().verdict(
                fall, p.gameMode == GameMode.CREATIVE, p.gameMode == GameMode.SPECTATOR,
                p.isFlying, p.isGliding, p.isInWater, p.hasPotionEffect(PotionEffectType.SLOW_FALLING),
            )
            if (dmg <= 0) return@run
            st.allowFallDamage = true
            try {
                p.damage(dmg, DamageSource.builder(DamageType.FALL).build())
            } finally {
                st.allowFallDamage = false
            }
        }, null)
    }

    // ---------------------------------------------------------------- 碰撞箱缩放

    fun applyScale(p: Player) {
        val inst = p.getAttribute(Attribute.SCALE) ?: return
        inst.modifiers.filter { it.key == scaleKey }.forEach { inst.removeModifier(it) }
        inst.addModifier(AttributeModifier(scaleKey, Mapping.SCALE - 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY))
    }

    fun removeScale(p: Player) {
        val inst = p.getAttribute(Attribute.SCALE) ?: return
        inst.modifiers.filter { it.key == scaleKey }.forEach { inst.removeModifier(it) }
    }
}
