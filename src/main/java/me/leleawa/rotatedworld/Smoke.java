package me.leleawa.rotatedworld;

import com.github.retrooper.packetevents.protocol.world.BlockFace;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import me.leleawa.rotatedworld.api.BlockProbe;
import me.leleawa.rotatedworld.api.MaterialRules;
import me.leleawa.rotatedworld.api.Services;
import me.leleawa.rotatedworld.api.Settings;
import me.leleawa.rotatedworld.api.Spot;
import me.leleawa.rotatedworld.engine.ChunkStore;
import me.leleawa.rotatedworld.geometry.Geometry;
import me.leleawa.rotatedworld.light.LightJvm;
import me.leleawa.rotatedworld.net.Mapping;
import me.leleawa.rotatedworld.script.ScriptLayers;

/**
 * 不需要服务器的检查：{@code ./gradlew smokeTest} 用成品 jar（加上 Paper API 和 PacketEvents）运行它。
 *
 * <ul>
 *   <li>纯计算层（Frege、Flix、Clojure、Haxe、Fantom）真的启动，结果和原来的 Kotlin 实现逐项对比；</li>
 *   <li>依赖服务器的层（Scala、Gosu、Groovy、Xtend、Kotlin）加载并解析全部方法签名，跨语言的链接错误在这里暴露；
 *       能脱离服务器构造的对象就构造一下（Gosu / Groovy / Scala 的运行时初始化）。</li>
 * </ul>
 */
public final class Smoke {
    private Smoke() {}

    private static int checks;

    public static void main(String[] args) throws Exception {
        // 像 Bukkit 插件一样，从一个自己的类加载器运行（父加载器是 platform，看不到 jar）。
        // 错误地去系统类加载器里找类的代码（脚本解释器很容易犯）在这里就会失败，而不是到服务器上才失败。
        if (Smoke.class.getClassLoader() == ClassLoader.getSystemClassLoader()) {
            String[] entries = System.getProperty("java.class.path").split(java.io.File.pathSeparator);
            java.net.URL[] urls = new java.net.URL[entries.length];
            for (int i = 0; i < entries.length; i++) urls[i] = java.nio.file.Path.of(entries[i]).toUri().toURL();
            try (java.net.URLClassLoader isolated = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
                // 服务器线程（主线程、区域线程、netty）的上下文类加载器是服务器自己的，看不见插件的类。
                // 这里用 platform 加载器模拟：靠上下文类加载器找类、又没有自己设好的代码会在这里失败
                Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
                isolated.loadClass(Smoke.class.getName()).getMethod("run").invoke(null);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof Error err) throw err;
                throw (Exception) e.getCause();
            }
            return;
        }
        run();
    }

    public static void run() throws Exception {
        System.out.println("jar     : " + Smoke.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("jvm     : " + System.getProperty("java.version") + " " + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        frege();
        flix();
        clojure();
        haxe();
        fantom();
        // 和插件 onEnable 的顺序一致：脚本层在 Gosu 之前加载。Gosu 的运行时一初始化就强行打开 java.base，
        // 之后 ABCL 的反射就会成功，"插件启动时往 System.err 打印"这类问题在这里就看不到了
        scripts();
        kotlinFacade();
        linkUpperLayers();
        System.out.println("OK (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void equal(Object expected, Object actual, String what) {
        check(expected.equals(actual), what + "\n expected: " + expected + "\n actual:   " + actual);
    }

    // ---------------------------------------------------------------- Frege

    private static void frege() {
        Random random = new Random(1);
        for (int i = 0; i < 10_000; i++) {
            int minY = random.nextInt(4096) - 2048;
            int off = (random.nextInt(400) - 200) * 16;
            equal(Math.floorDiv(minY - off, 16), Geometry.zBase(minY, off), "zBase is floorDiv");
            double v = (random.nextDouble() - 0.5) * 1e5;
            equal((int) Math.round(v / 16.0) * 16, Geometry.align16(v), "align16 is Math.round");
        }
        // 原来的 Mapping.blockToClient(x, y, z, off) = (x, z + off, -y - 1)
        equal(List.of(-44, -73), List.of(Geometry.blockToClientY(-60, 16), Geometry.blockToClientZ(72)), "blockToClient");
        System.out.println("frege   : geometry agrees with the JDK");
    }

    // ---------------------------------------------------------------- Flix

    private static void flix() {
        FlixBridge.start();
        MaterialRules r = Services.materialRules();
        Map<String, String> wall = Map.of(
                "TORCH", "WALL_TORCH", "SOUL_TORCH", "SOUL_WALL_TORCH", "OAK_SIGN", "OAK_WALL_SIGN",
                "OAK_HANGING_SIGN", "", "WHITE_BANNER", "WHITE_WALL_BANNER", "ZOMBIE_HEAD", "ZOMBIE_WALL_HEAD",
                "PISTON_HEAD", "", "TUBE_CORAL_FAN", "TUBE_CORAL_WALL_FAN", "WALL_TORCH", "", "STONE", "");
        wall.forEach((in, out) -> equal(out, r.wallVariant(in), "wallVariant " + in));
        Map<String, String> floor = Map.of(
                "WALL_TORCH", "TORCH", "OAK_WALL_SIGN", "OAK_SIGN", "OAK_WALL_HANGING_SIGN", "",
                "DEAD_TUBE_CORAL_WALL_FAN", "DEAD_TUBE_CORAL_FAN", "STONE", "");
        floor.forEach((in, out) -> equal(out, r.floorVariant(in), "floorVariant " + in));
        Map<String, String> be = Map.ofEntries(
                Map.entry("chest", "chest"), Map.entry("ender_chest", "ender_chest"),
                Map.entry("oxidized_copper_chest", "copper_chest"), Map.entry("red_bed", "bed"),
                Map.entry("oak_hanging_sign", "hanging_sign"), Map.entry("oak_wall_sign", "sign"),
                Map.entry("red_shulker_box", "shulker_box"), Map.entry("white_wall_banner", "banner"),
                Map.entry("skeleton_skull", "skull"), Map.entry("zombie_wall_head", "skull"),
                Map.entry("piston_head", ""), Map.entry("soul_campfire", "campfire"), Map.entry("oak_shelf", "shelf"),
                Map.entry("spawner", "mob_spawner"), Map.entry("trial_spawner", "trial_spawner"),
                Map.entry("bell", "bell"), Map.entry("stone", ""));
        be.forEach((in, out) -> equal(out, r.blockEntityType(in), "blockEntityType " + in));

        boolean[] keep = Services.retentionPolicy().retain(
                new int[] {0, 2, 0, 2, 10, 10, 5, 5}, new int[] {1, 3, 10, 10, -1}, new int[] {1, 1, 5, 6, 0});
        equal("[true, false, true, false, false]", Arrays.toString(keep), "retention");
        System.out.println("flix    : block name rules + Datalog retention");
    }

    // ---------------------------------------------------------------- Clojure

    private static void clojure() {
        Settings defaults = ClojureBridge.read(ClojureBridge.settingsReader(), Map.of(), 10);
        equal(new Settings(true, 8, 64, 4, 0.8, 112, 80, 14, 24, 24, false, true, false, 32), defaults, "defaults");
        Settings s = ClojureBridge.read(ClojureBridge.settingsReader(), Map.of(
                "view-radius", 50, "fast-speed", 1, "safe-spawn.radius", 2, "debug", true, "columns-per-tick", "x"), 10);
        equal(10, s.viewRadius(), "view-radius is capped by the server view distance");
        equal(1.0, s.fastSpeed(), "int -> double");
        equal(4, s.safeSpawnRadius(), "safe-spawn.radius clamped");
        equal(true, s.debug(), "debug");
        equal(24, s.columnsPerTick(), "wrong type -> default");
        System.out.println("clojure : settings schema");
    }

    // ---------------------------------------------------------------- Haxe

    private static void haxe() {
        Random random = new Random(2);
        for (int round = 0; round < 20; round++) {
            byte[] src = new byte[2048];
            random.nextBytes(src);
            byte[] expected = new byte[2048];
            // 原来的 ChunkStore.rotateNibbles
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int v = nibble(src, (y << 8) | (z << 4) | x);
                int idx = (z << 8) | ((15 - y) << 4) | x;
                expected[idx >> 1] = (byte) ((expected[idx >> 1] & ~(15 << ((idx & 1) << 2))) | (v << ((idx & 1) << 2)));
                // 和 Frege 的 section 内坐标映射是同一个
                check(idx == Geometry.localIndex(x, y, z), "Haxe and Frege agree on the local index");
            }
            check(Arrays.equals(expected, LightJvm.rotate(src)), "rotate nibbles");
        }
        equal(15, LightJvm.uniformValue(LightJvm.filled(15)), "uniform 15");
        equal(0, LightJvm.uniformValue(LightJvm.filled(0)), "uniform 0");
        byte[] a = LightJvm.filled(0);
        LightJvm.set(a, 1234, 9);
        equal(9, LightJvm.get(a, 1234), "set/get");
        equal(-1, LightJvm.uniformValue(a), "not uniform");
        System.out.println("haxe    : light nibbles");
    }

    private static int nibble(byte[] a, int idx) {
        return (a[idx >> 1] >> ((idx & 1) << 2)) & 15;
    }

    // ---------------------------------------------------------------- Fantom

    private static void fantom() throws Exception {
        FantomBridge.start(Files.createTempDirectory("rotatedworld-数据-smoke"));
        // 只有 (3, 64, 0) 能站，身体在 y >= 66 时是空的
        BlockProbe probe = new BlockProbe() {
            @Override
            public Spot standableOn(long bx, long by, long bz) {
                return bx == 3 && by == 64 && bz == 0 ? new Spot(3.5, 64.2, 2.35) : null;
            }

            @Override
            public boolean bodyFree(double x, double y, double z) {
                return y >= 66;
            }

            @Override
            public long maxHeight() {
                return 320;
            }
        };
        Spot spot = Services.spotFinder().standable(probe, 0.5, 64.5, 0.5, 8);
        equal("(3.5, 64.2, 2.4)", String.valueOf(spot), "standable");
        check(Services.spotFinder().standable(probe, 0.5, 64.5, 0.5, 2) == null, "radius respected");
        check(Services.spotFinder().unstuck(probe, 0.5, 70.0, 0.5) == null, "free body is not moved");
        equal("(0.5, 66.0, 0.5)", String.valueOf(Services.spotFinder().unstuck(probe, 0.5, 64.0, 0.5)), "unstuck: straight up first");
        System.out.println("fantom  : spot search");
    }

    // ---------------------------------------------------------------- Kotlin（Frege + PacketEvents）

    private static void kotlinFacade() {
        // 原来手写的方向表：UP->NORTH, DOWN->SOUTH, SOUTH->UP, NORTH->DOWN，现在从 Frege 的旋转推出来
        Map<BlockFace, BlockFace> expected = Map.of(
                BlockFace.UP, BlockFace.NORTH, BlockFace.DOWN, BlockFace.SOUTH, BlockFace.SOUTH, BlockFace.UP,
                BlockFace.NORTH, BlockFace.DOWN, BlockFace.EAST, BlockFace.EAST, BlockFace.WEST, BlockFace.WEST);
        expected.forEach((s, c) -> {
            equal(c, Mapping.INSTANCE.faceToClient(s), "faceToClient " + s);
            equal(s, Mapping.INSTANCE.faceToServer(c), "faceToServer " + c);
        });
        float[] look = Mapping.INSTANCE.lookToClient(30f, 10f);
        float[] back = Mapping.INSTANCE.lookToServer(look[0], look[1]);
        check(Math.abs(back[0] - 30f) < 1e-3 && Math.abs(back[1] - 10f) < 1e-3, "look round trip " + Arrays.toString(back));
        System.out.println("kotlin  : mapping facade");
    }

    // ---------------------------------------------------------------- 依赖服务器的层

    private static void linkUpperLayers() throws Exception {
        String[] classes = {
            "me.leleawa.rotatedworld.engine.ChunkStore",
            "me.leleawa.rotatedworld.engine.ViewManager",
            "me.leleawa.rotatedworld.probe.WorldProbe",
            "me.leleawa.rotatedworld.command.RotateCommand",
            "me.leleawa.rotatedworld.blocks.BlockRotation",
            "me.leleawa.rotatedworld.net.PacketHandler",
            "me.leleawa.rotatedworld.PlayerListener",
            "me.leleawa.rotatedworld.RotatedWorldPlugin",
        };
        for (String name : classes) {
            Class<?> c = Class.forName(name, false, Smoke.class.getClassLoader());
            c.getDeclaredMethods();
            c.getDeclaredFields();
            c.getDeclaredConstructors();
            checks++;
        }
        // Scala -> Haxe（构造时创建光照数组）
        ChunkStore store = new ChunkStore(null);
        equal(Geometry.zBase(-64, 32), store.zBase(-64, 32), "Scala calls Frege");
        // Gosu / Groovy 的运行时初始化
        new me.leleawa.rotatedworld.probe.WorldProbe(null);
        new me.leleawa.rotatedworld.command.RotateCommand(null);
        checks += 2;
        System.out.println("upper   : " + classes.length + " classes linked");
    }

    // ---------------------------------------------------------------- 脚本层（和原来的 Kotlin / Scala 实现逐项对比）

    private static void scripts() throws Exception {
        long t0 = System.nanoTime();
        // 插件的日志：记下所有 WARNING 以上的记录（陪审团意见不一致时会记警告，那说明某个脚本算错了）
        java.util.logging.Logger log = java.util.logging.Logger.getLogger("smoke");
        List<String> warnings = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        // 不用 JUL 默认的 ConsoleHandler（它写 System.err）：插件日志打到 stdout，stderr 里只剩库自己打印的东西
        log.setUseParentHandlers(false);
        log.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord r) {
                System.out.println("[" + r.getLevel() + "] " + r.getMessage());
                if (r.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) warnings.add(r.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        // 加载期间解释器不许往 System.err 打印（Paper 会警告"插件用了 System.err"）：录下来，同时照常输出
        java.io.PrintStream originalErr = System.err;
        java.io.ByteArrayOutputStream stderr = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
                stderr.write(b);
                originalErr.write(b);
            }
        }, true));
        ScriptLayers.Scripts sc;
        try {
            sc = ScriptLayers.load(log, Files.createTempDirectory("rotatedworld-脚本-scripts"),
                    List.of(new me.leleawa.rotatedworld.janitor.KawaJuror()));
        } finally {
            System.setErr(originalErr);
        }
        check(stderr.size() == 0, "script layers printed to System.err while loading: " + stderr);
        System.out.println("scripts : loaded in " + (System.nanoTime() - t0) / 1_000_000 + " ms, jury " + sc.fallJury().jurors());
        Random random = new Random(3);

        // Lua + 陪审团：五个脚本对每个输入都要和原版一致
        for (int i = 0; i < 400; i++) {
            double fall = random.nextDouble() * 60;
            boolean[] flags = new boolean[6];
            if (random.nextInt(3) == 0) flags[random.nextInt(6)] = true;
            boolean exempt = false;
            for (boolean f : flags) exempt |= f;
            double expected = exempt ? 0 : Math.max(Math.ceil(fall - 3.0), 0);
            equal(expected, sc.fallJury().verdict(fall, flags[0], flags[1], flags[2], flags[3], flags[4], flags[5]), "fall verdict " + fall);
            checks++;
            check(unanimous(sc, fall, flags, expected), "jury unanimous");
        }

        check(warnings.isEmpty(), "jury disagreed: " + warnings);

        // JavaScript：卡顿放行
        for (int i = 0; i < 2000; i++) {
            long nanos = (long) (random.nextDouble() * 3e9);
            double dx = random.nextGaussian() * 20, dy = random.nextGaussian() * 20, dz = random.nextGaussian() * 80;
            double ticks = Math.min(nanos / 50_000_000.0, 40.0) + 2;
            boolean expected = Math.hypot(dx, dy) <= ticks + 1 && Math.abs(dz) <= ticks * 3.92 + 2;
            equal(expected, sc.moveAllowance().allowTooQuick(nanos, dx, dy, dz), "allowTooQuick");
        }

        // JRuby：区块柱顺序（原来的 Scala：for dx, dz 生成，按距离稳定排序）
        for (int r = 0; r <= 12; r++) {
            List<int[]> cells = new java.util.ArrayList<>();
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) cells.add(new int[] {dx, dz});
            cells.sort(java.util.Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1]));
            int[] expected = cells.stream().flatMapToInt(java.util.Arrays::stream).toArray();
            check(Arrays.equals(expected, sc.spiral().order(r)), "spiral order r=" + r);
            int[] xs = java.util.stream.IntStream.rangeClosed(0, r).flatMap(i -> i == 0 ? java.util.stream.IntStream.of(0) : java.util.stream.IntStream.of(i, -i)).toArray();
            check(Arrays.equals(xs, sc.spiral().columnsX(r)), "columns x r=" + r);
        }

        // Jython：击退
        for (int i = 0; i < 500; i++) {
            double vx = random.nextGaussian() * 5, vz = random.nextGaussian() * 5;
            double ax = i % 50 == 0 ? vx : random.nextGaussian() * 5, az = i % 50 == 0 ? vz : random.nextGaussian() * 5;
            double r1 = random.nextDouble(), r2 = random.nextDouble();
            double dx = vx - ax, dz = vz - az;
            if (Math.sqrt(dx * dx + dz * dz) < 1e-4) { dx = r1 - 0.5; dz = r2 - 0.5; }
            double n = Math.sqrt(dx * dx + dz * dz);
            double[] got = sc.knockback().velocity(vx, vz, ax, az, r1, r2);
            check(got.length == 3 && Math.abs(got[0] - dx / n * 0.4) < 1e-12 && got[1] == 0.36 && Math.abs(got[2] - dz / n * 0.4) < 1e-12,
                    "knockback " + Arrays.toString(got));
        }

        // Kotlin Script：重新定位决策（原来的 Scala）
        Settings s = new Settings(true, 8, 64, 4, 0.8, 112, 80, 14, 24, 24, false, true, false, 32);
        for (int i = 0; i < 5000; i++) {
            int minY = -64, maxY = 320, center = 128;
            boolean fast = random.nextBoolean();
            double vy = fast ? (random.nextBoolean() ? 1 : -1) : random.nextGaussian();
            double clientY = minY + random.nextDouble() * (maxY - minY);
            double expected =
                    fast && vy < 0 && clientY - minY < s.fastEdgeDistance() ? maxY - s.fastTargetMargin()
                    : fast && vy > 0 && maxY - clientY < s.fastEdgeDistance() ? minY + s.fastTargetMargin()
                    : !fast && Math.abs(clientY - center) > s.recenterThreshold() ? center
                    : Double.NaN;
            equal(expected, sc.recenter().target(fast, vy, clientY, minY, maxY, center, s), "recenter target");
            equal(clientY - minY < 24 || maxY - clientY < 24, sc.recenter().emergency(clientY, minY, maxY), "emergency");
        }
        System.out.println("scripts : jury / JavaScript / JRuby / Jython / Kotlin Script agree with the original");

        // Common Lisp：放行策略（原来的 Kotlin when）
        equal("allow", sc.eventPolicy().failedMove("MOVED_WRONGLY"), "lisp MOVED_WRONGLY");
        equal("ask-javascript", sc.eventPolicy().failedMove("MOVED_TOO_QUICKLY"), "lisp MOVED_TOO_QUICKLY");
        equal("deny", sc.eventPolicy().failedMove("CLIPPED_INTO_BLOCK"), "lisp CLIPPED_INTO_BLOCK");
        equal(true, sc.eventPolicy().forgiveKick("FLYING_PLAYER"), "lisp FLYING_PLAYER");
        equal(false, sc.eventPolicy().forgiveKick("TIMEOUT"), "lisp TIMEOUT");

        // Prolog：/rotate 的参数语法（原来的 Groovy：没有参数 = toggle，未知子命令 = 显示用法，多余参数忽略）
        Map<List<String>, String> grammar = new java.util.LinkedHashMap<>();
        grammar.put(List.of(), "[toggle, ]");
        grammar.put(List.of("status"), "[status, ]");
        grammar.put(List.of("on", "Steve"), "[on, Steve]");
        grammar.put(List.of("unstuck", "玩家_1", "extra", "args"), "[unstuck, 玩家_1]");
        grammar.put(List.of("reload"), "[reload, ]");
        grammar.put(List.of("dance"), "null");
        grammar.forEach((in, out) -> {
            String[] parsed = sc.commandGrammar().parse(in.toArray(new String[0]));
            equal(out, parsed == null ? "null" : Arrays.toString(parsed), "prolog grammar " + in);
        });

        // BeanShell：status 那一行（和原来的 Groovy 输出逐字一致）
        String expected = "§eSteve: offset=-48 (in=-32) gen=7 客户端坐标=" + String.format("(%.1f, %.1f, %.1f)", 1.25, 70.0, -64.3)
                + " 已发区块柱=289 实体=12 缓存区块=4321";
        equal(expected, sc.statusLine().render("Steve", -48, -32, 7, 1.25, 70.0, -64.3, 289, 12, 4321), "beanshell status line");

        // Kawa：缓存清理（假的 Host，只有 settings / allStates；Flix 的 RetentionPolicy 是真的）
        kawaJanitor(s);
        System.out.println("scripts : Common Lisp / Prolog / BeanShell / Kawa agree with the original, jury of " + sc.fallJury().jurors().size());
    }

    private static void kawaJanitor(Settings settings) {
        ChunkStore store = new ChunkStore(null);
        for (int x = -30; x <= 30; x++) for (int z = -60; z <= 60; z++) {
            store.put("w", x, z, new me.leleawa.rotatedworld.engine.RotatedChunk(new java.util.concurrent.atomic.AtomicReferenceArray<>(0)), false);
        }
        store.put("empty", 0, 0, new me.leleawa.rotatedworld.engine.RotatedChunk(new java.util.concurrent.atomic.AtomicReferenceArray<>(0)), false);
        me.leleawa.rotatedworld.kernel.PlayerState st = new me.leleawa.rotatedworld.kernel.PlayerState(java.util.UUID.randomUUID(), null, Runnable::run);
        st.worldKey = "w";
        st.initialized = true;
        st.viewX = 5;
        st.viewZBase = -10;
        st.minY = -64;
        st.maxY = 320;   // 24 个 section
        me.leleawa.rotatedworld.kernel.Host host = (me.leleawa.rotatedworld.kernel.Host) java.lang.reflect.Proxy.newProxyInstance(
                Smoke.class.getClassLoader(), new Class<?>[] {me.leleawa.rotatedworld.kernel.Host.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "settings" -> settings;
                    case "allStates" -> List.of(st);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        // 走插件里同一条路径（PluginContext 包装），在"看不见插件"的上下文类加载器下运行
        RotatedWorldPlugin.janitorTask(host, store, Services.retentionPolicy()).run();
        // 原来的 Scala：x ∈ [viewX - (r+2), viewX + (r+2)]，z ∈ [zb - margin, zb + count + margin]
        int r = settings.viewRadius() + 2;
        int margin = Math.max(settings.prefetchChunks(), settings.prefetchAheadChunks()) + 2;
        int expected = 0;
        for (int x = -30; x <= 30; x++) for (int z = -60; z <= 60; z++) {
            boolean keep = x >= 5 - r && x <= 5 + r && z >= -10 - margin && z <= -10 + 24 + margin;
            check(keep == store.has("w", x, z), "kawa janitor keeps (" + x + ", " + z + ")");
            if (keep) expected++;
        }
        equal(expected, store.size(), "kawa janitor: other worlds cleared, only the view box left");
    }

    private static boolean unanimous(ScriptLayers.Scripts sc, double fall, boolean[] flags, double expected) throws Exception {
        java.lang.reflect.Method m = sc.fallJury().getClass().getDeclaredMethod("unanimous", double.class, boolean[].class, double.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(sc.fallJury(), fall, flags, expected);
    }
}
