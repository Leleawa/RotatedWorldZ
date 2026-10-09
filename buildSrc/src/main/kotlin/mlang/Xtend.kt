package mlang

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*

/**
 * Xtend: `.xtend` -> `.java`. The generated Java is fed to the normal `compileJava` of the `main` source set.
 * Plain Java sources in [javaSourceRoots] are visible to the Xtend compiler (so Xtend can call Java and the other way round).
 *
 * The Xtend batch compiler insists on source and output folders having a common, not too shallow parent
 * (and mis-handles Windows separators), so everything is staged in a scratch workspace first.
 */
@CacheableTask
abstract class XtendCompile : LangCompile() {
    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val javaSourceRoots: ConfigurableFileCollection

    @TaskAction
    fun compile() {
        val (out, work) = cleanDirs()
        val ws = work.resolve("ws")
        val xtendDir = ws.resolve("xtend")
        val javaDir = ws.resolve("java")
        val genDir = ws.resolve("gen")
        fsOps.copy { from(sourceRoot); into(xtendDir) }
        javaSourceRoots.files.filter { it.isDirectory }.forEach { root -> fsOps.copy { from(root); into(javaDir) } }
        javaDir.mkdirs()
        genDir.mkdirs()

        val cp = libraries.files.filter { it.exists() }.joinToString(HostOs.pathSeparator) { it.absolutePath }
        execOps.javaexec {
            executable = launcher.get().executablePath.asFile.absolutePath
            classpath = compilerClasspath
            mainClass.set("org.eclipse.xtend.core.compiler.batch.Main")
            args(
                "-cp", cp,
                "-d", genDir.invariantSeparatorsPath,
                "-encoding", "UTF-8",
                "-javaSourceVersion", "21",
                listOf(xtendDir, javaDir).joinToString(HostOs.pathSeparator) { it.invariantSeparatorsPath },
            )
        }
        fsOps.copy { from(genDir); into(out) }
    }
}
