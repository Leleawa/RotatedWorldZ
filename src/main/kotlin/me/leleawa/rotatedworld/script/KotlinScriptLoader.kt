package me.leleawa.rotatedworld.script

import java.nio.file.Path
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

/**
 * Kotlin Script（.kts），运行时用内嵌的 Kotlin 编译器编译，然后就是普通的 JVM 字节码（不是解释执行）。
 * 脚本对插件 jar 编译，所以能用所有层的类；脚本的最后一个表达式就是返回值。
 */
object KotlinScriptLoader {
    @JvmStatic
    fun load(source: String, name: String, pluginJar: Path?, loader: ClassLoader): Any {
        val compilation = ScriptCompilationConfiguration {
            jvm {
                if (pluginJar != null) updateClasspath(listOf(pluginJar.toFile()))
                else dependenciesFromCurrentContext(wholeClasspath = true)
            }
        }
        val evaluation = ScriptEvaluationConfiguration {
            jvm { baseClassLoader(loader) }
        }
        val result = BasicJvmScriptingHost().eval(source.toScriptSource(name), compilation, evaluation)
        val errors = result.reports.filter { it.severity >= ScriptDiagnostic.Severity.ERROR }
        check(errors.isEmpty()) { "Kotlin script $name failed:\n" + errors.joinToString("\n") { it.render() } }
        return (result.valueOrNull()?.returnValue as? ResultValue.Value)?.value
            ?: error("Kotlin script $name must end with an expression")
    }
}
