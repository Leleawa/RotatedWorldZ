package me.leleawa.rotatedworld.engine

import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag
import com.github.retrooper.packetevents.util.{Vector3d, Vector3i}
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import com.github.retrooper.packetevents.wrapper.play.server.{WrapperPlayServerBlockEntityData, WrapperPlayServerPlayerPositionAndLook, WrapperPlayServerUnloadChunk, WrapperPlayServerUpdateViewPosition}
import me.leleawa.rotatedworld.api.ScriptedRules
import me.leleawa.rotatedworld.geometry.Geometry
import me.leleawa.rotatedworld.kernel.{Host, PlayerState}
import org.bukkit.{Bukkit, Chunk, World}
import org.bukkit.entity.Player

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import scala.jdk.CollectionConverters.*

/**
 * 每个被旋转的玩家挂一个 EntityScheduler 定时任务（Folia 上跑在玩家所在的区域线程，Paper 上就是主线程）：
 *  1. 维护客户端视野中心
 *  2. 需要时做浮动原点重新定位（假传送 + 重发区块柱 + 重发实体位置）
 *  3. 保证窗口里需要的服务端区块都已经旋转并缓存
 *  4. 按距离由近到远发送客户端区块柱，卸载超出范围的
 */
final class ViewManager(host: Host, store: ChunkStore,
                        recenter: ScriptedRules.Recenter, spiralRule: ScriptedRules.Spiral):

  private def settings = host.settings

  /** 正在取快照 / 构建的服务端区块，避免重复提交。 */
  private val inProgress = ConcurrentHashMap.newKeySet[String]()

  /** (dx, dz) 按距离排序、预取的 x 顺序，由 JRuby 脚本算（rules.rb），半径变化时重算。 */
  @volatile private var spiralCache: (Int, IndexedSeq[(Int, Int)], Seq[Int]) = (-1, IndexedSeq.empty, Seq.empty)

  private def spirals(r: Int): (IndexedSeq[(Int, Int)], Seq[Int]) =
    val c = spiralCache
    if c._1 == r then (c._2, c._3)
    else
      val flat = spiralRule.order(r)
      val order = flat.grouped(2).map(a => (a(0), a(1))).toIndexedSeq
      val xs = spiralRule.columnsX(r).toSeq
      spiralCache = (r, order, xs)
      (order, xs)

  private def spiral(r: Int): IndexedSeq[(Int, Int)] = spirals(r)._1

  private def spiralXs(r: Int): Seq[Int] = spirals(r)._2

  /** 玩家进服时调用：挂上每 tick 的任务，玩家下线时调度器会自动取消。 */
  def start(p: Player): Unit =
    p.getScheduler.runAtFixedRate(host.plugin, (_ => {
      val st = host.stateOf(p)
      if st != null then
        try
          st.requestBudget = settings.chunkRequestsPerTick
          tickPlayer(p, st)
        catch case t: Throwable => host.logger.warning(s"tick ${p.getName} 出错: $t")
    }): Consumer[io.papermc.paper.threadedregions.scheduler.ScheduledTask], null, 1L, 1L)

  private def tickPlayer(p: Player, st: PlayerState): Unit =
    if !st.initialized || st.recenterInFlight then return
    val world = p.getWorld
    val wk = world.getKey.toString
    if wk != st.worldKey then return

    if st.resetColumns then
      st.resetColumns = false
      st.sentColumns.clear()
      st.centerX = Int.MinValue
      st.centerZ = Int.MinValue
    val gen = st.generation
    val off = st.outOff
    if gen != st.generation then return
    val minY = st.minY
    val maxY = st.maxY
    val count = (maxY - minY) >> 4
    val r = settings.viewRadius

    val loc = p.getLocation
    if host.pendingSafeSpawn.contains(st.uuid) then
      // 等周围几个区块加载好再找（最多等 3 秒），这时客户端还在加载地形，看不到自己往下掉
      val sr = (settings.safeSpawnRadius >> 4) + 1
      val cx = loc.getBlockX >> 4
      val cz = loc.getBlockZ >> 4
      val loaded = (cx - sr to cx + sr).forall(x => (cz - sr to cz + sr).forall(z => world.isChunkLoaded(x, z)))
      st.safeSpawnWaitTicks += 1
      if loaded || st.safeSpawnWaitTicks > 60 then
        host.pendingSafeSpawn.remove(st.uuid)
        st.safeSpawnWaitTicks = 0
        st.needsSafeSpot = false
        if host.moveToStandableSpot(p) then return
    else if st.needsSafeSpot && world.isChunkLoaded(loc.getBlockX >> 4, loc.getBlockZ >> 4) then
      st.needsSafeSpot = false
      if host.moveToSafeSpot(p) then return

    // 竖直速度（netty 线程按客户端移动包算好的），高速状态带 1 秒迟滞
    val v = st.verticalSpeed
    if math.abs(v) > settings.fastSpeed then
      st.fastTicks = 20
      st.fastDir = if v > 0 then 1 else -1
    else if st.fastTicks > 0 then
      st.fastTicks -= 1
    val fast = st.fastTicks > 0
    val vy = if fast then st.fastDir.toDouble else v

    // 客户端坐标：x 不变，竖直 = 服务端 z，客户端 z = 服务端 -y
    val clientX = loc.getX
    val clientY = Geometry.selfToClientY(loc.getZ, off)
    val clientZ = Geometry.selfToClientZ(loc.getY)
    val pcx = math.floor(clientX).toInt >> 4
    val pcz = math.floor(clientZ).toInt >> 4
    st.viewX = pcx
    st.viewZBase = store.zBase(minY, off)

    if pcx != st.centerX || pcz != st.centerZ then
      st.centerX = pcx
      st.centerZ = pcz
      st.sendOrdered(null, java.util.List.of(WrapperPlayServerUpdateViewPosition(pcx, pcz)))

    // 浮动原点：要不要挪、挪到哪，由 Kotlin Script 决定（rules.kts）
    val t = recenter.target(fast, vy, clientY, minY, maxY, st.center, settings)
    if !t.isNaN then
      val newOff = off + Geometry.align16(t - clientY)
      val emergency = recenter.emergency(clientY, minY, maxY)
      if newOff != off && tryRecenter(p, st, world, wk, pcx, pcz, off, newOff, emergency) then return

    // 保证需要的服务端区块已缓存。高速移动时往运动方向多预取，重新定位时就不用等
    val zb = store.zBase(minY, off)
    val below = if fast && vy < 0 then settings.prefetchAheadChunks else settings.prefetchChunks
    val above = if fast && vy > 0 then settings.prefetchAheadChunks else settings.prefetchChunks
    val zs = (zb - below until zb + count + above).toIndexedSeq
    val ordered = if vy > 0 then zs.reverse else zs
    for dx <- spiralXs(r); z <- ordered do ensure(st, world, wk, pcx + dx, z, minY, maxY)

    // 发送区块柱
    var sent = 0
    val it = spiral(r).iterator
    while it.hasNext && sent < settings.columnsPerTick do
      val (dx, dz) = it.next()
      val cx = pcx + dx
      val cz = pcz + dz
      val key = ChunkStore.key(cx, cz)
      if !st.sentColumns.contains(key) && store.columnReady(wk, cx, cz, minY, maxY, off) then
        val packet = store.buildColumn(wk, cx, cz, minY, maxY, off)
        if packet != null then
          st.sendOrdered(gen, java.util.List.of(packet))
          st.sentColumns.add(key)
          sent += 1

    // 卸载超出范围的
    val unload = java.util.ArrayList[PacketWrapper[?]]()
    val sentIt = st.sentColumns.iterator()
    while sentIt.hasNext do
      val key = sentIt.next()
      val x = ChunkStore.keyX(key)
      val z = ChunkStore.keyZ(key)
      if math.abs(x - pcx) > r + 1 || math.abs(z - pcz) > r + 1 then
        unload.add(WrapperPlayServerUnloadChunk(x, z))
        sentIt.remove()
    if !unload.isEmpty then st.sendOrdered(null, unload)

  /**
   * 重新定位。玩家脚下那一列先发，然后发假传送，再发周围 8 列和实体位置，
   * 全部放在同一个 netty 任务里，尽量让客户端在同一帧里处理完。
   * 区块包在编码线程里序列化好，netty 线程只负责写出。
   * 新旧 offset 下都是纯空气的区块柱（天上那一半）不用重发。
   */
  private def tryRecenter(p: Player, st: PlayerState, world: World, wk: String,
                          pcx: Int, pcz: Int, off: Int, newOff: Int, emergency: Boolean): Boolean =
    val minY = st.minY
    val maxY = st.maxY
    val count = (maxY - minY) >> 4
    val zbNew = store.zBase(minY, newOff)
    // 只要求玩家所在的那一列 x 的数据齐了，周围的有多少发多少
    var ready = true
    for z <- zbNew until zbNew + count do
      if !store.has(wk, pcx, z) then
        ensure(st, world, wk, pcx, z, minY, maxY)
        ready = false
    if !ready && !emergency then return false
    val own = store.buildColumn(wk, pcx, pcz, minY, maxY, newOff, allowMissing = emergency)
    if own == null then return false

    val around = java.util.ArrayList[PacketWrapper[?]]()
    val keys = java.util.HashSet[java.lang.Long]()
    // 用空气占位发出去的那一列不算"已发送"，数据齐了之后会自动重发
    if ready then keys.add(ChunkStore.key(pcx, pcz))
    for dx <- -1 to 1; dz <- -1 to 1 if dx != 0 || dz != 0 do
      val col = store.buildColumn(wk, pcx + dx, pcz + dz, minY, maxY, newOff)
      if col != null then
        around.add(col)
        keys.add(ChunkStore.key(pcx + dx, pcz + dz))
    var keptAir = 0
    for key <- st.sentColumns.asScala if !keys.contains(key) do
      val x = ChunkStore.keyX(key)
      val z = ChunkStore.keyZ(key)
      if store.columnAllAir(wk, x, z, minY, maxY, off) && store.columnAllAir(wk, x, z, minY, maxY, newOff) then
        keys.add(key)
        keptAir += 1

    val delta = newOff - off
    st.recenterInFlight = true
    st.sentColumns.clear()
    st.sentColumns.addAll(keys)
    // 队列里按旧 offset 组装、还没编码的区块柱全部作废
    st.minValidGen = st.generation + 1
    // 重新定位不排队：进行中主线程不会再发新的区块柱，中心更新 / 卸载和 offset 无关，先后都行
    st.encoder.execute { () =>
      val ownBufs = st.encode(own)
      val aroundBufs = around.asScala.map(st.encode).toList
      st.runInEventLoop { () =>
        try
          st.outOff = newOff
          st.generation += 1
          val id = st.nextFakeTeleportId()
          st.pendingOffsets.put(id, newOff)
          val all = Seq(RelativeFlag.X, RelativeFlag.Y, RelativeFlag.Z, RelativeFlag.YAW, RelativeFlag.PITCH,
            RelativeFlag.DELTA_X, RelativeFlag.DELTA_Y, RelativeFlag.DELTA_Z).map(_.getFullMask).reduce(_ | _)
          val tp = WrapperPlayServerPlayerPositionAndLook(
            id, Vector3d(0.0, delta.toDouble, 0.0), Vector3d.zero(), 0f, 0f, RelativeFlag(all),
          )
          st.sendEncoded(ownBufs)
          st.user.sendPacketSilently(tp)
          aroundBufs.foreach(st.sendEncoded)
          host.resyncEntities(st)
        finally st.recenterInFlight = false
      }
    }
    if settings.debug then
      host.logger.info(f"[${p.getName}] 重新定位 offset $off -> $newOff (Δ=$delta, v=${st.verticalSpeed}%.2f, 保留纯空气列 $keptAir${if ready then "" else ", 紧急占位"})")
    true

  /**
   * 保证服务端区块 (x, z) 已旋转并缓存。统一用 getChunkAtAsync：
   * 回调在区块所属的线程上执行（Folia 是那个区域的线程，Paper 是主线程），快照就在那里取。
   */
  private def ensure(st: PlayerState, world: World, wk: String, x: Int, z: Int, minY: Int, maxY: Int): Unit =
    if store.has(wk, x, z) || st.requestBudget <= 0 then return
    val key = s"$wk|$x|$z"
    if !inProgress.add(key) then return
    st.requestBudget -= 1
    world.getChunkAtAsync(x, z).whenComplete { (chunk, err) =>
      if err != null || chunk == null then inProgress.remove(key)
      else submitSnapshot(chunk, wk, key, minY, maxY)
    }

  private def submitSnapshot(chunk: Chunk, wk: String, key: String, minY: Int, maxY: Int): Unit =
    val snapshot =
      try chunk.getChunkSnapshot(false, true, false)
      catch case _: Throwable => { inProgress.remove(key); return }
    val cx = chunk.getX
    val cz = chunk.getZ
    host.worker.execute { () =>
      try store.put(wk, cx, cz, store.buildFromSnapshot(snapshot, minY, maxY), overwrite = false)
      catch case t: Throwable => host.logger.warning(s"构建区块 $cx,$cz 失败: $t")
      finally inProgress.remove(key)
    }

  /**
   * 服务端发来的区块包构建完成后（在构建线程调用）：
   * 如果对应的客户端区块柱已经发过了，把带 NBT 的方块实体（告示牌文字、旗帜图案……）补发一次。
   */
  def onPacketChunkBuilt(st: PlayerState, wk: String, x: Int, z: Int, chunk: RotatedChunk): Unit =
    val player = Bukkit.getPlayer(st.uuid)
    if player == null then return
    player.getScheduler.run(host.plugin, (_ => {
      if wk == st.worldKey && st.initialized then
        val gen = st.generation
        val off = st.outOff
        val minSection = st.minY >> 4
        val packets = java.util.ArrayList[PacketWrapper[?]]()
        for k <- 0 until chunk.sections.length do
          val sec = chunk.sections.get(k)
          val cz = Geometry.serverSectionOfColumn(minSection + k)
          if sec != null && st.sentColumns.contains(ChunkStore.key(x, cz)) then
            for be <- sec.blockEntities if be.nbt != null do
              val lx = be.local & 15
              val lz = (be.local >> 4) & 15
              val ly = be.local >> 8
              val clientPos = Vector3i(x * 16 + lx, Geometry.blockToClientY(z * 16 + ly, off), cz * 16 + lz)
              packets.add(WrapperPlayServerBlockEntityData(clientPos, be.tpe, be.nbt))
        if !packets.isEmpty then st.sendOrdered(gen, packets)
    }): Consumer[io.papermc.paper.threadedregions.scheduler.ScheduledTask], null)
