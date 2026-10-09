package mlang

import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

object HostOs {
    val windows: Boolean = System.getProperty("os.name").lowercase().contains("win")
    val mac: Boolean = System.getProperty("os.name").lowercase().contains("mac")
    val pathSeparator: String = File.pathSeparator

    /** `haxe.exe` on Windows, `haxe` elsewhere. */
    fun exe(name: String) = if (windows) "$name.exe" else name
}

/** Downloads [url] to [target] (once). The tools end up in `.tools/`, which is git-ignored. */
abstract class DownloadTool : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:OutputFile
    abstract val target: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun download() {
        val dest = target.get().asFile
        dest.parentFile.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        logger.lifecycle("Downloading ${url.get()}")
        URI(url.get()).toURL().openStream().use { Files.copy(it, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * Base class of every "foreign" language compiler (the ones Gradle has no native plugin for).
 *
 *  - [sources]   the language's own source files
 *  - [libraries] classes / jars the language is allowed to link against (Bukkit, `api`, lower layers ...)
 *  - [outputDir] compiled JVM classes, packaged into the plugin jar
 */
abstract class LangCompile : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    abstract val sources: ConfigurableFileCollection

    @get:Classpath
    abstract val libraries: ConfigurableFileCollection

    @get:Nested
    abstract val launcher: Property<JavaLauncher>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Scratch directory, wiped on every run. */
    @get:Internal
    abstract val workDir: DirectoryProperty

    @get:Inject
    abstract val execOps: ExecOperations

    @get:Inject
    abstract val fsOps: FileSystemOperations

    @get:Inject
    abstract val archives: ArchiveOperations

    protected fun javaBin(): File = launcher.get().executablePath.asFile.parentFile

    protected fun cleanDirs(): Pair<File, File> {
        val out = outputDir.get().asFile
        val work = workDir.get().asFile
        out.deleteRecursively(); out.mkdirs()
        work.deleteRecursively(); work.mkdirs()
        return out to work
    }

    protected fun pathWithJava(): String = javaBin().absolutePath + File.pathSeparator + (System.getenv("PATH") ?: "")
}

/** Packs a class directory into a jar (some compilers only accept jars on their library path). */
internal fun zipDirectory(dir: File, jar: File) {
    jar.parentFile.mkdirs()
    java.util.zip.ZipOutputStream(jar.outputStream().buffered()).use { zip ->
        dir.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { f ->
            val name = f.relativeTo(dir).invariantSeparatorsPath
            zip.putNextEntry(java.util.zip.ZipEntry(name).apply { time = 0 })
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

/** Library entries as jars: jars are returned untouched, directories are zipped into [scratch]. */
internal fun Collection<File>.asJars(scratch: File): List<File> {
    scratch.mkdirs()
    return filter { it.exists() }.mapIndexed { i, f ->
        if (f.isDirectory) File(scratch, "dir$i-${f.parentFile.parentFile.name}-${f.name}.jar").also { zipDirectory(f, it) } else f
    }
}
