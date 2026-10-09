/// 方块变体之间的命名规则。
///
/// 原来是 Kotlin 里一长串 `when { n.endsWith(...) -> ... }`；这里写成规则表 + 第一个匹配的规则生效。
/// 全部是纯函数（Flix 的效果系统保证：签名里没有 `\ IO`），所以可以在任何线程调用、结果可以随便缓存。
mod BlockNames {

    /// 一条后缀规则：名字以 `suffix` 结尾（且不是 `except` 里的名字）时，结果是 `rewrite(去掉后缀的部分)`。
    /// `rewrite` 返回 None 表示"命中了，但没有对应变体"（规则表到此为止）。
    type alias Rule = { suffix = String, except = List[String], rewrite = String -> Option[String] }

    def rule(suffix: String, rewrite: String -> Option[String]): Rule =
        { suffix = suffix, except = Nil, rewrite = rewrite }

    def ruleExcept(suffix: String, except: List[String], rewrite: String -> Option[String]): Rule =
        { suffix = suffix, except = except, rewrite = rewrite }

    /// 第一条匹配的规则决定结果。
    def firstMatch(rules: List[Rule], name: String): Option[String] =
        List.findMap(r -> {
            if (String.endsWith(suffix = r#suffix, name) and not List.memberOf(name, r#except))
                Some(r#rewrite(String.dropRight(String.length(r#suffix), name)))
            else
                None
        }, rules) |> Option.flatten

    // ------------------------------------------------------------ 立在地上 -> 贴在墙上

    /// 立在地上的火把/告示牌/旗帜/头颅/珊瑚扇 -> 它的墙上变体。
    pub def wallVariant(name: String): Option[String] =
        if (String.contains(substr = "WALL", name)) None
        else if (name == "TORCH") Some("WALL_TORCH")
        else firstMatch(floorRules(), name)

    def floorRules(): List[Rule] =
        rule("_TORCH",            base -> Some(base + "_WALL_TORCH")) ::
        rule("_HANGING_SIGN",     _    -> None) ::
        rule("_SIGN",             base -> Some(base + "_WALL_SIGN")) ::
        rule("_BANNER",           base -> Some(base + "_WALL_BANNER")) ::
        rule("_SKULL",            base -> Some(base + "_WALL_SKULL")) ::
        ruleExcept("_HEAD", "PISTON_HEAD" :: Nil, base -> Some(base + "_WALL_HEAD")) ::
        rule("_CORAL_FAN",        base -> Some(base + "_CORAL_WALL_FAN")) :: Nil

    // ------------------------------------------------------------ 贴在墙上 -> 立在地上

    /// 挂在墙上的方块 -> 它的地面变体（悬挂式的除外）。
    pub def floorVariant(name: String): Option[String] =
        if (String.contains(substr = "HANGING", name)) None
        else if (name == "WALL_TORCH") Some("TORCH")
        else if (String.contains(substr = "_WALL_", name)) Some(String.replace(src = "_WALL_", dst = "_", name))
        else None

    // ------------------------------------------------------------ 方块实体

    /// 客户端只会给区块包里列出的坐标创建方块实体，箱子/床/告示牌这类靠方块实体渲染的方块
    /// 如果不列出来就是隐形的。方块状态类型名（小写）-> 方块实体类型名。
    pub def blockEntityType(name: String): Option[String] =
        if (List.memberOf(name, "chest" :: "trapped_chest" :: "ender_chest" :: Nil)) Some(name)
        else if (name == "spawner") Some("mob_spawner")
        else match firstMatch(blockEntityRules(), name) {
            case Some(t) => Some(t)
            case None    => if (Set.memberOf(name, renderedBlockEntities())) Some(name) else None
        }

    def blockEntityRules(): List[Rule] =
        rule("copper_chest",  _ -> Some("copper_chest")) ::
        rule("_bed",          _ -> Some("bed")) ::
        rule("_hanging_sign", _ -> Some("hanging_sign")) ::
        rule("_sign",         _ -> Some("sign")) ::
        rule("shulker_box",   _ -> Some("shulker_box")) ::
        rule("_banner",       _ -> Some("banner")) ::
        rule("_skull",        _ -> Some("skull")) ::
        ruleExcept("_head", "piston_head" :: Nil, _ -> Some("skull")) ::
        rule("campfire",      _ -> Some("campfire")) ::
        rule("shelf",         _ -> Some("shelf")) :: Nil

    def renderedBlockEntities(): Set[String] = Set#{
        "bell", "decorated_pot", "conduit", "enchanting_table", "lectern", "trial_spawner",
        "vault", "end_portal", "end_gateway", "beacon", "copper_golem_statue", "brushable_block"
    }
}
