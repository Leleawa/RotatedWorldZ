package mlang

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Fantom.  A Fantom build produces `.pod` files (fcode), not class files; the Fantom runtime
 * (`sys.jar`) turns them into classes at run time.  This task therefore produces
 *
 *  - [outputDir]   the Fantom runtime classes (`fan.sys.*`, `fanx.*`), so Java can call the runtime API
 *  - [homeZip]     a minimal "Fantom home" (sys.pod + our pods + etc/) which the plugin unpacks at start-up
 *
 * Fantom can use Java classes through its Java FFI; the jars in [libraries] are made known to it as `lib/java/ext`.
 */
@CacheableTask
abstract class FantomCompile : LangCompile() {
    @get:Internal
    abstract val fantomHome: DirectoryProperty

    /** Directory containing `build.fan` (and the `fan/` sources it refers to). */
    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    @get:OutputFile
    abstract val homeZip: RegularFileProperty

    @TaskAction
    fun compile() {
        val (out, work) = cleanDirs()
        val home = work.resolve("home")
        fsOps.copy {
            from(fantomHome) { include("lib/**", "etc/**") }
            into(home)
        }
        val before = home.resolve("lib/fan").list()!!.toSet()

        val ext = home.resolve("lib/java/ext").also { it.mkdirs() }
        libraries.files.asJars(work.resolve("jars")).forEachIndexed { i, jar -> jar.copyTo(ext.resolve("dep$i-${jar.name}")) }

        val src = work.resolve("src")
        fsOps.copy { from(sourceRoot); into(src) }
        val sysJar = home.resolve("lib/java/sys.jar")
        execOps.exec {
            workingDir = src
            environment("FAN_HOME", home.absolutePath)
            commandLine(
                launcher.get().executablePath.asFile.absolutePath,
                "-cp", sysJar.absolutePath,
                "-Dfan.home=${home.absolutePath}",
                "fanx.tools.Fan", src.resolve("build.fan").absolutePath,
            )
        }

        val ownPods = home.resolve("lib/fan").list()!!.filter { it !in before }
        check(ownPods.isNotEmpty()) { "The Fantom build did not produce any pod" }

        fsOps.copy {
            from(archives.zipTree(sysJar))
            exclude("META-INF/**")
            into(out)
        }

        val zip = homeZip.get().asFile
        zip.parentFile.mkdirs()
        ZipOutputStream(zip.outputStream().buffered()).use { z ->
            fun add(name: String, file: File) {
                z.putNextEntry(ZipEntry(name).apply { time = 0 })
                file.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
            add("lib/fan/sys.pod", home.resolve("lib/fan/sys.pod"))
            ownPods.sorted().forEach { add("lib/fan/$it", home.resolve("lib/fan/$it")) }
            home.resolve("etc/sys").walkTopDown().filter { it.isFile }.sortedBy { it.path }
                .forEach { add("etc/sys/" + it.relativeTo(home.resolve("etc/sys")).invariantSeparatorsPath, it) }
        }
    }
}
