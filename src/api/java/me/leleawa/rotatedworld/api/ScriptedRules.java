package me.leleawa.rotatedworld.api;

/**
 * 运行时由脚本层（JavaScript、Kotlin Script、JRuby、Lua、Jython）实现的规则。
 * 脚本在插件启动时加载，{@code plugins/RotatedWorldZ/scripts/<语言>/} 里的同名文件会覆盖 jar 里的那份。
 */
public final class ScriptedRules {
    private ScriptedRules() {}

    /** Kotlin Script：浮动原点的重新定位决策（每 tick 每个玩家都调用；KTS 是编译执行的）。 */
    public interface Recenter {
        /** 想把玩家放到的客户端高度；不需要重新定位时返回 NaN。 */
        double target(boolean fast, double vy, double clientY, int minY, int maxY, int center, Settings settings);

        /** 快掉出窗口了：数据没准备好也要挪。 */
        boolean emergency(double clientY, int minY, int maxY);
    }

    /** JRuby：区块柱的发送顺序。 */
    public interface Spiral {
        /** (dx, dz) 按距离由近到远，展平成 [dx0, dz0, dx1, dz1, ...]。 */
        int[] order(int radius);

        /** 预取服务端区块时 x 方向的顺序：0, 1, -1, 2, -2 ... */
        int[] columnsX(int radius);
    }

    /** JavaScript："移动过快"是不是卡顿造成的。 */
    public interface MoveAllowance {
        /** dx, dy：客户端水平方向（服务端 x / y）；dz：客户端竖直方向（服务端 z）。 */
        boolean allowTooQuick(long nanosSinceAcceptedMove, double dx, double dy, double dz);
    }

    /** Jython：在客户端坐标系里算击退。 */
    public interface Knockback {
        /** 返回客户端坐标系里的速度 [x, y, z]；r1、r2 是 [0, 1) 的随机数（两人重合时用）。 */
        double[] velocity(double victimX, double victimZ, double attackerX, double attackerZ, double r1, double r2);
    }

    /** Common Lisp：服务端按没旋转的世界做的检查，对旋转玩家哪些放行。 */
    public interface EventPolicy {
        /** PlayerFailMoveEvent 的原因（枚举名）-> "allow"（放行）、"ask-javascript"（问 MoveAllowance）或 "deny"。 */
        String failedMove(String reason);

        /** PlayerKickEvent 的原因（枚举名）-> 是否取消这次踢出。 */
        boolean forgiveKick(String cause);
    }

    /** Prolog：/rotate 的参数语法（DCG）。 */
    public interface CommandGrammar {
        /** 参数（第一个已转小写）-> [动作, 目标玩家名或 ""]；不合语法时返回 null。 */
        String[] parse(String[] args);
    }

    /** BeanShell：/rotate status 的那一行字。 */
    public interface StatusLine {
        String render(String player, int outOff, int inOff, int generation, double clientX, double clientY, double clientZ,
                      int sentColumns, int entities, int cachedChunks);
    }

    /** 所有脚本层（再加上编译的 Kawa）都实现：摔落伤害（陪审团投票，见 FallJury）。 */
    public interface FallJuror {
        String juror();

        /** 返回伤害值，0 表示不受伤。 */
        double fallDamage(double fall, boolean creative, boolean spectator, boolean flying, boolean gliding,
                          boolean inWater, boolean slowFalling);
    }
}
