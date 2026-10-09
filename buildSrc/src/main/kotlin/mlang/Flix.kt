package mlang

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

/**
 * Flix.  Flix has no "export this function to Java" feature; the only entry point of a Flix program
 * is its `--entrypoint` function.  Ours registers Flix objects (implementations of Java interfaces)
 * in `template.api.ModuleRegistry`, and Java reaches them through that registry.
 *
 * Flix resolves Java classes from the jars in `lib/external/`.  Jars normally arrive there through
 * the `[jar-dependencies]` section of flix.toml (downloaded from a URL).  We pre-place our own jars
 * and declare placeholder URLs: Flix sees the files as "cached" and never downloads anything.
 */
@CacheableTask
abstract class FlixCompile : LangCompile() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val flixJar: RegularFileProperty

    @get:Input
    abstract val flixVersion: Property<String>

    /** Fully qualified Flix function used as program entry point, e.g. `Plugin.init`. */
    @get:Input
    abstract val entrypoint: Property<String>

    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    @TaskAction
    fun compile() {
        val (out, work) = cleanDirs()
        val project = work.resolve("project")
        val src = project.resolve("src")
        fsOps.copy {
            from(sourceRoot)
            include("**/*.flix")
            into(src)
        }
        val jars = libraries.files.asJars(work.resolve("jars"))
        val ext = project.resolve("lib/external").also { it.mkdirs() }
        val deps = StringBuilder()
        jars.forEachIndexed { i, jar ->
            val name = "dep$i-${jar.name}"
            jar.copyTo(ext.resolve(name), overwrite = true)
            deps.appendLine("\"$name\" = \"url:https://example.invalid/placeholder/$name\"")
        }
        project.resolve("flix.toml").writeText(
            """
            [package]
            version = "0.1.0"
            flix    = "${flixVersion.get()}"

            [jar-dependencies]
            $deps
            """.trimIndent()
        )
        execOps.exec {
            workingDir = project
            commandLine(
                launcher.get().executablePath.asFile.absolutePath,
                "-jar", flixJar.get().asFile.absolutePath,
                "build-classes", "--entrypoint", entrypoint.get(), "--no-install",
            )
        }
        fsOps.copy {
            from(project.resolve("build/class"))
            into(out)
        }
    }
}
