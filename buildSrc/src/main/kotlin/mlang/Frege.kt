package mlang

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*

/**
 * Frege (Haskell for the JVM). The Frege compiler translates `.fr` to Java and then calls `javac`
 * itself, which is why the toolchain's `bin` directory is put first on the PATH.
 */
@CacheableTask
abstract class FregeCompile : LangCompile() {
    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    @TaskAction
    fun compile() {
        val (out, _) = cleanDirs()
        val files = sources.files.filter { it.extension == "fr" }.sortedBy { it.path }
        execOps.javaexec {
            executable = launcher.get().executablePath.asFile.absolutePath
            classpath = compilerClasspath
            mainClass.set("frege.compiler.Main")
            jvmArgs("-Xss16m")
            val args = mutableListOf("-d", out.absolutePath, "-sp", sourceRoot.get().asFile.absolutePath)
            val fp = libraries.files.filter { it.exists() }.joinToString(HostOs.pathSeparator) { it.absolutePath }
            if (fp.isNotEmpty()) args += listOf("-fp", fp)
            args += files.map { it.absolutePath }
            args(args)
            environment("PATH", pathWithJava())
        }
        // Frege leaves the generated Java next to the classes; it is not needed in the jar.
        out.walkTopDown().filter { it.extension == "java" }.forEach { it.delete() }
    }
}
