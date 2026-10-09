// Kotlin Script 层：浮动原点的重新定位决策（原来在 Scala 的 ViewManager 里）。
// 运行时由内嵌的 Kotlin 编译器编译成字节码，所以每 tick 每个玩家调用一次也没关系。
// 最后一个表达式是这个脚本的返回值：一个同时实现 Recenter 和 FallJuror 的对象（不能是匿名对象）。
import me.leleawa.rotatedworld.api.ScriptedRules
import me.leleawa.rotatedworld.api.Settings
import kotlin.math.abs
import kotlin.math.ceil

class Rules : ScriptedRules.Recenter, ScriptedRules.FallJuror {

    // 浮动原点：
    //  - 慢速：离窗口中心太远就放回中心
    //  - 高速：快到运动方向那一侧的边缘时，把玩家放到窗口另一端，前方留出尽量多的空间，
    //    这样高速下落时重新定位的次数少得多
    override fun target(fast: Boolean, vy: Double, clientY: Double, minY: Int, maxY: Int, center: Int, settings: Settings): Double =
        when {
            fast && vy < 0 && clientY - minY < settings.fastEdgeDistance() -> (maxY - settings.fastTargetMargin()).toDouble()
            fast && vy > 0 && maxY - clientY < settings.fastEdgeDistance() -> (minY + settings.fastTargetMargin()).toDouble()
            !fast && abs(clientY - center) > settings.recenterThreshold() -> center.toDouble()
            else -> Double.NaN
        }

    // 快掉出窗口了但前方数据还没好：先用空气占位也要挪，不能让玩家掉到窗口外面
    override fun emergency(clientY: Double, minY: Int, maxY: Int): Boolean = clientY - minY < 24 || maxY - clientY < 24

    // ---------------------------------------------------------------- 摔落伤害陪审团的一员

    override fun juror() = "Kotlin Script"

    override fun fallDamage(fall: Double, creative: Boolean, spectator: Boolean, flying: Boolean, gliding: Boolean,
                            inWater: Boolean, slowFalling: Boolean): Double =
        if (creative || spectator || flying || gliding || inWater || slowFalling) 0.0
        else ceil(fall - 3.0).coerceAtLeast(0.0)
}

Rules()
