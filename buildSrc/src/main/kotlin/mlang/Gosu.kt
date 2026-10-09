package mlang

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*

/** Gosu: compiled with the stock `gosuc` command line compiler. */
@CacheableTask
abstract class GosuCompile : LangCompile() {
    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    @TaskAction
    fun compile() {
        val (out, _) = cleanDirs()
        val files = sources.files.filter { it.extension == "gs" || it.extension == "gsx" || it.extension == "gst" }.sortedBy { it.path }
        val cp = libraries.files.filter { it.exists() }.joinToString(HostOs.pathSeparator) { it.absolutePath }
        execOps.javaexec {
            executable = launcher.get().executablePath.asFile.absolutePath
            classpath = compilerClasspath
            mainClass.set("gw.lang.gosuc.cli.CommandLineCompiler")
            args("-d", out.absolutePath, "-classpath", cp, "-sourcepath", sourceRoot.get().asFile.absolutePath)
            args(files.map { it.absolutePath })
        }
        // gosuc copies the .gs sources next to the classes; only needed for Gosu's own reflection.
    }
}
