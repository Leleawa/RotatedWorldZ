package me.leleawa.rotatedworld.net

import com.github.retrooper.packetevents.protocol.world.BlockFace
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.util.Vector3i
import me.leleawa.rotatedworld.geometry.Geometry

/**
 * 把 Frege 的坐标代数（Geometry，只有 int / double 分量函数）包装成 PacketEvents 的向量类型。
 * 旋转本身的定义和性质测试都在 Frege 层，这里没有数学，只有搬运。
 */
object Mapping {

    val SCALE: Double get() = Geometry.scale
    val SELF_Y: Double get() = Geometry.selfY
    val SELF_Z: Double get() = Geometry.selfZ

    fun blockToClient(x: Int, y: Int, z: Int, off: Int) =
        Vector3i(x, Geometry.blockToClientY(z, off), Geometry.blockToClientZ(y))

    fun blockToServer(x: Int, y: Int, z: Int, off: Int) =
        Vector3i(x, Geometry.blockToServerY(z), Geometry.blockToServerZ(y, off))

    fun blockToClient(v: Vector3i, off: Int) = blockToClient(v.x, v.y, v.z, off)
    fun blockToServer(v: Vector3i, off: Int) = blockToServer(v.x, v.y, v.z, off)

    fun pointToClient(x: Double, y: Double, z: Double, off: Int) =
        Vector3d(x, Geometry.pointToClientY(z, off), Geometry.pointToClientZ(y))

    fun pointToClient(v: Vector3d, off: Int) = pointToClient(v.x, v.y, v.z, off)

    fun vecToClient(x: Double, y: Double, z: Double) = Vector3d(x, Geometry.vecToClientY(z), Geometry.vecToClientZ(y))
    fun vecToServer(x: Double, y: Double, z: Double) = Vector3d(x, Geometry.vecToServerY(z), Geometry.vecToServerZ(y))
    fun vecToClient(v: Vector3d) = vecToClient(v.x, v.y, v.z)

    fun vecToClientF(v: Vector3f) =
        Vector3f(v.x, Geometry.vecToClientY(v.z.toDouble()).toFloat(), Geometry.vecToClientZ(v.y.toDouble()).toFloat())

    fun vecToServerF(v: Vector3f) =
        Vector3f(v.x, Geometry.vecToServerY(v.z.toDouble()).toFloat(), Geometry.vecToServerZ(v.y.toDouble()).toFloat())

    /** 被旋转的玩家自己（以及别的被旋转玩家）的脚底坐标。 */
    fun selfToClient(x: Double, y: Double, z: Double, off: Int) =
        Vector3d(x, Geometry.selfToClientY(z, off), Geometry.selfToClientZ(y))

    fun selfToServer(x: Double, y: Double, z: Double, off: Int) =
        Vector3d(x, Geometry.selfToServerY(z), Geometry.selfToServerZ(y, off))

    /** UseItemOn 的方块内光标位置 (0..1)。 */
    fun cursorToServer(c: Vector3f) = Vector3f(c.x, Geometry.cursorToServerY(c.z), Geometry.cursorToServerZ(c.y))

    /** 方块面 = 它的法向量，和其它方向向量走同一个旋转（UP -> NORTH，SOUTH -> UP……），表只算一次。 */
    private val facesToClient = faceTable { v -> vecToClient(v) }
    private val facesToServer = faceTable { v -> vecToServer(v.x, v.y, v.z) }

    fun faceToClient(f: BlockFace): BlockFace = facesToClient.getValue(f)
    fun faceToServer(f: BlockFace): BlockFace = facesToServer.getValue(f)

    private fun faceTable(rotate: (Vector3d) -> Vector3d): Map<BlockFace, BlockFace> =
        BlockFace.entries.associateWith { f ->
            if (f == BlockFace.OTHER) return@associateWith f
            val r = rotate(Vector3d(f.modX.toDouble(), f.modY.toDouble(), f.modZ.toDouble()))
            BlockFace.entries.firstOrNull {
                it != BlockFace.OTHER && it.modX == r.x.toInt() && it.modY == r.y.toInt() && it.modZ == r.z.toInt()
            } ?: f
        }

    /** 客户端视角 -> 服务端视角。返回 [yaw, pitch]。 */
    fun lookToServer(yaw: Float, pitch: Float) =
        floatArrayOf(Geometry.lookToServerYaw(yaw, pitch), Geometry.lookToServerPitch(yaw, pitch))

    fun lookToClient(yaw: Float, pitch: Float) =
        floatArrayOf(Geometry.lookToClientYaw(yaw, pitch), Geometry.lookToClientPitch(yaw, pitch))

    fun align16(v: Double): Int = Geometry.align16(v)
}
