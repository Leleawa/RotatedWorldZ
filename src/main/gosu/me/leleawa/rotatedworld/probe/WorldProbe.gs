package me.leleawa.rotatedworld.probe

uses me.leleawa.rotatedworld.api.BlockProbe
uses me.leleawa.rotatedworld.api.Spot
uses me.leleawa.rotatedworld.geometry.Geometry
uses org.bukkit.World
uses org.bukkit.util.BoundingBox

/**
 * 找安全位置时对世界的全部提问（BlockProbe）：Fantom 的搜索策略只通过这里看世界。
 * 只读已加载的区块（Folia 上不能跨区域加载），在玩家自己的线程上调用。
 */
class WorldProbe implements BlockProbe {

  var _world : World

  construct(world : World) {
    _world = world
  }

  override function maxHeight() : long {
    return _world.MaxHeight
  }

  /**
   * 客户端身体在服务端里是一个躺着的长方体：x±0.3，y∈[py, py+0.6]，z∈[pz-1.35, pz+0.45]。
   * 服务端出生点只保证"站着"的空间是空的，躺着的这条可能插进山坡或树里。
   */
  override function bodyFree(px : double, py : double, pz : double) : boolean {
    var box = new BoundingBox(
        px - 0.3, py, pz - Geometry.selfZ,
        px + 0.3, py + 0.6, pz + (1.8 - Geometry.selfZ))
    for (x in (Math.floor(box.MinX) as int)..(Math.floor(box.MaxX - 1e-7) as int)) {
      for (y in (Math.floor(box.MinY) as int)..(Math.floor(box.MaxY - 1e-7) as int)) {
        if (y < _world.MinHeight or y >= _world.MaxHeight) {
          continue
        }
        for (z in (Math.floor(box.MinZ) as int)..(Math.floor(box.MaxZ - 1e-7) as int)) {
          var b = _world.getBlockAt(x, y, z)
          if (b.Passable) {
            continue
          }
          var rel = box.clone().shift(-x as double, -y as double, -z as double)
          if (b.CollisionShape.overlaps(rel)) {
            return false
          }
        }
      }
    }
    return true
  }

  /**
   * 方块 (bx,by,bz) 能当旋转视角下的地面的话，返回站在它上面时的服务端脚底坐标。
   * 客户端的"地面"是服务端方块的南面（+Z 面），所以要它南边能放下躺着的身体（沿 +Z 1.8 格），且没有液体。
   */
  override function standableOn(bx : long, by : long, bz : long) : Spot {
    if (by < _world.MinHeight or by >= _world.MaxHeight) {
      return null
    }
    var x = bx as int
    var y = by as int
    var z = bz as int
    if (not _world.isChunkLoaded(x >> 4, z >> 4) or not _world.isChunkLoaded(x >> 4, (z + 3) >> 4)) {
      return null
    }
    var type = _world.getBlockAt(x, y, z).Type
    if (not type.RotatedGround or type.Hazard) {
      return null
    }
    // 身体：x 在方块中间，服务端 y∈[by+0.2, by+0.8]（客户端里刚好在方块顶面上），z 从方块南面往外 1.8 格
    var px = x + 0.5
    var py = y + 0.2
    var pz = z + 1 + Geometry.selfZ
    for (zz in (z + 1)..(z + 2)) {
      var b = _world.getBlockAt(x, y, zz)
      if (b.Liquid or b.Type.Hazard) {
        return null
      }
    }
    if (not bodyFree(px, py, pz)) {
      return null
    }
    return new Spot(px, py, pz)
  }
}
