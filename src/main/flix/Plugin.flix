/// Flix 层的入口（`Plugin.init`，见 build.gradle.kts）。
///
/// Flix 不能把普通函数导出给 Java。入口函数创建实现 Java 接口的对象，交给 `me.leleawa.rotatedworld.api.Services`；
/// Java / Scala / Xtend 通过那里调用 Flix。这个文件只做 Java 类型 <-> Flix 类型的转换，规则本身在
/// BlockNames.flix 和 Retention.flix 里。
mod Plugin {
    import me.leleawa.rotatedworld.api.MaterialRules
    import me.leleawa.rotatedworld.api.RetentionPolicy
    import me.leleawa.rotatedworld.api.Services

    pub def init(): Unit \ IO =
        // Flix 的 new 表达式只能实现一个 Java 类型，所以两个服务是两个对象
        let rules = new MaterialRules {
            def wallVariant(_this: MaterialRules, name: String): String =
                BlockNames.wallVariant(name) |> orEmpty
            def floorVariant(_this: MaterialRules, name: String): String =
                BlockNames.floorVariant(name) |> orEmpty
            def blockEntityType(_this: MaterialRules, name: String): String =
                BlockNames.blockEntityType(name) |> orEmpty
        };
        Services.provideMaterialRules(rules);
        let retention = new RetentionPolicy {
            def retain(_this: RetentionPolicy, boxes: Array[Int32, Static], xs: Array[Int32, Static], zs: Array[Int32, Static]): Array[Bool, Static] \ IO =
                let views = List.range(0, Array.length(boxes) / 4) |> List.map(i ->
                    (Array.get(i * 4, boxes), Array.get(i * 4 + 1, boxes), Array.get(i * 4 + 2, boxes), Array.get(i * 4 + 3, boxes)));
                let n = Array.length(xs);
                let cached = List.range(0, n) |> List.map(i -> (Array.get(i, xs), Array.get(i, zs)));
                let kept = Retention.keep(views, cached);
                let out = Array.repeat(Static, n, false);
                List.forEach(i -> Array.put(Set.memberOf((Array.get(i, xs), Array.get(i, zs)), kept), i, out), List.range(0, n));
                out
        };
        Services.provideRetentionPolicy(retention)

    /// Java 那边用 "" 表示"没有"。
    def orEmpty(o: Option[String]): String = Option.getWithDefault("", o)
}
