# RotatedWorldZ

A 20-language edition of RotatedWorld (a Paper/Folia plugin that rotates the entire world 90° around the X-axis for players using PacketEvents):
**Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe** compiled ahead of time,
plus 8 runtime-loaded scripting layers: **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog**, all packaged into **a single jar**.


## Who Does What

| Layer | Language | Responsibility | Rationale |
|---|---|---|---|
| 0 api | Java | Contracts to be implemented by the pure computation layer (JDK types only) | Lowest common denominator understood by all compilers |
| 1 Pure computation | **Frege** | Coordinate algebra: canonical definition of rotation, plus 10 QuickCheck properties | Pure functions + built-in QuickCheck; compiles to `static int/float/double` methods without boxing |
| | **Flix** | Block naming rules (standing ↔ wall variants, block entity types); cache eviction | Rule tables + effect system guarantees purity; "which chunks are still needed" is a **Datalog** rule |
| | **Clojure** | Configuration parsing: config options written as schema data | Data-driven: adding a config option = adding a line of data |
| | **Haxe** | Rotation of lighting nibble arrays | Uses only `haxe.io.Bytes`; the exact same code can compile to JS/C++ (e.g. offline previewers) |
| | **Fantom** | Safe landing spot search strategy | Pure algorithm inspecting the world via the `BlockProbe` interface, zero Bukkit dependencies |
| 2 kernel | Java | Player state `PlayerState`, `SerialExecutor`, `Host` / `BlockRotator` interfaces | Concurrent shared state with `volatile` / atomic primitives; most straightforward in Java |
| 3 Engine | **Scala 3** | Chunk cache `ChunkStore` + view distance engine `ViewManager` (floating origin, prefetching, chunk dispatch) | Performance core with heavy array loops |
| | **Gosu** | `WorldProbe`: Bukkit block / bounding box checks; `Material` enhancements | Enhancements add properties to Bukkit types (`type.Hazard`) |
| | **Groovy** | Command workflow orchestration for `/rotate` | Cold path |
| | **Kawa Scheme** | Cache cleanup: collects view ranges for each world and delegates retention decisions to Flix's Datalog (originally in Scala `ViewManager`) | Ahead-of-time compilation, list processing |
| | **Xtend** | `BlockRotation`: rotation rules for `BlockData` | `instanceof` smart casts + property syntax; rules read like the rules themselves |
| 4 Plugin | **Kotlin** | Packet rewriting (Netty hot path), Bukkit events, PacketEvents vector facades | Original codebase; PacketEvents syntax is most idiomatic in Kotlin |
| | Java | Plugin entry point (implements `Host`), bootstrap bridges for Flix/Fantom/Clojure, script layer loader | Entry point and wiring |
| 5 Scripts | **Kotlin Script** | Floating origin recentering decisions (originally in Scala `ViewManager`) | Compiled to bytecode at runtime; performant enough even when called every tick |
| | **JRuby** | Chunk column dispatch order (originally in Scala `ViewManager`) | A single `product.each_with_index.sort_by` chain |
| | **JavaScript** | "Moved too quickly" rubberband pass-through (originally in Kotlin listener) | Interpreted via Rhino; events are infrequent |
| | **Jython** | Knockback (originally in Kotlin listener) | Evaluated once per hit |
| | **Lua** | Fall damage (originally in Kotlin listener) | Chief juror |
| | **BeanShell** | Status line for `/rotate status` (originally in Groovy) | Scripting with Java syntax |
| | **Common Lisp** | Determining which server-side checks to exempt for rotated players (failed moves, flying kick) (originally in Kotlin listener) | A single `cond` decision table |
| | **Prolog** | Argument grammar for `/rotate`, written as DCG (originally in Groovy) | Grammars belong in DCGs |
| | **All eight + Kawa** | **Fall Damage Jury**: Evaluated across nine languages independently; majority vote rules, ties broken by Lua, warnings logged on dissent | Pure entertainment |

**Layer 1 cannot see Bukkit or PacketEvents at build time** (it only has access to `api`), nor can the five languages see each other. Downward calls are standard JVM calls; upward calls can only go through interfaces: Flix and Fantom delegate implementations to `api.Services`, and the main plugin class provides services to Layer 3 via `kernel.Host`.

## Scripting Layers

Scripts are not compiled ahead of time, but loaded by embedded interpreters at plugin startup (`script.ScriptLayers`, 8 interpreters bootstrap in parallel, taking ~4–5 seconds):

* Sources reside in `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*`, packaged into `scripts/` inside the jar at build time.
* If `plugins/RotatedWorldZ/scripts/<dir>/rules.*` exists, it overrides the one in the jar: **modify rules without rebuilding—just reload/restart the server**.
* The interfaces implemented by scripts are in `api.ScriptedRules`. The final expression of Kotlin Script must be an object implementing both `Recenter` and `FallJuror`, and cannot be an anonymous object; all other scripts only define global functions (for Prolog, predicates where the last argument is the result), wrapped into interfaces by Java.
* None of the embedded interpreters guarantee thread safety; invocations to each script are serialized (`synchronized`). Therefore, only Kotlin Script (compiled execution) is on the per-tick hot path, while the rest are on cold paths.
* Jython's bundled jnr/jffi libraries conflict with JRuby's versions, so Jython is packaged separately at build time with these packages relocated (the `jythonIsolated` task, following the upstream template approach).

Areas where cross-language consistency is guaranteed:

* Block face mapping (UP→NORTH, etc.) is no longer hardcoded: both Xtend (Bukkit `BlockFace`) and Kotlin (PacketEvents `BlockFace`) are derived from the exact same rotation in Frege.
* The intra-section coordinate mapping `Geometry.localIndex` (Frege) and Haxe lighting rotation share the exact same mapping, verified block-by-block in `smokeTest`.

## Building

```bash
./gradlew build          # Output: build/libs/RotatedWorldZ-1.0.0.jar; includes checkGeometry and smokeTest
./gradlew checkGeometry  # Runs only Frege's QuickCheck properties
./gradlew smokeTest      # No server required: boots the pure computation layer from the built jar, verifies against original Kotlin behavior, and checks linkage across all layers
```

PacketEvents must be installed on the server at runtime (`depend: [packetevents]`).

The initial build downloads Flix, Haxe, and Fantom into `.tools/` (~60 MB). Haxe also requires a JDK 8 (only for its `rt.jar`; automatically downloaded via Foojay if not found by the Gradle toolchain). Everything else uses JDK 21.

## Language Quirks & Gotchas

* **Flix**: Can only export a single entry function, generated in the `Main` class of the default package (invoked via reflection in `FlixBridge`). It also generates ~500 classes and packages (`Array`, `List`, etc.) in the root directory, which shadow Scala/Kotlin's own `Array`/`List`. Therefore, Flix's output **is not on any compilation classpath**, and only included in runtime and the jar. Datalog guards capture at most 5 variables. Java interfaces and Flix modules cannot share the same name.
* **Frege**: Arguments are lazy by default (represented as `Lazy<Float>` in Java signatures); append `!` to patterns to enforce strict primitive parameters.
* **Fantom**: Implementing Java interfaces with `default` methods causes crashes, so interfaces in `api` have no `default` methods. Java `long`/`double`/`boolean` map directly to Fantom's `Int`/`Float`/`Bool`, and all reference types are nullable. Floating-point literals must be written as `0.5f` (`0.5` is a `Decimal`).
* **Gosu**: `block` is a reserved keyword (Gosu's lambda). `gosuc` eagerly resolves annotation types, so the compilation classpath must include Paper API dependencies `org.jetbrains:annotations` and `jspecify`.
* **Xtend**: Bukkit's `Keyed` has both `key()` and `getKey()`, making `b.key` ambiguous; use `b.getKey`. When a field and method share the same name, references within the method body resolve to the method.
* **Haxe**: Uses the `--jvm` target; the Java external class loader only recognizes JDK 8's `rt.jar`; `haxelib` in the official Windows distribution depends on Neko, so a stub is placed on the PATH during build.
* **Clojure**: Relies on the thread context classloader (TCCL) to locate namespaces; `ClojureBridge` temporarily sets it to the plugin's classloader during initialization and invocation.
* **Kotlin Script**: The script's evaluated result cannot be an anonymous object (`object : X {}` throws "anonymous type"); declare a named class first.
* **Kawa**: Java's `null` evaluates to truthy in Scheme (only `#f` is false); null checks must be written as `(eq? x #!null)`; `obj:field` accesses fields, and `(obj:method)` invokes methods.
* **Thread Context ClassLoader (TCCL)**: Server threads (main thread, region threads, Netty, global scheduler) have context classloaders that cannot see plugin classes, yet Kawa and Clojure runtimes use TCCL when resolving classes by name. Therefore, any entry point from server threads into these runtimes is wrapped with `kernel.PluginContext` (Kawa cache eviction, jury evaluation, all script invocations, Gosu/Fantom safe landing search, Groovy command execution). The `smokeTest` main thread sets its context classloader to the platform classloader to simulate server threads.
* **Prolog**: tuProlog does not load DCG by default; requires `loadLibrary(new DCGLibrary())` to enable `-->` and `phrase/2`.
* **Jython**: `PyString` is a Python 2 byte string; when paths contain non-ASCII characters (e.g. Chinese), `Py.newStringOrUnicode` must be used. `smokeTest` runs from a non-ASCII path like `build/smoke/插件 plugins/` to verify this.
* **Common Lisp (ABCL)**: Tries to open JDK's virtual thread factory via reflection during initialization; without `--add-opens java.base/java.lang`, it logs a line to `System.err` (prompting Paper to warn "plugin used System.err"). Harmless; `StderrFilter` intercepts and suppresses this single line during ABCL initialization.
* **Jury**: `ceil(2.5 - 3)` evaluates to `-0.0`. Python, Kotlin, and Scheme's `max` preserve it as-is, so it must be normalized to `0.0` before tallying votes; otherwise, falling 2–3 blocks triggers false "dissent" warnings.
* **Gosu**: Initializing its runtime opens `java.base` to reflection globally, which masks reflection issues in other libraries. Therefore, `smokeTest` follows the plugin's `onEnable` sequence: loading script layers first before touching Gosu. Run `./gradlew smokeTest -PsmokeJava=25` to test against the server's target JDK.
* **JRuby**: `sort_by` is not stable; to preserve original order, indices must be included in the sort key.
* **Scala 3**: After deleting source files, incremental compilation may leave stale `.tasty` files that enter the build cache. If unexpected classes appear in the jar, run `./gradlew clean build --rerun-tasks`.

## Verified / Unverified

Verified (Windows 11, JDK 21, Gradle 9.7.1):

* All 12 compiled languages compile cleanly, all 8 scripting layers load successfully, `./gradlew clean build --rerun-tasks` passes.
* `checkGeometry`: All 10 QuickCheck properties pass, including roundtrip identity, grid/point consistency, pitch/yaw rotation matching vector rotation, and `zBase` floor division.
* `smokeTest` (123,134 checks in total, executed in an isolated classloader from a non-ASCII path to simulate plugin loading; verified on both JDK 21 and 25):
  * Flix rules, Clojure configuration, Haxe lighting, and Fantom search results match the original Kotlin implementation item-by-item;
  * Results from all 8 scripts and Kawa match the original code they replaced item-by-item; all 9 jury votes agree with zero warnings; scripting layer loading emits nothing to `System.err`;
  * Scala/Gosu/Groovy/Xtend/Kotlin layers link cleanly; Gosu, Groovy, and Scala runtimes initialize successfully.

**Unverified**:

* Not tested on a live Paper / Folia server, nor joined with a real Minecraft client (which requires accepting the Minecraft EULA and installing PacketEvents). Server-dependent paths such as packet rewriting, chunk sending, and recentering rely solely on compilation checks and line-by-line porting fidelity. Test on a staging server before deploying to production.
* The jar is ~231 MB (original was 4.9 MB): includes the Kotlin compiler, JRuby, Jython, ABCL, plus runtimes for Gosu, Groovy, Scala, Clojure, Frege, and Kawa. Plugin startup takes an extra 4–5 seconds to load script layers. These runtimes are bundled as-is without relocation: potential classpath conflicts may arise if other plugins on the server bundle different versions of Kotlin, Scala, or Groovy.

## Directory Structure

```
src/api/java        Layer 0: Pure computation contracts (JDK types)
src/kernel/java     Layer 2: Shared state and service interfaces (Bukkit + PacketEvents)
src/main/<language> Source code per language (scripts in src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog})
src/main/resources  plugin.yml, config.yml
buildSrc            Gradle build tasks for each language
build.gradle.kts    Layer definitions and dependencies
```
