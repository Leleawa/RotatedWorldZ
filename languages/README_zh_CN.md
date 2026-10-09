# RotatedWorldZ

RotatedWorld（用 PacketEvents 把整个世界绕 X 轴转 90° 给玩家看的 Paper/Folia 插件）的 20 语言版本：
**Java、Kotlin、Scala 3、Gosu、Groovy、Kawa Scheme、Xtend、Fantom、Flix、Frege、Clojure、Haxe** 编译进来，
外加 8 个运行时加载的脚本层 **Kotlin Script、JRuby、JavaScript、Jython、Lua、BeanShell、Common Lisp、Prolog**，全部打成**一个 jar**。


## 谁负责什么

| 层 | 语言 | 负责 | 为什么是它 |
|---|---|---|---|
| 0 api | Java | 纯计算层要实现的契约（只用 JDK 类型） | 所有编译器都读得懂的最小公约数 |
| 1 纯计算 | **Frege** | 坐标代数：旋转的唯一定义，外加 10 条 QuickCheck 性质 | 纯函数 + 自带 QuickCheck；编译出来是 `static int/float/double` 方法，没有装箱 |
| | **Flix** | 方块命名规则（立式 ↔ 墙式、方块实体类型）；缓存回收 | 规则表 + 效果系统保证纯；"哪些区块还有人要"是一条 **Datalog** 规则 |
| | **Clojure** | 配置解析：配置项写成一份 schema 数据 | 数据驱动：加配置项 = 加一行数据 |
| | **Haxe** | 光照 nibble 数组的旋转 | 只用 `haxe.io.Bytes`，同一份代码能编译到 JS/C++（比如离线预览器） |
| | **Fantom** | 安全落脚点的搜索策略 | 纯算法，通过 `BlockProbe` 接口看世界，不碰 Bukkit |
| 2 kernel | Java | 玩家状态 `PlayerState`、`SerialExecutor`、`Host` / `BlockRotator` 接口 | 有 volatile / 原子量的并发共享状态，Java 写最直接 |
| 3 引擎 | **Scala 3** | 区块缓存 `ChunkStore` + 视野引擎 `ViewManager`（浮动原点、预取、发区块） | 性能核心，大量数组循环 |
| | **Gosu** | `WorldProbe`：Bukkit 方块 / 碰撞箱检查；`Material` 的 enhancement | enhancement 给 Bukkit 类型加属性（`type.Hazard`） |
| | **Groovy** | `/rotate` 命令的流程编排 | 冷路径 |
| | **Kawa Scheme** | 缓存清理：收集每个世界的视野范围，交给 Flix 的 Datalog 决定留哪些（原来在 Scala `ViewManager`） | 提前编译，列表处理 |
| | **Xtend** | `BlockRotation`：BlockData 的旋转规则 | `instanceof` 自动转型 + 属性语法，规则读起来就是规则本身 |
| 4 插件 | **Kotlin** | 数据包改写（netty 热路径）、Bukkit 事件、PacketEvents 向量门面 | 原代码；PacketEvents 的 Kotlin 写法最顺 |
| | Java | 插件入口（实现 `Host`）、启动 Flix/Fantom/Clojure 的桥、脚本层加载器 | 入口和装配 |
| 5 脚本 | **Kotlin Script** | 浮动原点的重新定位决策（原来在 Scala `ViewManager`） | 运行时编译成字节码，每 tick 调用也不慢 |
| | **JRuby** | 区块柱的发送顺序（原来在 Scala `ViewManager`） | 一条 `product.each_with_index.sort_by` 链 |
| | **JavaScript** | "移动过快"的卡顿放行（原来在 Kotlin 监听器） | Rhino 解释执行，事件很少 |
| | **Jython** | 击退（原来在 Kotlin 监听器） | 每次挨打一次 |
| | **Lua** | 摔落伤害（原来在 Kotlin 监听器） | 首席陪审员 |
| | **BeanShell** | `/rotate status` 的那一行字（原来在 Groovy） | Java 语法的脚本 |
| | **Common Lisp** | 哪些服务端检查对旋转玩家放行（失败的移动、飞行踢出）（原来在 Kotlin 监听器） | 一张 `cond` 决策表 |
| | **Prolog** | `/rotate` 的参数语法，写成 DCG（原来在 Groovy） | 语法就该用 DCG |
| | **全部八个 + Kawa** | **摔落伤害陪审团**：九种语言各算一遍，少数服从多数，平票听 Lua 的，意见不一致时记警告 | 娱乐 |

**第 1 层在构建上就看不到 Bukkit 和 PacketEvents**（它们只拿到 `api`），五种语言之间也互相看不到。往下调用是普通的 JVM 调用；往上调用只能走接口：Flix、Fantom 把实现交给 `api.Services`，插件主类通过 `kernel.Host` 给第 3 层提供服务。

## 脚本层

脚本不是编译进来的，而是插件启动时由内嵌解释器加载（`script.ScriptLayers`，8 个解释器并行启动，大约 4–5 秒）：

* 源码在 `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*`，构建时打进 jar 的 `scripts/` 下。
* 如果 `plugins/RotatedWorldZ/scripts/<目录>/rules.*` 存在，就用它覆盖 jar 里的那份：**改规则不用重新构建，重启服务器即可**。
* 脚本实现的接口在 `api.ScriptedRules`。Kotlin Script 的最后一个表达式要是一个同时实现 `Recenter` 和 `FallJuror` 的对象，而且不能是匿名对象；其余脚本只定义全局函数（Prolog 是谓词，最后一个参数是结果），由 Java 包装成接口。
* 内嵌解释器都不保证线程安全，每个脚本的调用是串行的（`synchronized`）。所以只有 Kotlin Script（编译执行）在每 tick 的热路径上，其余都是冷路径。
* Jython 自带的 jnr/jffi 等库和 JRuby 的版本冲突，所以构建时先把 Jython 单独打包，并重定位这些包（`jythonIsolated` 任务，沿用上游模板的做法）。

几个跨语言保证一致的地方：

* 方块面的映射（UP→NORTH……）不再手写，Xtend（Bukkit `BlockFace`）和 Kotlin（PacketEvents `BlockFace`）都从 Frege 的同一个旋转推出来。
* section 内的坐标映射 `Geometry.localIndex`（Frege）和 Haxe 光照旋转用的是同一个映射，`smokeTest` 逐格比对。

## 构建

```bash
./gradlew build          # 产物：build/libs/RotatedWorldZ-1.0.0.jar；包含 checkGeometry 和 smokeTest
./gradlew checkGeometry  # 只跑 Frege 的 QuickCheck 性质
./gradlew smokeTest      # 不需要服务器：从成品 jar 启动纯计算层并和原 Kotlin 行为对比，链接检查其余各层
```

运行时需要服务器上装 PacketEvents（`depend: [packetevents]`）。

第一次构建会把 Flix、Haxe、Fantom 下载到 `.tools/`（约 60 MB），Haxe 还需要一个 JDK 8（只用它的 `rt.jar`，Gradle toolchain 找不到会通过 foojay 自动下载）。其余全部用 JDK 21。

## 各语言的坑

* **Flix**：只能导出一个入口函数，生成在默认包的 `Main` 类里（`FlixBridge` 用反射调用）。它还会在根目录生成约 500 个类和包（`Array`、`List`……），会遮住 Scala/Kotlin 自己的 `Array`/`List`，所以 Flix 的输出**不在任何编译 classpath 上**，只进运行时和 jar。Datalog 的 guard 最多捕获 5 个变量。Java 接口和 Flix 模块不能同名。
* **Frege**：参数默认是惰性的（Java 签名里是 `Lazy<Float>`），要严格的原始类型参数就在模式上加 `!`。
* **Fantom**：实现带 default 方法的 Java 接口会崩，所以 `api` 里的接口没有 default 方法。Java `long`/`double`/`boolean` 直接对应 Fantom 的 `Int`/`Float`/`Bool`，引用类型都是可空的。浮点字面量要写 `0.5f`（`0.5` 是 Decimal）。
* **Gosu**：`block` 是关键字（Gosu 的 lambda）。gosuc 会立即解析注解类型，所以编译 classpath 上要有 Paper API 依赖的 `org.jetbrains:annotations` 和 `jspecify`。
* **Xtend**：Bukkit 的 `Keyed` 同时有 `key()` 和 `getKey()`，`b.key` 有歧义，要写 `b.getKey`。字段和方法同名时方法体里的名字指向方法。
* **Haxe**：`--jvm` 目标；Java 外部类加载器只认 JDK 8 的 `rt.jar`；官方 Windows 包里的 `haxelib` 依赖 Neko，构建时放一个桩到 PATH。
* **Clojure**：靠线程上下文类加载器找命名空间，`ClojureBridge` 在初始化和调用时临时指向插件的类加载器。
* **Kotlin Script**：脚本的结果不能是匿名对象（`object : X {}` 报 "anonymous type"），要先声明一个具名类。
* **Kawa**：Java 的 `null` 在 Scheme 里是真值（只有 `#f` 是假），判断要写 `(eq? x #!null)`；`obj:field` 读字段，`(obj:method)` 调方法。
* **线程上下文类加载器**：服务器线程（主线程、区域线程、netty、全局调度器）的上下文类加载器看不见插件的类，而 Kawa、Clojure 的运行时按名字找类时用的正是它。所以凡是从服务器线程进入这些运行时的地方都用 `kernel.PluginContext` 包一层（Kawa 缓存清理、陪审团、所有脚本调用、Gosu/Fantom 找落脚点、Groovy 命令）。`smokeTest` 的主线程因此把上下文类加载器设成 platform 加载器，模拟服务器线程。
* **Prolog**：tuProlog 默认不加载 DCG，要 `loadLibrary(new DCGLibrary())` 才有 `-->` 和 `phrase/2`。
* **Jython**：`PyString` 是 Python 2 的字节串，路径里有中文时要用 `Py.newStringOrUnicode`。`smokeTest` 因此从 `build/smoke/插件 plugins/` 这种非 ASCII 路径运行。
* **Common Lisp (ABCL)**：初始化时想用反射打开 JDK 的虚拟线程工厂，没有 `--add-opens java.base/java.lang` 就往 `System.err` 打一行（Paper 会因此警告"插件用了 System.err"）。无害，`StderrFilter` 在 ABCL 初始化期间只拦下这一行。
* **陪审团**：`ceil(2.5 - 3)` 是 `-0.0`，Python / Kotlin / Scheme 的 `max` 会原样保留它，计票前要规范化成 `0.0`，否则摔 2～3 格时会误报"意见不一"。
* **Gosu**：运行时一初始化就会让 `java.base` 对反射开放，之后别的库的反射问题就看不出来了。所以 `smokeTest` 按插件 `onEnable` 的顺序，先加载脚本层，再碰 Gosu。`./gradlew smokeTest -PsmokeJava=25` 可以换成服务器用的 JDK 跑。
* **JRuby**：`sort_by` 不稳定，要保持原来的顺序得把下标放进排序键。
* **Scala 3**：删除源文件后，增量编译可能留下旧的 `.tasty`，而且会进构建缓存。遇到 jar 里有不该有的类时，跑一次 `./gradlew clean build --rerun-tasks`。

## 已验证 / 未验证

已验证（Windows 11，JDK 21，Gradle 9.7.1）：

* 12 种编译语言全部编译、8 个脚本层全部加载，`./gradlew clean build --rerun-tasks` 通过。
* `checkGeometry`：10 条 QuickCheck 性质通过，包括往返、格子/点一致、视角旋转和向量旋转一致、`zBase` 是向下取整除法。
* `smokeTest`（共 123,134 项检查，在隔离的类加载器里、从带中文的路径运行，模拟插件加载；JDK 21 和 25 都跑过）：
  * Flix 规则、Clojure 配置、Haxe 光照、Fantom 搜索的结果，和原 Kotlin 实现逐项一致；
  * 8 个脚本和 Kawa 的结果和它们接手的原代码逐项一致，陪审团九票全部一致、不记任何警告；脚本层加载时不往 `System.err` 打印；
  * Scala/Gosu/Groovy/Xtend/Kotlin 各层能链接，Gosu/Groovy/Scala 的运行时能初始化。

**没验证**：

* 没有在真实的 Paper / Folia 服务器上运行，也没有用客户端进服（那需要接受 Minecraft EULA 并装 PacketEvents）。数据包改写、区块发送、重新定位这些依赖服务器的路径，只有编译检查和"逐行移植自原版"作保证。上服前请先在测试服验一遍。
* jar 约 231 MB（原版 4.9 MB）：Kotlin 编译器、JRuby、Jython、ABCL，再加 Gosu、Groovy、Scala、Clojure、Frege、Kawa 的运行时。插件启动多花 4–5 秒加载脚本层。这些运行时是原样打进去的，没有 relocate：如果同一个服务器上还有别的插件带了不同版本的 Kotlin/Scala/Groovy，可能冲突。

## 目录

```
src/api/java        第 0 层：纯计算层的契约（JDK 类型）
src/kernel/java     第 2 层：共享状态和服务接口（Bukkit + PacketEvents）
src/main/<语言>      各语言源码（脚本层在 src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog}）
src/main/resources  plugin.yml、config.yml
buildSrc            各语言的 Gradle 编译任务
build.gradle.kts    分层与依赖
```
