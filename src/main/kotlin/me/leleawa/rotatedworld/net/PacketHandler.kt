package me.leleawa.rotatedworld.net

import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.player.DiggingAction
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3i
import com.github.retrooper.packetevents.wrapper.play.client.*
import com.github.retrooper.packetevents.wrapper.play.server.*
import me.leleawa.rotatedworld.RotatedWorldPlugin
import me.leleawa.rotatedworld.kernel.PlayerState
import kotlin.math.abs

/**
 * 数据包改写。
 *  - 发送：服务端坐标 -> 客户端坐标；服务端自己的区块包全部拦掉，由 ViewManager 发旋转后的区块柱
 *  - 接收：客户端坐标 -> 服务端坐标
 */
class PacketHandler(private val plugin: RotatedWorldPlugin) : PacketListenerAbstract(PacketListenerPriority.HIGH) {

    private val rot get() = plugin.rotation
    private val store get() = plugin.store

    // ================================================================ 服务端 -> 客户端

    override fun onPacketSend(event: PacketSendEvent) {
        if (event.packetType !is PacketType.Play.Server) return
        // 所有观察者（包括没开旋转的）都要过滤：被旋转玩家的服务端碰撞箱缩到了 1/3，
        // 但别人看到的他要保持正常大小
        if (event.packetType == PacketType.Play.Server.UPDATE_ATTRIBUTES) {
            filterScale(event)
            if (event.isCancelled) return
        }
        val st = plugin.stateOf(event.user) ?: return
        when (event.packetType) {
            PacketType.Play.Server.JOIN_GAME -> st.resetForNewWorld(WrapperPlayServerJoinGame(event).worldName)
            PacketType.Play.Server.RESPAWN -> st.resetForNewWorld(WrapperPlayServerRespawn(event).worldName.orElse(null))

            PacketType.Play.Server.PLAYER_POSITION_AND_LOOK -> onServerTeleport(event, st)

            // 区块：全部由我们自己发
            PacketType.Play.Server.CHUNK_DATA -> {
                event.isCancelled = true
                val world = st.worldKey ?: return
                val w = WrapperPlayServerChunkData(event)
                val minY = event.user.minWorldHeight
                val column = w.column
                val light = w.lightData
                plugin.worker().execute {
                    val built = store.buildFromPacket(column, light, minY)
                    store.put(world, column.x, column.z, built, true)
                    plugin.viewManager.onPacketChunkBuilt(st, world, column.x, column.z, built)
                }
            }
            PacketType.Play.Server.UPDATE_LIGHT -> {
                event.isCancelled = true
                val world = st.worldKey ?: return
                val w = WrapperPlayServerUpdateLight(event)
                val light = w.lightData
                plugin.worker().execute {
                    store.get(world, w.x, w.z)?.let { store.rotateLightInto(it, light) }
                }
            }
            PacketType.Play.Server.UNLOAD_CHUNK,
            PacketType.Play.Server.UPDATE_VIEW_POSITION,
            PacketType.Play.Server.CHUNK_BIOMES -> event.isCancelled = true

            // 方块
            PacketType.Play.Server.BLOCK_CHANGE -> {
                val w = WrapperPlayServerBlockChange(event)
                val p = w.blockPosition
                st.worldKey?.let { store.applyBlockChange(it, p.x, p.y, p.z, st.minY, w.blockId) }
                w.blockPosition = Mapping.blockToClient(p, st.outOff)
                w.setBlockID(rot.rotateId(w.blockId))
                event.markForReEncode(true)
            }
            PacketType.Play.Server.MULTI_BLOCK_CHANGE -> {
                val w = WrapperPlayServerMultiBlockChange(event)
                val off = st.outOff
                val sec = w.chunkPosition
                val world = st.worldKey
                w.blocks = w.blocks.map { b ->
                    if (world != null) store.applyBlockChange(world, b.x, b.y, b.z, st.minY, b.blockId)
                    val c = Mapping.blockToClient(b.x, b.y, b.z, off)
                    WrapperPlayServerMultiBlockChange.EncodedBlock(rot.rotateId(b.blockId), c.x, c.y, c.z)
                }.toTypedArray()
                // 一个服务端 section 正好整个落在一个客户端 section 里
                w.chunkPosition = Vector3i(sec.x, Math.floorDiv(sec.z * 16 + off, 16), -sec.y - 1)
                event.markForReEncode(true)
            }
            PacketType.Play.Server.BLOCK_ENTITY_DATA -> {
                val w = WrapperPlayServerBlockEntityData(event)
                val p = w.position
                st.worldKey?.let { store.applyBlockEntity(it, p.x, p.y, p.z, st.minY, w.type, w.nbt) }
                w.position = Mapping.blockToClient(p, st.outOff)
                event.markForReEncode(true)
            }
            PacketType.Play.Server.BLOCK_ACTION -> {
                val w = WrapperPlayServerBlockAction(event)
                w.blockPosition = Mapping.blockToClient(w.blockPosition, st.outOff)
                event.markForReEncode(true)
            }
            PacketType.Play.Server.BLOCK_BREAK_ANIMATION -> {
                val w = WrapperPlayServerBlockBreakAnimation(event)
                w.blockPosition = Mapping.blockToClient(w.blockPosition, st.outOff)
                event.markForReEncode(true)
            }
            PacketType.Play.Server.EFFECT -> {
                val w = WrapperPlayServerEffect(event)
                w.position = Mapping.blockToClient(w.position, st.outOff)
                event.markForReEncode(true)
            }
            PacketType.Play.Server.SPAWN_POSITION -> {
                val w = WrapperPlayServerSpawnPosition(event)
                w.position = Mapping.blockToClient(w.position, st.outOff)
                event.markForReEncode(true)
            }

            // 声音 / 粒子 / 爆炸
            PacketType.Play.Server.SOUND_EFFECT -> {
                val w = WrapperPlayServerSoundEffect(event)
                val p = w.effectPosition // 坐标 * 8
                w.effectPosition = Vector3i(p.x, p.z + st.outOff * 8, -p.y)
                event.markForReEncode(true)
            }
            PacketType.Play.Server.PARTICLE -> {
                val w = WrapperPlayServerParticle(event)
                w.position = Mapping.pointToClient(w.position, st.outOff)
                w.offset = Mapping.vecToClientF(w.offset)
                event.markForReEncode(true)
            }
            PacketType.Play.Server.EXPLOSION -> {
                val w = WrapperPlayServerExplosion(event)
                w.position = Mapping.pointToClient(w.position, st.outOff)
                w.knockback?.let { w.knockback = Mapping.vecToClient(it) }
                event.markForReEncode(true)
            }

            // 实体
            PacketType.Play.Server.SPAWN_ENTITY -> {
                val w = WrapperPlayServerSpawnEntity(event)
                val p = w.position
                st.entities[w.entityId] = doubleArrayOf(p.x, p.y, p.z, w.yaw.toDouble(), w.pitch.toDouble())
                w.position = entityToClient(w.entityId, p, st.outOff)
                w.velocity = w.velocity.map { Mapping.vecToClient(it) }
                event.markForReEncode(true)
            }
            PacketType.Play.Server.ENTITY_TELEPORT -> {
                val w = WrapperPlayServerEntityTeleport(event)
                val flags = w.relativeFlags
                val p = w.position
                if (flags.has(RelativeFlag.X) || flags.has(RelativeFlag.Y) || flags.has(RelativeFlag.Z)) {
                    // 相对传送很少见，这里只按增量旋转
                    w.position = Mapping.vecToClient(p)
                    w.relativeFlags = permuteFlags(flags)
                } else {
                    track(st, w.entityId, p, w.yaw, w.pitch)
                    w.position = entityToClient(w.entityId, p, st.outOff)
                }
                w.deltaMovement = Mapping.vecToClient(w.deltaMovement)
                event.markForReEncode(true)
            }
            PacketType.Play.Server.ENTITY_POSITION_SYNC -> {
                val w = WrapperPlayServerEntityPositionSync(event)
                val v = w.values
                track(st, w.id, v.position, v.yaw, v.pitch)
                v.position = entityToClient(w.id, v.position, st.outOff)
                v.deltaMovement = Mapping.vecToClient(v.deltaMovement)
                event.markForReEncode(true)
            }
            PacketType.Play.Server.ENTITY_RELATIVE_MOVE -> {
                val w = WrapperPlayServerEntityRelativeMove(event)
                st.entities[w.entityId]?.let { it[0] += w.deltaX; it[1] += w.deltaY; it[2] += w.deltaZ }
                val dy = w.deltaY
                w.deltaY = w.deltaZ
                w.deltaZ = -dy
                event.markForReEncode(true)
            }
            PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION -> {
                val w = WrapperPlayServerEntityRelativeMoveAndRotation(event)
                st.entities[w.entityId]?.let {
                    it[0] += w.deltaX; it[1] += w.deltaY; it[2] += w.deltaZ
                    it[3] = w.yaw.toDouble(); it[4] = w.pitch.toDouble()
                }
                val dy = w.deltaY
                w.deltaY = w.deltaZ
                w.deltaZ = -dy
                event.markForReEncode(true)
            }
            PacketType.Play.Server.ENTITY_ROTATION -> {
                val w = WrapperPlayServerEntityRotation(event)
                st.entities[w.entityId]?.let { it[3] = w.yaw.toDouble(); it[4] = w.pitch.toDouble() }
            }
            PacketType.Play.Server.ENTITY_VELOCITY -> {
                val w = WrapperPlayServerEntityVelocity(event)
                if (w.entityId == event.user.entityId && System.currentTimeMillis() < st.dropSelfVelocityUntil) {
                    event.isCancelled = true
                    return
                }
                w.velocity = Mapping.vecToClient(w.velocity)
                event.markForReEncode(true)
            }
            PacketType.Play.Server.DESTROY_ENTITIES -> {
                for (id in WrapperPlayServerDestroyEntities(event).entityIds) st.entities.remove(id)
            }
            PacketType.Play.Server.PLAYER_ABILITIES -> st.flying = WrapperPlayServerPlayerAbilities(event).isFlying
            else -> {}
        }
    }

    private fun filterScale(event: PacketSendEvent) {
        val w = WrapperPlayServerUpdateAttributes(event)
        if (!plugin.isRotatedEntity(w.entityId)) return
        val props = w.properties.filterNot { it.attribute.name.key == "scale" }
        if (props.size == w.properties.size) return
        if (props.isEmpty()) event.isCancelled = true
        else { w.properties = props; event.markForReEncode(true) }
    }

    private fun track(st: PlayerState, id: Int, p: Vector3d, yaw: Float, pitch: Float) {
        st.entities[id] = doubleArrayOf(p.x, p.y, p.z, yaw.toDouble(), pitch.toDouble())
    }

    /** 被旋转的其他玩家用和自己一样的碰撞箱映射，这样在别人眼里的位置才和他自己客户端里一致。 */
    fun entityToClient(id: Int, p: Vector3d, off: Int): Vector3d =
        if (plugin.isRotatedEntity(id)) Mapping.selfToClient(p.x, p.y, p.z, off)
        else Mapping.pointToClient(p, off)

    /** 客户端当前追踪的所有实体按新 offset 重发一次位置。在 netty 事件循环里调用。 */
    fun resyncEntities(st: PlayerState) {
        val off = st.outOff
        for ((id, a) in st.entities) {
            val pos = entityToClient(id, Vector3d(a[0], a[1], a[2]), off)
            st.user.sendPacketSilently(WrapperPlayServerEntityTeleport(id, pos, a[3].toFloat(), a[4].toFloat(), false))
        }
    }

    private fun onServerTeleport(event: PacketSendEvent, st: PlayerState) {
        val w = WrapperPlayServerPlayerPositionAndLook(event)
        val flags = w.relativeFlags
        val relX = flags.has(RelativeFlag.X)
        val relY = flags.has(RelativeFlag.Y)
        val relZ = flags.has(RelativeFlag.Z)
        val pos = w.position

        if (!st.initialized) {
            // 进服 / 换世界后的第一次传送：选一个 offset，让玩家落在客户端高度窗口的中间
            st.minY = event.user.minWorldHeight
            st.maxY = st.minY + event.user.totalWorldHeight
            val off = Mapping.align16(st.center() - (pos.z - Mapping.SELF_Z))
            st.outOff = off
            st.inOff = off
            st.generation++
            st.resetColumns = true
            st.initialized = true
            st.needsSafeSpot = true
            resyncEntities(st)
        } else if (!relZ) {
            // 远距离传送：如果落点离窗口中心太远，顺便换一个 offset
            val cy = pos.z + st.outOff - Mapping.SELF_Z
            if (abs(cy - st.center()) > plugin.settings().recenterThreshold()) {
                val newOff = Mapping.align16(st.center() - (pos.z - Mapping.SELF_Z))
                st.outOff = newOff
                st.generation++
                st.resetColumns = true
                st.pendingOffsets[w.teleportId] = newOff
                resyncEntities(st)
            }
        }

        val off = st.outOff
        val cx = pos.x
        val cy = if (relZ) pos.z else pos.z + off - Mapping.SELF_Z
        val cz = if (relY) -pos.y else -pos.y - Mapping.SELF_Y
        w.position = Vector3d(cx, cy, cz)
        w.deltaMovement = Mapping.vecToClient(w.deltaMovement)

        var mask = permuteFlags(flags).fullMask
        val relYaw = flags.has(RelativeFlag.YAW)
        val relPitch = flags.has(RelativeFlag.PITCH)
        if (!(relYaw && relPitch && w.yaw == 0f && w.pitch == 0f)) {
            val sy = if (relYaw) st.serverYaw + w.yaw else w.yaw
            val sp = if (relPitch) st.serverPitch + w.pitch else w.pitch
            st.serverYaw = sy
            st.serverPitch = sp
            val c = Mapping.lookToClient(sy, sp)
            w.yaw = c[0]
            w.pitch = c[1]
            mask = mask and RelativeFlag.YAW.fullMask.inv() and RelativeFlag.PITCH.fullMask.inv()
        }
        w.relativeFlags = RelativeFlag(mask)
        st.fallDistance = 0.0
        st.lastHeight = Double.NaN
        event.markForReEncode(true)
    }

    /** 服务端 Y 轴的相对标志 <-> 客户端 Z 轴，服务端 Z <-> 客户端 Y。 */
    private fun permuteFlags(f: RelativeFlag): RelativeFlag {
        var m = f.fullMask and (RelativeFlag.Y.fullMask or RelativeFlag.Z.fullMask or
            RelativeFlag.DELTA_Y.fullMask or RelativeFlag.DELTA_Z.fullMask).inv()
        if (f.has(RelativeFlag.Y)) m = m or RelativeFlag.Z.fullMask
        if (f.has(RelativeFlag.Z)) m = m or RelativeFlag.Y.fullMask
        if (f.has(RelativeFlag.DELTA_Y)) m = m or RelativeFlag.DELTA_Z.fullMask
        if (f.has(RelativeFlag.DELTA_Z)) m = m or RelativeFlag.DELTA_Y.fullMask
        return RelativeFlag(m)
    }

    // ================================================================ 客户端 -> 服务端

    override fun onPacketReceive(event: PacketReceiveEvent) {
        if (event.packetType !is PacketType.Play.Client) return
        val st = plugin.stateOf(event.user) ?: return
        val type = event.packetType
        if (WrapperPlayClientPlayerFlying.isFlying(type)) {
            onMove(event, st)
            return
        }
        when (type) {
            PacketType.Play.Client.TELEPORT_CONFIRM -> {
                val id = WrapperPlayClientTeleportConfirm(event).teleportId
                st.pendingOffsets.remove(id)?.let { st.inOff = it }
                if (st.isFakeTeleport(id)) event.isCancelled = true
                st.lastHeight = Double.NaN
                if (!st.isFakeTeleport(id)) st.lastPacketZ = Double.NaN
            }
            PacketType.Play.Client.PLAYER_DIGGING -> {
                val w = WrapperPlayClientPlayerDigging(event)
                if (w.action == DiggingAction.START_DIGGING || w.action == DiggingAction.CANCELLED_DIGGING ||
                    w.action == DiggingAction.FINISHED_DIGGING
                ) {
                    w.blockPosition = Mapping.blockToServer(w.blockPosition, st.inOff)
                    w.blockFace = Mapping.faceToServer(w.blockFace)
                    event.markForReEncode(true)
                }
            }
            PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT -> {
                val w = WrapperPlayClientPlayerBlockPlacement(event)
                w.blockPosition = Mapping.blockToServer(w.blockPosition, st.inOff)
                w.face = Mapping.faceToServer(w.face)
                w.cursorPosition = Mapping.cursorToServer(w.cursorPosition)
                event.markForReEncode(true)
            }
            PacketType.Play.Client.INTERACT_ENTITY -> {
                val w = WrapperPlayClientInteractEntity(event)
                if (w.target.isPresent) {
                    w.target = w.target.map { Mapping.vecToServerF(it) }
                    event.markForReEncode(true)
                }
            }
            PacketType.Play.Client.UPDATE_SIGN -> {
                val w = WrapperPlayClientUpdateSign(event)
                w.blockPosition = Mapping.blockToServer(w.blockPosition, st.inOff)
                event.markForReEncode(true)
            }
            PacketType.Play.Client.PICK_ITEM_FROM_BLOCK -> {
                val w = WrapperPlayClientPickItemFromBlock(event)
                w.blockPos = Mapping.blockToServer(w.blockPos, st.inOff)
                event.markForReEncode(true)
            }
            PacketType.Play.Client.PLAYER_ABILITIES -> st.flying = WrapperPlayClientPlayerAbilities(event).isFlying
            // 死亡后点"重生"：Paper 和 Folia 上都一定会有这个包，比 PlayerRespawnEvent 可靠
            PacketType.Play.Client.CLIENT_STATUS -> {
                if (plugin.settings().safeSpawnOnRespawn() &&
                    WrapperPlayClientClientStatus(event).action == WrapperPlayClientClientStatus.Action.PERFORM_RESPAWN
                ) plugin.pendingSafeSpawn().add(st.uuid)
            }
            else -> {}
        }
    }

    private fun onMove(event: PacketReceiveEvent, st: PlayerState) {
        if (!st.initialized) return
        val w = WrapperPlayClientPlayerFlying(event)
        val loc = w.location
        if (w.hasPositionChanged()) {
            val s = Mapping.selfToServer(loc.x, loc.y, loc.z, chooseInOffset(st, loc.y))
            loc.position = s
            if (!st.lastPacketZ.isNaN()) {
                val v = s.z - st.lastPacketZ
                if (abs(v) < 20) st.verticalSpeed = st.verticalSpeed * 0.5 + v * 0.5
            }
            st.lastPacketZ = s.z
            trackFall(event, st, s.z, w.isOnGround)
        }
        if (w.hasRotationChanged()) {
            val r = Mapping.lookToServer(loc.yaw, loc.pitch)
            loc.yaw = r[0]
            loc.pitch = r[1]
            st.serverYaw = r[0]
            st.serverPitch = r[1]
        }
        w.location = loc
        event.markForReEncode(true)
    }

    /**
     * 有待切换的 offset 时，不只看传送确认包：新旧 offset 各换算一次，取离上一个位置最近的。
     * offset 每次至少差 16 格，正常一个包只移动几格，不会判错；
     * 这样即使确认包和移动包的顺序被中间层（ViaVersion / ViaProxy）打乱，也不会出现几十格的跳变。
     */
    private fun chooseInOffset(st: PlayerState, clientY: Double): Int {
        var best = st.inOff
        if (st.pendingOffsets.isEmpty() || st.lastPacketZ.isNaN()) return best
        var bestD = abs(clientY - best + Mapping.SELF_Z - st.lastPacketZ)
        for (o in st.pendingOffsets.values) {
            val d = abs(clientY - o + Mapping.SELF_Z - st.lastPacketZ)
            if (d < bestD) { best = o; bestD = d }
        }
        if (best != st.inOff) st.inOff = best
        return best
    }

    /** 高度用服务端 z（= 客户端竖直方向），和 offset 无关，重新定位时不会跳变。 */
    private fun trackFall(event: PacketReceiveEvent, st: PlayerState, height: Double, onGround: Boolean) {
        if (st.flying) {
            st.fallDistance = 0.0
        } else if (onGround) {
            if (st.fallDistance > 3.0) plugin.listener.scheduleFallDamage(st.uuid, st.fallDistance)
            st.fallDistance = 0.0
        } else if (!st.lastHeight.isNaN() && height < st.lastHeight) {
            st.fallDistance += st.lastHeight - height
        }
        st.lastHeight = height
    }
}
