package mlang

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.*

/** Groovy, compiled statically ahead of time with the stock `groovyc` (FileSystemCompiler). */
@CacheableTask
abstract class GroovyLangCompile : LangCompile() {
    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @TaskAction
    fun compile() {
        val (out, _) = cleanDirs()
        val files = sources.files.filter { it.extension == "groovy" }.sortedBy { it.path }
        val cp = libraries.files.filter { it.exists() }.joinToString(HostOs.pathSeparator) { it.absolutePath }
        execOps.javaexec {
            executable = launcher.get().executablePath.asFile.absolutePath
            classpath = compilerClasspath
            mainClass.set("org.codehaus.groovy.tools.FileSystemCompiler")
            args("-d", out.absolutePath, "-cp", cp)
            args(files.map { it.absolutePath })
        }
    }
}
