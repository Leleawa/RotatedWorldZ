/// 缓存回收：哪些旋转好的服务端区块还有玩家需要。
///
/// 这本质上是一个关系连接（缓存的区块 × 玩家视野范围），用 Datalog 写就是一条规则。
/// 每个世界单独求解，全局清理任务每 5 秒调用一次。
mod Retention {

    /// views：(minX, maxX, minZ, maxZ)；cached：(x, z)。返回要保留的 (x, z)。
    pub def keep(views: List[(Int32, Int32, Int32, Int32)], cached: List[(Int32, Int32)]): Set[(Int32, Int32)] =
        let rules = #{
            // Flix 的 guard 最多捕获 5 个变量，所以 x / z 两个方向分开写
            Keep(x, z) :- Cached(x, z), View(minX, maxX, minZ, maxZ),
                          if (minX <= x and x <= maxX),
                          if (minZ <= z and z <= maxZ).
        };
        let facts = inject views, cached into View/4, Cached/2;
        query rules, facts select (x, z) from Keep(x, z) |> Vector.toSet
}
