package mlang

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*

/**
 * Clojure, ahead-of-time compiled (`clojure.lang.Compile`).  A namespace that uses `(:gen-class)` becomes a real
 * Java class, so other languages can instantiate it and call it like any other class.
 */
@CacheableTask
abstract class ClojureCompile : LangCompile() {
    @get:javax.inject.Inject
    abstract val objects: org.gradle.api.model.ObjectFactory

    @get:Classpath
    abstract val compilerClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    @TaskAction
    fun compile() {
        val (out, _) = cleanDirs()
        val root = sourceRoot.get().asFile
        val namespaces = sources.files.filter { it.extension == "clj" }.sortedBy { it.path }
            .map { it.relativeTo(root).invariantSeparatorsPath.removeSuffix(".clj").replace('/', '.').replace('_', '-') }
        val cp = (listOf(root) + libraries.files.filter { it.exists() }).joinToString(HostOs.pathSeparator) { it.absolutePath }
        execOps.javaexec {
            executable = launcher.get().executablePath.asFile.absolutePath
            classpath = compilerClasspath + objects.fileCollection().from(cp.split(HostOs.pathSeparator))
            mainClass.set("clojure.lang.Compile")
            systemProperty("clojure.compile.path", out.absolutePath)
            args(namespaces)
        }
    }
}
