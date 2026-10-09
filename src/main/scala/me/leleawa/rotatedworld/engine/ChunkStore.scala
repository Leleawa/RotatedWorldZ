package me.leleawa.rotatedworld.engine

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound
import com.github.retrooper.packetevents.protocol.world.chunk.{BaseChunk, Column, HeightmapType, LightData, TileEntity}
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18
import com.github.retrooper.packetevents.protocol.world.chunk.palette.{DataPalette, PaletteType, SingletonPalette}
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData
import me.leleawa.rotatedworld.geometry.Geometry
import me.leleawa.rotatedworld.kernel.BlockRotator
import me.leleawa.rotatedworld.light.LightJvm
import org.bukkit.ChunkSnapshot

import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReferenceArray
import scala.jdk.CollectionConverters.*

/** 方块实体：local 是客户端 section 内坐标 (ly shl 8 or lz shl 4 or lx)。 */
final class BE(val local: Int, val tpe: Int, val nbt: NBTCompound | Null)

/**
 * 一个服务端 section 旋转之后的样子。旋转只取决于 section 本身，和浮动原点 offset 无关
 * （offset 是 16 的倍数，只决定它落在客户端区块柱的第几个 section），所以可以缓存复用。
 * 发布后不可变；方块更新时整个复制一份再替换（copy-on-write），保证 netty 线程序列化时的安全。
 *
 * 光照全 0 / 全 15 时不存数组（sky / block 为 null，值在 skyUniform / blockUniform 里）。
 */
final class RotSection(
    val chunk: Chunk_v1_18,
    val sky: Array[Byte] | Null, val skyUniform: Int,
    val block: Array[Byte] | Null, val blockUniform: Int,
    val blockEntities: List[BE],
)

/** 一个服务端区块旋转后的全部 section（下标 = 服务端 section 下标 - minSection）。 */
final class RotatedChunk(val sections: AtomicReferenceArray[RotSection])

object ChunkStore:
  // 区块坐标打包成 long（Java 的 Set<Long> 也用这个，见 PlayerState.sentColumns）
  def key(x: Int, z: Int): Long = (x.toLong << 32) | (z.toLong & 0xffffffffL)
  def keyX(k: Long): Int = (k >> 32).toInt
  def keyZ(k: Long): Int = k.toInt

/**
 * 旋转后区块的缓存：世界 -> (服务端区块坐标 -> 旋转好的区块)。
 * 构建在构建线程，读取在玩家线程 / 编码线程，方块更新在 netty 线程。
 */
final class ChunkStore(rot: BlockRotator):
  import ChunkStore.*

  private val worlds = ConcurrentHashMap[String, ConcurrentHashMap[java.lang.Long, RotatedChunk]]()
  private val fullLight: Array[Byte] = LightJvm.filled(15)
  private val noLight: Array[Byte] = LightJvm.filled(0)

  private def map(world: String) = worlds.computeIfAbsent(world, _ => ConcurrentHashMap())

  def get(world: String, x: Int, z: Int): RotatedChunk | Null =
    val m = worlds.get(world)
    if m == null then null else m.get(key(x, z))

  def has(world: String, x: Int, z: Int): Boolean = get(world, x, z) != null

  def put(world: String, x: Int, z: Int, chunk: RotatedChunk, overwrite: Boolean): Unit =
    val m = map(world)
    if overwrite then m.put(key(x, z), chunk) else m.putIfAbsent(key(x, z), chunk)

  def size: Int = worlds.values.asScala.map(_.size).sum

  def clearWorld(world: String): Unit = worlds.remove(world)

  def worldKeys: Set[String] = worlds.keySet.asScala.toSet

  /** 给 Kawa 用的（Scala 的 Set 在 Scheme 里不好用）。 */
  def worldKeyArray: Array[String] = worlds.keySet.asScala.toArray

  /** 当前缓存的区块坐标（快照）。 */
  def keys(world: String): Array[Long] =
    val m = worlds.get(world)
    if m == null then Array.emptyLongArray else m.keySet.asScala.map(_.longValue).toArray

  def remove(world: String, x: Int, z: Int): Unit =
    val m = worlds.get(world)
    if m != null then m.remove(key(x, z))

  // ---------------------------------------------------------------- 构建

  /** 从 Bukkit 快照构建（用于离玩家较远、服务端不会主动发送的区块）。 */
  def buildFromSnapshot(s: ChunkSnapshot, minY: Int, maxY: Int): RotatedChunk =
    val count = (maxY - minY) >> 4
    val arr = AtomicReferenceArray[RotSection](count)
    var k = 0
    while k < count do
      val baseY = minY + (k << 4)
      val biomes = PaletteType.BIOME.create()
      for bx <- 0 to 3; by <- 0 to 3; bz <- 0 to 3 do
        biomes.set(bx, by, bz, rot.biomeId(s.getBiome(bx * 4, baseY + Geometry.localServerY(4, bz) * 4, by * 4)))
      val empty = s.isSectionEmpty(k)
      val blocks = if empty then emptyPalette() else newChunkPalette()
      var nonAir = 0
      var bes = List.empty[BE]
      val sky = new Array[Byte](2048)
      val blk = new Array[Byte](2048)
      var ly = 0
      while ly < 16 do
        var lz = 0
        while lz < 16 do
          val y = baseY + Geometry.localServerY(16, lz)
          var lx = 0
          while lx < 16 do
            val idx = (ly << 8) | (lz << 4) | lx
            if !empty then
              val rid = rot.rotateData(s.getBlockData(lx, y, ly))
              if rid != 0 then
                blocks.set(lx, ly, lz, rid)
                if !rot.isAir(rid) then nonAir += 1
                val be = rot.blockEntityType(rid)
                if be >= 0 then bes = BE(idx, be, null) :: bes
            LightJvm.set(sky, idx, s.getBlockSkyLight(lx, y, ly))
            LightJvm.set(blk, idx, s.getBlockEmittedLight(lx, y, ly))
            lx += 1
          lz += 1
        ly += 1
      arr.set(k, section(Chunk_v1_18(rot.version, nonAir, blocks, biomes), sky, blk, bes.reverse))
      k += 1
    RotatedChunk(arr)

  /** 从服务端自己发出的区块包构建（玩家附近的区块，带方块实体 NBT，速度也更快）。 */
  def buildFromPacket(column: Column, light: LightData | Null, minY: Int): RotatedChunk =
    val chunks = column.getChunks
    val count = chunks.length
    val arr = AtomicReferenceArray[RotSection](count)

    // 光照数组按 section 下标 + 1 排列（0 是世界下方那一格）
    val skyArrays = new Array[Array[Byte] | Null](count + 2)
    val skyZero = new Array[Boolean](count + 2)
    val blockArrays = new Array[Array[Byte] | Null](count + 2)
    if light != null then
      unpackLight(light.getSkyLightMask, light.getEmptySkyLightMask, light.getSkyLightArray, skyArrays, skyZero)
      unpackLight(light.getBlockLightMask, light.getEmptyBlockLightMask, light.getBlockLightArray, blockArrays, new Array[Boolean](count + 2))

    val besBySection = Array.fill(count)(List.empty[BE])
    for te <- column.getTileEntities do
      val k = (te.getY - minY) >> 4
      if k >= 0 && k < count then
        besBySection(k) = BE(Geometry.localIndex(te.getX, te.getY & 15, te.getZ), te.getType, te.getNBT) :: besBySection(k)

    var k = 0
    while k < count do
      val src = chunks(k) match
        case c: Chunk_v1_18 => c
        case _              => null
      val biomes = PaletteType.BIOME.create()
      val blocks = if src == null || src.isEmpty then emptyPalette() else newChunkPalette()
      var nonAir = 0
      var bes = besBySection(k).reverse
      if src != null then
        val srcBiomes = src.getBiomeData
        for bx <- 0 to 3; by <- 0 to 3; bz <- 0 to 3 do
          biomes.set(bx, by, bz, srcBiomes.get(bx, Geometry.localServerY(4, bz), by))
        if !src.isEmpty then
          val known = bes.map(_.local).toSet
          var y = 0
          while y < 16 do
            var z = 0
            while z < 16 do
              var x = 0
              while x < 16 do
                val sid = src.getBlockId(x, y, z)
                if sid != 0 then
                  val rid = rot.rotateId(sid)
                  val lz = Geometry.localServerY(16, y)
                  blocks.set(x, z, lz, rid)
                  if !rot.isAir(rid) then nonAir += 1
                  val local = Geometry.localIndex(x, y, z)
                  if !known.contains(local) then
                    val be = rot.blockEntityType(rid)
                    if be >= 0 then bes = BE(local, be, null) :: bes
                x += 1
              z += 1
            y += 1
      val sky =
        val s = skyArrays(k + 1)
        if s != null then LightJvm.rotate(s)
        else if skyZero(k + 1) then noLight
        else fullLight // 服务端没有数据 = 地表以上，天空光满
      val bl = blockArrays(k + 1)
      val blk = if bl != null then LightJvm.rotate(bl) else noLight
      arr.set(k, section(Chunk_v1_18(rot.version, nonAir, blocks, biomes), sky, blk, bes))
      k += 1
    RotatedChunk(arr)

  private def unpackLight(mask: BitSet, empty: BitSet, arrays: Array[Array[Byte]], out: Array[Array[Byte] | Null], zero: Array[Boolean]): Unit =
    var i = 0
    var bit = mask.nextSetBit(0)
    while bit >= 0 do
      if bit < out.length && i < arrays.length then out(bit) = arrays(i)
      i += 1
      bit = mask.nextSetBit(bit + 1)
    bit = empty.nextSetBit(0)
    while bit >= 0 do
      if bit < zero.length then zero(bit) = true
      bit = empty.nextSetBit(bit + 1)

  /** 服务端发来的光照更新，旋转后写进已缓存的区块。 */
  def rotateLightInto(chunk: RotatedChunk, light: LightData): Unit =
    val count = chunk.sections.length
    val skyArrays = new Array[Array[Byte] | Null](count + 2)
    val skyZero = new Array[Boolean](count + 2)
    val blockArrays = new Array[Array[Byte] | Null](count + 2)
    val blockZero = new Array[Boolean](count + 2)
    unpackLight(light.getSkyLightMask, light.getEmptySkyLightMask, light.getSkyLightArray, skyArrays, skyZero)
    unpackLight(light.getBlockLightMask, light.getEmptyBlockLightMask, light.getBlockLightArray, blockArrays, blockZero)
    for k <- 0 until count do
      val j = k + 1
      val touchSky = skyArrays(j) != null || skyZero(j)
      val touchBlock = blockArrays(j) != null || blockZero(j)
      val old = chunk.sections.get(k)
      if (touchSky || touchBlock) && old != null then
        val sky = if touchSky then rotatedOrDark(skyArrays(j)) else stored(old.sky, old.skyUniform)
        val blk = if touchBlock then rotatedOrDark(blockArrays(j)) else stored(old.block, old.blockUniform)
        chunk.sections.set(k, section(old.chunk, sky, blk, old.blockEntities))

  private def rotatedOrDark(a: Array[Byte] | Null): Array[Byte] = if a != null then LightJvm.rotate(a) else noLight

  private def stored(a: Array[Byte] | Null, uniform: Int): Array[Byte] =
    if a != null then a else if uniform == 15 then fullLight else noLight

  private def section(chunk: Chunk_v1_18, sky: Array[Byte], blk: Array[Byte], bes: List[BE]): RotSection =
    val su = LightJvm.uniformValue(sky)
    val bu = LightJvm.uniformValue(blk)
    RotSection(chunk, if su >= 0 then null else sky, su, if bu >= 0 then null else blk, bu, bes)

  // ---------------------------------------------------------------- 方块更新（copy-on-write）

  /** 服务端方块 (x,y,z) 变成 serverId。在 netty 线程调用。 */
  def applyBlockChange(world: String, x: Int, y: Int, z: Int, minY: Int, serverId: Int): Unit =
    val chunk = get(world, x >> 4, z >> 4)
    if chunk == null then return
    val k = (y - minY) >> 4
    if k < 0 || k >= chunk.sections.length then return
    val old = chunk.sections.get(k)
    if old == null then return
    val lx = x & 15
    val ly = z & 15
    val lz = Geometry.localServerY(16, y & 15)
    val rid = rot.rotateId(serverId)
    if old.chunk.getBlockId(lx, ly, lz) == rid then return

    val blocks = copyPalette(old.chunk.getChunkData, PaletteType.CHUNK)
    blocks.set(lx, ly, lz, rid)
    var nonAir = 0
    var i = 0
    while i < 4096 do
      if !rot.isAir(blocks.get(i & 15, i >> 8, (i >> 4) & 15)) then nonAir += 1
      i += 1
    val local = Geometry.localIndex(x & 15, y & 15, z & 15)
    val be = rot.blockEntityType(rid)
    val kept = old.blockEntities.filter(_.local != local)
    val bes = if be >= 0 then kept :+ BE(local, be, null) else kept
    chunk.sections.set(k, RotSection(
      Chunk_v1_18(rot.version, nonAir, blocks, old.chunk.getBiomeData),
      old.sky, old.skyUniform, old.block, old.blockUniform, bes,
    ))

  /** 方块实体数据更新（告示牌文字等），存进缓存，重发区块柱时带上。 */
  def applyBlockEntity(world: String, x: Int, y: Int, z: Int, minY: Int, tpe: Int, nbt: NBTCompound | Null): Unit =
    val chunk = get(world, x >> 4, z >> 4)
    if chunk == null then return
    val k = (y - minY) >> 4
    if k < 0 || k >= chunk.sections.length then return
    val old = chunk.sections.get(k)
    if old == null then return
    val local = Geometry.localIndex(x & 15, y & 15, z & 15)
    val bes = old.blockEntities.filter(_.local != local) :+ BE(local, tpe, nbt)
    chunk.sections.set(k, RotSection(old.chunk, old.sky, old.skyUniform, old.block, old.blockUniform, bes))

  /**
   * PacketEvents 的 PaletteType.create() 是个空的列表调色板，存储全是 0，
   * 而 0 指的是"第一个加进去的方块"。先把空气放到下标 0，没写到的格子才是空气。
   */
  private def newChunkPalette(): DataPalette =
    val p = PaletteType.CHUNK.create()
    p.set(0, 0, 0, 0)
    p

  /** 纯空气 section：单值调色板，只能读，不能再 set（扩容时不会保留原来的值）。 */
  private def emptyPalette(): DataPalette = DataPalette(SingletonPalette(0), null, PaletteType.CHUNK)

  private def copyPalette(src: DataPalette, tpe: PaletteType): DataPalette =
    val dst = if tpe == PaletteType.CHUNK then newChunkPalette() else tpe.create()
    val size = if tpe == PaletteType.CHUNK then 16 else 4
    for y <- 0 until size; z <- 0 until size; x <- 0 until size do
      val v = src.get(x, y, z)
      if v != 0 then dst.set(x, y, z, v)
    dst

  // ---------------------------------------------------------------- 组装客户端区块柱

  @volatile private var airSectionCache: Chunk_v1_18 | Null = null

  private def airSection(): Chunk_v1_18 =
    val cached = airSectionCache
    if cached != null then cached
    else
      val biomes = PaletteType.BIOME.create()
      val plains = rot.plainsBiomeId
      for bx <- 0 to 3; by <- 0 to 3; bz <- 0 to 3 do biomes.set(bx, by, bz, plains)
      val air = Chunk_v1_18(rot.version, 0, emptyPalette(), biomes)
      airSectionCache = air
      air

  def resetAirSection(): Unit = airSectionCache = null

  /** 客户端区块柱 (cx, cz) 需要的服务端区块 z 范围：[zBase, zBase + count)。 */
  def zBase(minY: Int, off: Int): Int = Geometry.zBase(minY, off)

  /**
   * 组装客户端区块柱。客户端 section i 来自服务端区块 (cx, zBase+i) 的第 m 个 section，
   * m = -cz-1（客户端 z 对应服务端 -y）。缺数据时返回 null。
   */
  def buildColumn(world: String, cx: Int, cz: Int, minY: Int, maxY: Int, off: Int, allowMissing: Boolean = false): WrapperPlayServerChunkData | Null =
    val count = (maxY - minY) >> 4
    val k = Geometry.serverSectionOfColumn(cz) - (minY >> 4)
    val sections = new Array[BaseChunk](count)
    val skyMask = BitSet(); val skyEmpty = BitSet()
    val blockMask = BitSet(); val blockEmpty = BitSet()
    val skyArrays = java.util.ArrayList[Array[Byte]]()
    val blockArrays = java.util.ArrayList[Array[Byte]]()
    val tiles = java.util.ArrayList[TileEntity]()

    def airAt(i: Int): Unit =
      sections(i) = airSection()
      skyMask.set(i + 1); skyArrays.add(fullLight); blockEmpty.set(i + 1)

    // 世界下方那一格 section
    skyMask.set(0); skyArrays.add(fullLight); blockEmpty.set(0)

    if k < 0 || k >= count then
      // 服务端 y 超出世界范围：整列都是空气
      for i <- 0 until count do airAt(i)
    else
      val zb = zBase(minY, off)
      var i = 0
      while i < count do
        val c = get(world, cx, zb + i)
        val sec = if c == null then null else c.sections.get(k)
        if sec == null then
          if !allowMissing then return null
          // 数据还没准备好：先用空气占位，之后会整列重发
          airAt(i)
        else
          sections(i) = sec.chunk
          val j = i + 1
          val sky = sec.sky
          if sky != null then { skyMask.set(j); skyArrays.add(sky) }
          else if sec.skyUniform == 15 then { skyMask.set(j); skyArrays.add(fullLight) }
          else skyEmpty.set(j)
          val blk = sec.block
          if blk != null then { blockMask.set(j); blockArrays.add(blk) } else blockEmpty.set(j)
          val baseY = minY + (i << 4)
          for be <- sec.blockEntities do
            val lx = be.local & 15
            val lz = (be.local >> 4) & 15
            val ly = be.local >> 8
            val nbt = if be.nbt != null then be.nbt else NBTCompound()
            tiles.add(TileEntity(((lx << 4) | lz).toByte, (baseY + ly).toShort, be.tpe, nbt))
        i += 1
    // 世界上方那一格 section
    skyMask.set(count + 1); skyArrays.add(fullLight); blockEmpty.set(count + 1)

    val column = Column(cx, cz, true, sections, tiles.toArray(new Array[TileEntity](0)), java.util.Map.of[HeightmapType, Array[Long]]())
    val light = LightData(
      false, blockMask, skyMask, blockEmpty, skyEmpty,
      skyArrays.size, blockArrays.size, skyArrays.toArray(new Array[Array[Byte]](0)), blockArrays.toArray(new Array[Array[Byte]](0)),
    )
    WrapperPlayServerChunkData(column, light)

  /**
   * 这个客户端区块柱在 offset 下是不是纯空气 + 满天空光。
   * 两个 offset 下都是的话，重新定位时不用重发（天上那一半基本都是这样）。
   */
  def columnAllAir(world: String, cx: Int, cz: Int, minY: Int, maxY: Int, off: Int): Boolean =
    val count = (maxY - minY) >> 4
    val k = Geometry.serverSectionOfColumn(cz) - (minY >> 4)
    if k < 0 || k >= count then return true
    val zb = zBase(minY, off)
    (0 until count).forall { i =>
      val c = get(world, cx, zb + i)
      val sec = if c == null then null else c.sections.get(k)
      sec != null && sec.chunk.getBlockCount == 0 && sec.sky == null && sec.skyUniform == 15 &&
        sec.block == null && sec.blockEntities.isEmpty
    }

  /** 某个客户端区块柱需要的服务端区块是否都已缓存。 */
  def columnReady(world: String, cx: Int, cz: Int, minY: Int, maxY: Int, off: Int): Boolean =
    val count = (maxY - minY) >> 4
    val k = Geometry.serverSectionOfColumn(cz) - (minY >> 4)
    if k < 0 || k >= count then return true
    val zb = zBase(minY, off)
    (0 until count).forall(i => has(world, cx, zb + i))
