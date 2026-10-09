// 找安全位置的搜索策略。纯算法：对世界的了解全部来自 BlockProbe（Gosu 在 Bukkit 之上实现），
// 所以这里没有 Bukkit，也不关心线程。
//
// Fantom 的 Java FFI：Java 的 long / double / boolean 就是 Fantom 的 Int / Float / Bool，
// Java 引用类型在 Fantom 里都是可空的（BlockProbe?、Spot?）。
using [java] me.leleawa.rotatedworld.api

class SpotSearch : SpotFinder
{
  ** 由 Java（me.leleawa.rotatedworld.FantomBridge）调用：把搜索器交给 Services。
  static Void init() { Services.provideSpotFinder(SpotSearch()) }

  **
  ** 由近到远一圈一圈找（只检查切比雪夫距离正好为 r 的那一层），每一圈里取离 (x, y, z) 最近的。
  ** 竖直方向（服务端 y，旋转后是客户端的水平方向）往下最多 16 格、往上最多 24 格。
  **
  override Spot? standable(BlockProbe? probe, Float x, Float y, Float z, Int radius)
  {
    ox := x.floor.toInt
    oy := y.floor.toInt
    oz := z.floor.toInt
    for (r := 0; r <= radius; ++r)
    {
      Spot? best := null
      bestDist := Float.posInf
      for (dx := -r; dx <= r; ++dx)
        for (dz := -r; dz <= r; ++dz)
          for (dy := -r.min(16); dy <= r.min(24); ++dy)
          {
            if (dx.abs.max(dz.abs).max(dy.abs) != r) continue
            spot := probe.standableOn(ox + dx, oy + dy, oz + dz)
            if (spot == null) continue
            d := dist2(spot, x, y, z)
            if (d < bestDist) { bestDist = d; best = spot }
          }
      if (best != null) return best
    }
    return null
  }

  **
  ** 身体卡在方块里时，在附近（竖直方向优先往上）找一个身体放得下的位置。
  **
  override Spot? unstuck(BlockProbe? probe, Float x, Float y, Float z)
  {
    if (probe.bodyFree(x, y, z)) return null
    bx := x.floor.toInt
    by := y.floor.toInt
    bz := z.floor.toInt
    maxY := probe.maxHeight.toFloat
    for (i := 0; i < candidates.size; ++i)
    {
      c := candidates[i]
      cx := (bx + c[0]).toFloat + 0.5f
      cy := (by + c[1]).toFloat
      cz := (bz + c[2]).toFloat + 0.5f
      if (cy + 0.6f > maxY) continue
      if (probe.bodyFree(cx, cy, cz)) return Spot(cx, cy, cz)
    }
    return null
  }

  ** 候选偏移 (dx, dy, dz)：dx -2..2，dy 0..32，dz -4..4，按距离排序（排序是稳定的，和原来的顺序一致）。
  private static const Int[][] candidates := makeCandidates

  private static Int[][] makeCandidates()
  {
    list := Int[][,]
    (-2..2).each |dx| { (0..32).each |dy| { (-4..4).each |dz| { list.add([dx, dy, dz]) } } }
    return list.sort |a, b| { len2(a) <=> len2(b) }.toImmutable
  }

  private static Int len2(Int[] c) { c[0] * c[0] + c[1] * c[1] + c[2] * c[2] }

  private static Float dist2(Spot s, Float x, Float y, Float z)
  {
    dx := s.x - x
    dy := s.y - y
    dz := s.z - z
    return dx * dx + dy * dy + dz * dz
  }
}
