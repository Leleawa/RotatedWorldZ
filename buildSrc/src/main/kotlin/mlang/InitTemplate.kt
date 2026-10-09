package mlang

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

/**
 * Turns the template into *your* plugin:
 *
 *     ./gradlew initTemplate --package=com.acme.myplugin --plugin-name=MyPlugin
 *
 * It renames the root package in every language (directories and source text), sets the plugin name in
 * `plugin.yml` / `settings.gradle.kts` and the Gradle `group`.  Re-runnable; the current package is read from
 * the directory that contains the shared `api` package.  Commit (or stash) first - it rewrites files in place.
 */
abstract class InitTemplate : DefaultTask() {
    @get:Input
    @get:Option(option = "package", description = "New root package, e.g. com.acme.myplugin (lower case, no dashes or underscores)")
    abstract val newPackage: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "plugin-name", description = "Plugin name for plugin.yml and the jar, e.g. MyPlugin")
    abstract val pluginName: Property<String>

    private val sourceRoots = listOf(
        "src/api/java", "src/main/java", "src/main/kotlin", "src/main/scala", "src/main/gosu",
        "src/main/groovy", "src/main/kawa", "src/main/xtend", "src/main/haxe", "src/main/frege", "src/main/clojure",
    )
    private val textExtensions = setOf("java", "kt", "scala", "gs", "gsx", "gst", "groovy", "xtend", "hx", "clj", "fan", "flix", "fr", "kts", "yml", "md", "js", "rb", "lua", "py", "scm", "bsh", "lisp", "pl")

    @TaskAction
    fun init() {
        val root = project.projectDir
        val newPkg = newPackage.get()
        require(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*").matches(newPkg)) {
            "Invalid package '$newPkg': use lower case letters and digits, separated by dots (Clojure and Fantom dislike more)"
        }
        val oldPkg = currentPackage(root)
        val name = pluginName.orNull

        if (oldPkg != newPkg) {
            logger.lifecycle("Renaming package $oldPkg -> $newPkg")
            moveDirectories(root, oldPkg, newPkg)
            rewriteText(root, oldPkg, newPkg)
        }
        if (name != null) {
            require(Regex("[A-Za-z][A-Za-z0-9_]*").matches(name)) { "Invalid plugin name '$name'" }
            logger.lifecycle("Plugin name -> $name")
            edit(File(root, "src/main/resources/plugin.yml")) { it.replace(Regex("(?m)^name: .*$"), "name: $name") }
            edit(File(root, "settings.gradle.kts")) { it.replace(Regex("rootProject\\.name = \"[^\"]*\""), "rootProject.name = \"$name\"") }
        }
        edit(File(root, "build.gradle.kts")) { it.replace(Regex("(?m)^group = \"[^\"]*\""), "group = \"$newPkg\"") }
        logger.lifecycle("Done. Run ./gradlew build to check.")
    }

    /** The directory that contains the `api` package is the root package. */
    private fun currentPackage(root: File): String {
        val base = File(root, "src/api/java")
        val api = base.walkTopDown().firstOrNull { it.isDirectory && it.name == "api" } ?: error("src/api/java has no 'api' package")
        return api.parentFile.relativeTo(base).invariantSeparatorsPath.replace('/', '.')
    }

    private fun moveDirectories(root: File, oldPkg: String, newPkg: String) {
        val oldPath = oldPkg.replace('.', '/')
        val newPath = newPkg.replace('.', '/')
        for (sr in sourceRoots) {
            val base = File(root, sr)
            val from = File(base, oldPath)
            if (!from.isDirectory) continue
            val to = File(base, newPath)
            // move through a temp name so that nested targets (a.b -> a.b.c) work
            val tmp = File(base, ".init-tmp")
            from.renameTo(tmp).also { check(it) { "Could not move $from" } }
            deleteEmptyParents(from.parentFile, base)
            to.parentFile.mkdirs()
            tmp.renameTo(to).also { check(it) { "Could not move to $to" } }
        }
    }

    private fun deleteEmptyParents(start: File?, stop: File) {
        var dir = start
        while (dir != null && dir != stop && dir.isDirectory && dir.list().isNullOrEmpty()) {
            dir.delete()
            dir = dir.parentFile
        }
    }

    private fun rewriteText(root: File, oldPkg: String, newPkg: String) {
        val pattern = Regex("(?<![\\w.])" + Regex.escape(oldPkg) + "\\.(?=[A-Za-z])")
        val replacement = Regex.escapeReplacement("$newPkg.")
        // `package template;` - the root package itself has no trailing dot
        val rootDecl = Regex("(?m)^(\\s*package\\s+)" + Regex.escape(oldPkg) + "(?=\\s*;?\\s*\$)")
        val files = (sourceRoots.map { File(root, it) } + File(root, "src/main/resources") + File(root, "src/main/fantom") + File(root, "src/main/flix") +
            listOf("javascript", "kts", "jruby", "lua", "jython", "beanshell", "commonlisp", "prolog").map { File(root, "src/main/$it") })
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in textExtensions }.toList() } +
            listOf(File(root, "build.gradle.kts"), File(root, "src/main/resources/plugin.yml"))
        files.distinct().filter { it.isFile }.forEach { f -> edit(f) { it.replace(pattern, replacement).replace(rootDecl) { m -> m.groupValues[1] + newPkg } } }
    }

    private fun edit(file: File, transform: (String) -> String) {
        if (!file.isFile) return
        val before = file.readText()
        val after = transform(before)
        if (after != before) file.writeText(after)
    }
}
