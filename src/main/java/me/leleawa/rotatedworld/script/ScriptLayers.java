package me.leleawa.rotatedworld.script;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import me.leleawa.rotatedworld.api.ScriptedRules.CommandGrammar;
import me.leleawa.rotatedworld.api.ScriptedRules.EventPolicy;
import me.leleawa.rotatedworld.api.ScriptedRules.FallJuror;
import me.leleawa.rotatedworld.api.ScriptedRules.Knockback;
import me.leleawa.rotatedworld.api.ScriptedRules.MoveAllowance;
import me.leleawa.rotatedworld.api.ScriptedRules.Recenter;
import me.leleawa.rotatedworld.api.ScriptedRules.Spiral;
import me.leleawa.rotatedworld.api.ScriptedRules.StatusLine;
import me.leleawa.rotatedworld.kernel.PluginContext;

/**
 * 编译层之上的脚本层，插件启动时加载，八个解释器并行启动（Kotlin、JRuby、Jython 要好几秒）。
 *
 * <pre>
 *  Kotlin Script  rules.kts   浮动原点的重新定位决策
 *  JRuby          rules.rb    区块柱的发送顺序
 *  JavaScript     rules.js    "移动过快"的卡顿放行
 *  Jython         rules.py    击退
 *  Lua            rules.lua   摔落伤害（首席陪审员）
 *  BeanShell      rules.bsh   /rotate status 的输出
 *  Common Lisp    rules.lisp  哪些服务端检查对旋转玩家放行
 *  Prolog         rules.pl    /rotate 的参数语法（DCG）
 *  全部八个 + 编译的 Kawa      摔落伤害陪审团（FallJury）
 * </pre>
 */
public final class ScriptLayers {
    private ScriptLayers() {}

    /** 加载好的脚本规则。 */
    public record Scripts(Recenter recenter, Spiral spiral, MoveAllowance moveAllowance, Knockback knockback,
                          EventPolicy eventPolicy, CommandGrammar commandGrammar, StatusLine statusLine, FallJury fallJury) {}

    /** @param compiledJurors 编译层里的陪审员（Kawa），排在脚本后面 */
    public static Scripts load(Logger log, Path dataFolder, List<FallJuror> compiledJurors) {
        ClassLoader loader = ScriptLayers.class.getClassLoader();
        Path jar = ScriptSources.codeSource();
        ExecutorService pool = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "RotatedWorld-script-init");
            t.setDaemon(true);
            t.setContextClassLoader(loader);
            return t;
        });
        try {
            var js = start(pool, log, "JavaScript", () ->
                    new RhinoInvoker(ScriptSources.read(dataFolder, "javascript", "rules.js"), "rules.js", loader));
            var kts = start(pool, log, "Kotlin Script", () ->
                    KotlinScriptLoader.load(ScriptSources.read(dataFolder, "kts", "rules.kts"), "rules.kts", jar, loader));
            var rb = start(pool, log, "JRuby", () ->
                    new JRubyInvoker(ScriptSources.read(dataFolder, "jruby", "rules.rb"), "rules.rb", loader));
            var lua = start(pool, log, "Lua", () ->
                    new LuaInvoker(ScriptSources.read(dataFolder, "lua", "rules.lua"), "rules.lua"));
            var py = start(pool, log, "Jython", () ->
                    new JythonInvoker(ScriptSources.read(dataFolder, "jython", "rules.py"), loader, jar));
            var bsh = start(pool, log, "BeanShell", () ->
                    new BeanShellInvoker(ScriptSources.read(dataFolder, "beanshell", "rules.bsh"), loader));
            var lisp = start(pool, log, "Common Lisp", () ->
                    new AbclInvoker(ScriptSources.read(dataFolder, "commonlisp", "rules.lisp"), log));
            var pl = start(pool, log, "Prolog", () ->
                    new PrologInvoker(ScriptSources.read(dataFolder, "prolog", "rules.pl")));

            Object kotlin = kts.join();
            if (!(kotlin instanceof Recenter recenter) || !(kotlin instanceof FallJuror kotlinJuror)) {
                throw new IllegalStateException("rules.kts must end with an object implementing Recenter and FallJuror, got " + kotlin);
            }
            // 脚本在服务器的各种线程上被调用，每次调用都先把上下文类加载器换成插件的
            ScriptInvoker ruby = inPluginContext(rb.join());
            ScriptInvoker javascript = inPluginContext(js.join());
            ScriptInvoker python = inPluginContext(py.join());
            ScriptInvoker luaInvoker = inPluginContext(lua.join());
            ScriptInvoker beanshell = inPluginContext(bsh.join());
            ScriptInvoker commonLisp = inPluginContext(lisp.join());
            ScriptInvoker prolog = inPluginContext(pl.join());

            Spiral spiral = new Spiral() {
                @Override
                public int[] order(int radius) {
                    return ScriptValues.ints(ruby.call("spiral", (long) radius));
                }

                @Override
                public int[] columnsX(int radius) {
                    return ScriptValues.ints(ruby.call("columns_x", (long) radius));
                }
            };
            MoveAllowance move = (nanos, dx, dy, dz) ->
                    ScriptValues.bool(javascript.call("allowTooQuick", (double) nanos, dx, dy, dz));
            Knockback knockback = (vx, vz, ax, az, r1, r2) ->
                    ScriptValues.doubles(python.call("knockback", vx, vz, ax, az, r1, r2));
            EventPolicy events = new EventPolicy() {
                @Override
                public String failedMove(String reason) {
                    return String.valueOf(commonLisp.call("failed-move", reason));
                }

                @Override
                public boolean forgiveKick(String cause) {
                    return ScriptValues.bool(commonLisp.call("forgive-kick", cause));
                }
            };
            CommandGrammar grammar = args -> {
                Object parsed = prolog.call("rotate_command", (Object) args);
                if (parsed == null) return null;
                List<?> cmd = (List<?>) parsed;
                return new String[] {String.valueOf(cmd.get(0)), String.valueOf(cmd.get(1))};
            };
            StatusLine status = (player, outOff, inOff, gen, cx, cy, cz, sent, entities, cached) ->
                    String.valueOf(beanshell.call("statusLine", player, outOff, inOff, gen, cx, cy, cz, sent, entities, cached));

            List<FallJuror> jurors = new ArrayList<>(List.of(
                    FallJury.juror("Lua", luaInvoker, "fallDamage"),
                    FallJury.juror("JavaScript", javascript, "fallDamage"),
                    FallJury.juror("JRuby", ruby, "fall_damage"),
                    FallJury.juror("Jython", python, "fall_damage"),
                    kotlinJuror,
                    FallJury.juror("BeanShell", beanshell, "fallDamage"),
                    FallJury.juror("Common Lisp", commonLisp, "fall-damage"),
                    FallJury.juror("Prolog", prolog, "fall_damage")));
            jurors.addAll(compiledJurors);
            return new Scripts(recenter, spiral, move, knockback, events, grammar, status, new FallJury(jurors, log));
        } finally {
            pool.shutdown();
        }
    }

    private static ScriptInvoker inPluginContext(ScriptInvoker invoker) {
        return (function, args) -> PluginContext.call(() -> invoker.call(function, args));
    }

    private interface Loader<T> {
        T load() throws Exception;
    }

    private static <T> CompletableFuture<T> start(ExecutorService pool, Logger log, String name, Loader<T> loader) {
        return CompletableFuture.supplyAsync(() -> {
            long t0 = System.nanoTime();
            try {
                T loaded = loader.load();
                log.info(name + " 脚本层加载用时 " + (System.nanoTime() - t0) / 1_000_000 + " ms");
                return loaded;
            } catch (Exception e) {
                throw new IllegalStateException(name + " 脚本层加载失败", e);
            }
        }, pool);
    }
}
