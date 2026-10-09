package mlang

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*

/**
 * Kawa Scheme, compiled ahead of time (`kawa.repl -C`).  `define-simple-class` produces a plain Java class,
 * so other languages can instantiate it like any other class.
 */
@CacheableTask
abstract class KawaCompile : LangCompile() {
    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    @TaskAction
    fun compile() {
        val (out, _) = cleanDirs()
        val files = sources.files.filter { it.extension == "scm" }.sortedBy { it.path }
        execOps.javaexec {
            executable = launcher.get().executablePath.asFile.absolutePath
            // Kawa resolves the Java classes a module imports through its own class path
            classpath = compilerClasspath + libraries.filter { it.exists() }
            mainClass.set("kawa.repl")
            workingDir = sourceRoot.get().asFile
            args("-d", out.absolutePath, "-C")
            args(files.map { it.relativeTo(sourceRoot.get().asFile).path })
        }
    }
}
