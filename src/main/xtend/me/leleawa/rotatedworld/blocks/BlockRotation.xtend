package me.leleawa.rotatedworld.blocks

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import com.github.retrooper.packetevents.protocol.world.biome.Biome
import com.github.retrooper.packetevents.protocol.world.biome.Biomes
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes
import com.github.retrooper.packetevents.util.mappings.IRegistry
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import java.util.Arrays
import java.util.Map
import java.util.Set
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import me.leleawa.rotatedworld.api.MaterialRules
import me.leleawa.rotatedworld.geometry.Geometry
import me.leleawa.rotatedworld.kernel.BlockRotator
import org.bukkit.Axis
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Directional
import org.bukkit.block.data.FaceAttachable
import org.bukkit.block.data.FaceAttachable.AttachedFace
import org.bukkit.block.data.Lightable
import org.bukkit.block.data.MultipleFacing
import org.bukkit.block.data.Orientable
import org.bukkit.block.data.Powerable
import org.bukkit.block.data.Rotatable
import org.bukkit.block.data.Waterlogged

/**
 * 方块状态 id 的旋转表（服务端 id -> 客户端看到的旋转后 id），以及群系 / 方块实体的 id 查询。
 * 所有方法都可以在任意线程调用。
 *
 * BlockData 是一堆可以叠加的接口（Directional、Orientable、MultipleFacing……），旋转规则就是对这些接口逐个做类型判断；
 * Xtend 的 instanceof 自动转型 + 属性语法（d.facing = n）让这部分读起来和规则本身一样。
 * 名字层面的规则（哪个方块有墙上变体、需要什么方块实体）在 Flix 层（MaterialRules）。
 */
class BlockRotation implements BlockRotator {

	static val CARDINALS = #[BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN]

	/** 服务端 -> 客户端的方向：把方向的单位向量交给 Frege 的同一个旋转（Geometry.vecToClient*），不手写表。 */
	static val Map<BlockFace, BlockFace> FACES = CARDINALS.toInvertedMap[rotated]

	def private static BlockFace rotated(BlockFace f) {
		val x = f.modX
		val y = Geometry.vecToClientY(f.modZ) as int
		val z = Geometry.vecToClientZ(f.modY) as int
		CARDINALS.findFirst[modX == x && modY == y && modZ == z]
	}

	val MaterialRules names
	val ClientVersion clientVersion = PacketEvents.API.serverManager.version.toClientVersion

	val int[] byId = newIntArrayOfSize(1 << 16)
	val byData = new ConcurrentHashMap<BlockData, Integer>
	val int[] beById = newIntArrayOfSize(1 << 16)
	val biomeIds = new ConcurrentHashMap<org.bukkit.block.Biome, Integer>
	val biomeRegistry = new AtomicReference<IRegistry<Biome>>(Biomes.registry)
	val Set<Integer> airIds
	val int chestTypeId

	new(MaterialRules names) {
		this.names = names
		Arrays.fill(byId, -1)
		Arrays.fill(beById, Integer.MIN_VALUE)
		airIds = #{
			0,
			WrappedBlockState.getDefaultState(clientVersion, StateTypes.CAVE_AIR).globalId,
			WrappedBlockState.getDefaultState(clientVersion, StateTypes.VOID_AIR).globalId
		}
		chestTypeId = BlockEntityTypes.CHEST.getId(clientVersion)
	}

	override version() { clientVersion }

	override useBiomeRegistry(IRegistry<Biome> registry) { biomeRegistry.set(registry) }

	override isAir(int clientId) { airIds.contains(clientId) }

	override int rotateId(int serverId) {
		if (serverId == 0) return 0
		if (serverId < 0 || serverId >= byId.length) return serverId
		val cached = byId.get(serverId)
		if (cached >= 0) return cached
		val result = try {
			val str = WrappedBlockState.getByGlobalId(clientVersion, serverId, false).toString
			idOf(rotate(Bukkit.createBlockData("minecraft:" + str)), serverId)
		} catch (Throwable t) {
			serverId
		}
		byId.set(serverId, result)
		result
	}

	override int rotateData(BlockData data) {
		byData.computeIfAbsent(data)[d|idOf(rotate(d), SpigotConversionUtil.fromBukkitBlockData(d).globalId)]
	}

	def private int idOf(BlockData rotated, int fallback) {
		val id = SpigotConversionUtil.fromBukkitBlockData(rotated).globalId
		if (id == 0 && !rotated.material.isAir) fallback else id
	}

	/**
	 * 客户端只会给区块包里列出的坐标创建方块实体，箱子/床/告示牌这类靠方块实体渲染的方块
	 * 如果不列出来就是隐形的。返回方块实体类型 id，不需要时返回 -1。
	 */
	override int blockEntityType(int clientId) {
		if (clientId <= 0 || clientId >= beById.length) return -1
		val cached = beById.get(clientId)
		if (cached != Integer.MIN_VALUE) return cached
		val name = WrappedBlockState.getByGlobalId(clientVersion, clientId, false).type.name.toLowerCase
		val typeName = names.blockEntityType(name)
		val result = if (typeName.empty) -1 else {
			val type = BlockEntityTypes.getByName(typeName)
			if (type === null) chestTypeId else type.getId(clientVersion)
		}
		beById.set(clientId, result)
		result
	}

	override int biomeId(org.bukkit.block.Biome biome) {
		biomeIds.computeIfAbsent(biome)[b|
			val key = try { b.getKey.toString } catch (Throwable t) { "minecraft:plains" }
			val reg = biomeRegistry.get
			val entry = reg.getByName(clientVersion, key) ?: reg.getByName(clientVersion, "minecraft:plains")
			if (entry === null) 0 else reg.getId(entry, clientVersion)
		]
	}

	override int plainsBiomeId() {
		val reg = biomeRegistry.get
		val entry = reg.getByName(clientVersion, "minecraft:plains")
		if (entry === null) 0 else reg.getId(entry, clientVersion)
	}

	// ---------------------------------------------------------------- 旋转规则

	def BlockData rotate(BlockData src) {
		val wall = floorToWall(src)
		if (wall !== null) return wall
		val floor = wallToFloor(src)
		if (floor !== null) return floor

		val d = src.clone
		if (d instanceof FaceAttachable && d instanceof Directional) {
			attach(d as FaceAttachable, d as Directional)
			return d
		}
		if (d instanceof Directional) {
			val n = FACES.get(d.facing)
			// 只能水平朝向的方块（楼梯、门、箱子……）转到上/下是非法的，只能保持原样
			if (n !== null && d.faces.contains(n)) d.facing = n
		}
		if (d instanceof Orientable) {
			val n = switch (d.axis) {
				case Y: Axis.Z
				case Z: Axis.Y
				default: Axis.X
			}
			if (d.axes.contains(n)) d.axis = n
		}
		if (d instanceof MultipleFacing) {
			val mapped = d.faces.map[FACES.get(it)].filterNull.toList
			if (mapped.size == d.faces.size && mapped.forall[f|d.allowedFaces.contains(f)]) {
				for (f : d.allowedFaces) d.setFace(f, false)
				for (f : mapped) d.setFace(f, true)
			}
		}
		d
	}

	/** 拉杆、按钮这类"贴在某个面上"的方块。 */
	def private void attach(FaceAttachable a, Directional d) {
		switch (a.attachedFace) {
			// 贴在下面 -> 客户端里贴在南面墙上
			case FLOOR: {
				a.attachedFace = AttachedFace.WALL
				d.facing = BlockFace.NORTH
			}
			case CEILING: {
				a.attachedFace = AttachedFace.WALL
				d.facing = BlockFace.SOUTH
			}
			case WALL:
				switch (d.facing) {
					case SOUTH: a.attachedFace = AttachedFace.FLOOR
					case NORTH: a.attachedFace = AttachedFace.CEILING
					default: {}
				}
		}
	}

	/** 立在地上的火把/告示牌/旗帜/头颅/珊瑚扇 -> 贴在南面墙上，朝北。 */
	def private BlockData floorToWall(BlockData src) {
		if (src instanceof Directional || src instanceof FaceAttachable) return null
		val mat = variant(names.wallVariant(src.material.name))
		if (mat === null) return null
		val d = mat.createBlockData
		if (d instanceof Directional) {
			if (d.faces.contains(BlockFace.NORTH)) d.facing = BlockFace.NORTH
		}
		copyCommon(src, d)
		d
	}

	/** 挂在墙上、朝南的（贴着北面的方块）-> 客户端里贴在下面，变成立着的。 */
	def private BlockData wallToFloor(BlockData src) {
		if (!(src instanceof Directional) || (src as Directional).facing != BlockFace.SOUTH) return null
		val mat = variant(names.floorVariant(src.material.name))
		if (mat === null || !mat.isBlock) return null
		val d = mat.createBlockData
		if (d instanceof Rotatable) d.rotation = BlockFace.SOUTH
		copyCommon(src, d)
		d
	}

	def private Material variant(String name) {
		if (name.empty) null else Material.getMaterial(name)
	}

	def private void copyCommon(BlockData src, BlockData dst) {
		if (src instanceof Waterlogged && dst instanceof Waterlogged) (dst as Waterlogged).waterlogged = (src as Waterlogged).waterlogged
		if (src instanceof Lightable && dst instanceof Lightable) (dst as Lightable).lit = (src as Lightable).lit
		if (src instanceof Powerable && dst instanceof Powerable) (dst as Powerable).powered = (src as Powerable).powered
	}
}
