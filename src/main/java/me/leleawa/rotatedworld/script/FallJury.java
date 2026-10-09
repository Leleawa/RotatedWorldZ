package me.leleawa.rotatedworld.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import me.leleawa.rotatedworld.api.ScriptedRules.FallJuror;
import me.leleawa.rotatedworld.kernel.PluginContext;

/**
 * 摔落伤害陪审团：八种脚本语言加上编译的 Kawa 各算一遍，少数服从多数，平票时听第一位陪审员（Lua）的。
 * 意见不一致时记一条警告（说明某个脚本被改坏了）。在玩家线程上调用，一次落地一次，冷路径。
 */
public final class FallJury {
    private final List<FallJuror> jurors;
    private final Logger log;

    FallJury(List<FallJuror> jurors, Logger log) {
        this.jurors = List.copyOf(jurors);
        this.log = log;
    }

    public List<String> jurors() {
        return jurors.stream().map(FallJuror::juror).toList();
    }

    public double verdict(double fall, boolean creative, boolean spectator, boolean flying, boolean gliding,
                          boolean inWater, boolean slowFalling) {
        // 在玩家线程上调用：Kawa 陪审员的运行时要能用上下文类加载器找到插件的类
        return PluginContext.call(() -> vote(fall, creative, spectator, flying, gliding, inWater, slowFalling));
    }

    private double vote(double fall, boolean creative, boolean spectator, boolean flying, boolean gliding,
                        boolean inWater, boolean slowFalling) {
        Map<Double, List<String>> votes = new LinkedHashMap<>();
        for (FallJuror j : jurors) {
            double d;
            try {
                d = j.fallDamage(fall, creative, spectator, flying, gliding, inWater, slowFalling);
            } catch (RuntimeException e) {
                log.warning("陪审员 " + j.juror() + " 弃权: " + e);
                continue;
            }
            // ceil(2.5 - 3) 是 -0.0：Python 的 max、Kotlin 的 coerceAtLeast、Scheme 的 max 都会原样留着它，
            // 而 Double 作为键时 -0.0 和 0.0 是两个键。+ 0.0 把 -0.0 变成 0.0，这样它们算同一个意见。
            votes.computeIfAbsent(d + 0.0, k -> new ArrayList<>()).add(j.juror());
        }
        if (votes.isEmpty()) return 0;
        Map.Entry<Double, List<String>> winner = null;
        for (Map.Entry<Double, List<String>> e : votes.entrySet()) {
            if (winner == null || e.getValue().size() > winner.getValue().size()) winner = e;
        }
        if (votes.size() > 1) {
            log.warning("摔落伤害陪审团意见不一（摔落 " + fall + " 格）: " + votes + "，采纳 " + winner.getKey());
        }
        return winner.getKey();
    }

    /** 测试用：所有陪审员意见一致吗。 */
    boolean unanimous(double fall, boolean[] flags, double expected) {
        for (FallJuror j : jurors) {
            double d = j.fallDamage(fall, flags[0], flags[1], flags[2], flags[3], flags[4], flags[5]);
            if (d != expected) {
                throw new AssertionError("juror " + j.juror() + " says " + d + " for fall " + fall + ", expected " + expected);
            }
        }
        return true;
    }

    static FallJuror juror(String name, ScriptInvoker invoker, String function) {
        return new FallJuror() {
            @Override
            public String juror() {
                return name;
            }

            @Override
            public double fallDamage(double fall, boolean creative, boolean spectator, boolean flying, boolean gliding,
                                     boolean inWater, boolean slowFalling) {
                return ScriptValues.num(invoker.call(function, fall, creative, spectator, flying, gliding, inWater, slowFalling));
            }
        };
    }

}
