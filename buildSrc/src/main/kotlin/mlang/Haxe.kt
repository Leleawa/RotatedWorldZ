package mlang

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLauncher
import java.io.File

/**
 * Haxe -> JVM bytecode (`--jvm`, no hxjava needed).
 *
 * Quirks handled here:
 *  - Haxe's Java extern loader only understands a JDK 8 `rt.jar`, so a Java 8 launcher is required
 *    (Gradle downloads one through the toolchain resolver if there is none).
 *  - `haxe` calls `haxelib` on start-up; the official zip ships a haxelib that needs Neko. We put a tiny stub first in PATH.
 */
@CacheableTask
abstract class HaxeCompile : LangCompile() {
    @get:Internal
    abstract val haxeHome: DirectoryProperty

    @get:Nested
    abstract val legacyLauncher: Property<JavaLauncher>

    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    private fun findHaxe(home: File): File =
        home.walkTopDown().firstOrNull { it.isFile && it.name == HostOs.exe("haxe") }
            ?: error("No ${HostOs.exe("haxe")} found below $home")

    private fun jdk8Libs(): List<File> {
        val java8Home = legacyLauncher.get().metadata.installationPath.asFile
        val libs = listOf("rt.jar", "jce.jar").map { name ->
            listOf(java8Home.resolve("jre/lib/$name"), java8Home.resolve("lib/$name")).firstOrNull { it.isFile }
                ?: error("$name not found in the Java 8 installation $java8Home")
        }
        return libs
    }

    @TaskAction
    fun compile() {
        val (out, work) = cleanDirs()
        val haxe = findHaxe(haxeHome.get().asFile)
        val std = haxe.parentFile.resolve("std")

        // stub `haxelib`
        val stub = work.resolve("stub").also { it.mkdirs() }
        val repo = work.resolve("haxelib").also { it.mkdirs() }
        if (HostOs.windows) {
            stub.resolve("haxelib.bat").writeText("@echo ${repo.absolutePath}\r\n")
        } else {
            stub.resolve("haxelib").apply { writeText("#!/bin/sh\necho '${repo.absolutePath}'\n"); setExecutable(true) }
        }

        val root = sourceRoot.get().asFile
        val types = sources.files.filter { it.extension == "hx" }.sortedBy { it.path }
            .map { it.relativeTo(root).invariantSeparatorsPath.removeSuffix(".hx").replace('/', '.') }
        val jar = work.resolve("haxe-out.jar")
        val args = mutableListOf(haxe.absolutePath, "-cp", root.absolutePath, "-dce", "no")
        (jdk8Libs() + libraries.files.asJars(work.resolve("jars"))).forEach { args += listOf("--java-lib", it.absolutePath) }
        args += listOf("--jvm", jar.absolutePath)
        args += types
        execOps.exec {
            commandLine(args)
            environment("PATH", stub.absolutePath + File.pathSeparator + haxe.parentFile.absolutePath + File.pathSeparator + System.getenv("PATH"))
            environment("HAXE_STD_PATH", std.absolutePath)
            environment("HAXEPATH", haxe.parentFile.absolutePath)
        }
        fsOps.copy {
            from(archives.zipTree(jar))
            exclude("META-INF/MANIFEST.MF")
            into(out)
        }
    }
}
