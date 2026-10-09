import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import mlang.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    scala
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "me.leleawa"
version = "1.0.0"

// ---------------------------------------------------------------------------------------------
// Versions
// ---------------------------------------------------------------------------------------------
val minecraftRelease = "1.21.11"
val paperApi = "io.papermc.paper:paper-api:$minecraftRelease-R0.1-SNAPSHOT"
val packetEventsApi = "com.github.retrooper:packetevents-spigot:2.13.0"
val scalaRelease = "3.10.0"
val fregeRelease = "3.25.153"
val clojureRelease = "1.12.6"
val groovyRelease = "5.1.3"
val rhinoRelease = "1.9.1"
val luajRelease = "3.0.1"
val jrubyRelease = "10.1.2.0"
val jythonRelease = "2.7.4"
val kawaRelease = "3.1.1"
val abclRelease = "1.9.2"
val beanshellRelease = "2.0b6"
val tuprologRelease = "4.1.1"
val gosuRelease = "1.18.12"
val xtendRelease = "2.44.0"
val flixRelease = "0.77.0"
val haxeRelease = "4.3.7"
val fantomRelease = "1.0.83"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_21
}

// =============================================================================================
//  Tiers.  A tier can see the tiers below it; inside a tier the order is the compilation order.
//
//    0  api      (Java)  src/api/java      contracts with JDK types only, no default methods
//
//    1  pure     Frege   geometry           the rotation itself (+ QuickCheck: ./gradlew checkGeometry)
//                Flix    block name rules, cache retention (Datalog)
//                Clojure config schema
//                Haxe    light nibble rotation (portable)
//                Fantom  safe spot search
//       Tier 1 sees nothing but `api`: Bukkit and PacketEvents are NOT on its classpath, and the
//       five languages do not see each other either.
//
//    2  kernel   (Java)  src/kernel/java   shared player state + service interfaces (Bukkit, PacketEvents)
//
//    3  engine   Scala 3 chunk store + view engine
//                Gosu    world probe
//                Groovy  /rotate command
//                Kawa    cache janitor (Scheme, compiled ahead of time)
//                Xtend   BlockData rotation      -> generated Java, compiled together with ...
//    4  plugin   Java + Kotlin                   plugin class, packet handler, listeners
//
//    5  scripts  Kotlin Script, JRuby, JavaScript (Rhino), Jython, Lua (LuaJ),
//                BeanShell, Common Lisp (ABCL), Prolog (tuProlog)
//                interpreted at run time from src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*,
//                see me.leleawa.rotatedworld.script.ScriptLayers
//
//  Calls downwards are plain JVM calls.  Calls upwards go through `api` / `kernel` interfaces
//  (Services for Flix + Fantom, Host for everything the plugin class provides).
// =============================================================================================
val api = sourceSets.create("api")
val kernel = sourceSets.create("kernel")
val scalaLayer = sourceSets.create("scalaLayer")

sourceSets {
    api.java.setSrcDirs(listOf("src/api/java"))
    api.resources.setSrcDirs(emptyList<String>())
    kernel.java.setSrcDirs(listOf("src/kernel/java"))
    kernel.resources.setSrcDirs(emptyList<String>())

    // Scala 3 is a layer of its own: Gradle's `main` Scala directory is handed over to it.
    (main.get().extensions.getByName("scala") as SourceDirectorySet).setSrcDirs(emptyList<String>())
    (scalaLayer.extensions.getByName("scala") as SourceDirectorySet).setSrcDirs(listOf("src/main/scala"))
    scalaLayer.java.setSrcDirs(emptyList<String>())
    scalaLayer.resources.setSrcDirs(emptyList<String>())
}

fun Configuration.jvmLibrary() = attributes {
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
}

val server = configurations.create("server") { isCanBeConsumed = false; jvmLibrary() }   // Paper API + PacketEvents (provided by the server)
val shade = configurations.create("shade") { isCanBeConsumed = false; jvmLibrary() }    // language runtimes (shaded into the jar)
val clojureCompiler = configurations.create("clojureCompiler") { isCanBeConsumed = false; jvmLibrary() }
val fregeCompiler = configurations.create("fregeCompiler") { isCanBeConsumed = false; jvmLibrary() }
val groovyCompiler = configurations.create("groovyCompiler") { isCanBeConsumed = false; jvmLibrary() }
val gosuCompiler = configurations.create("gosuCompiler") { isCanBeConsumed = false; jvmLibrary() }
val xtendCompiler = configurations.create("xtendCompiler") { isCanBeConsumed = false; jvmLibrary() }
val scriptRuntime = configurations.create("scriptRuntime") { isCanBeConsumed = false; jvmLibrary() }   // interpreters of the script layers
val jythonLib = configurations.create("jythonLib") { isCanBeConsumed = false; jvmLibrary() }   // compiled against; packed via jythonIsolated
val kawaCompiler = configurations.create("kawaCompiler") { isCanBeConsumed = false; jvmLibrary() }

configurations {
    // note: not `apiCompileOnly` - the api tier must stay free of server classes
    listOf("compileOnly", "kernelCompileOnly", "scalaLayerCompileOnly").forEach { named(it) { extendsFrom(server) } }
    listOf("implementation", "scalaLayerImplementation").forEach { named(it) { extendsFrom(shade) } }
    named("implementation") { extendsFrom(scriptRuntime) }
    named("compileOnly") { extendsFrom(jythonLib) }
}

dependencies {
    server(paperApi)
    server(packetEventsApi)
    // annotations the Paper API is compiled against; gosuc resolves annotation types eagerly
    server("org.jetbrains:annotations:26.0.2")
    server("org.jspecify:jspecify:1.0.0")

    shade(kotlin("stdlib"))
    shade("org.scala-lang:scala3-library_3:$scalaRelease")
    shade("org.frege-lang:frege:$fregeRelease")
    shade("org.clojure:clojure:$clojureRelease")
    shade("org.gosu-lang.gosu:gosu-core:$gosuRelease")
    shade("org.apache.groovy:groovy:$groovyRelease")
    shade("org.eclipse.xtext:org.eclipse.xtext.xbase.lib:$xtendRelease")
    shade("com.github.arvyy:kawa:$kawaRelease")

    // script layers (interpreted at run time): JavaScript, Kotlin Script, JRuby, Lua, Jython
    scriptRuntime("org.mozilla:rhino:$rhinoRelease")
    scriptRuntime(kotlin("scripting-common"))
    scriptRuntime(kotlin("scripting-jvm"))
    scriptRuntime(kotlin("scripting-jvm-host"))
    scriptRuntime(kotlin("scripting-compiler-embeddable"))
    scriptRuntime("org.jruby:jruby-complete:$jrubyRelease")
    scriptRuntime("org.luaj:luaj-jse:$luajRelease")
    jythonLib("org.python:jython-standalone:$jythonRelease")
    scriptRuntime("org.apache-extras.beanshell:bsh:$beanshellRelease")
    scriptRuntime("org.abcl:abcl:$abclRelease")
    scriptRuntime("it.unibo.alice.tuprolog:2p-core:$tuprologRelease")

    fregeCompiler("org.frege-lang:frege:$fregeRelease")
    clojureCompiler("org.clojure:clojure:$clojureRelease")
    groovyCompiler("org.apache.groovy:groovy:$groovyRelease")
    gosuCompiler("org.gosu-lang.gosu:gosu-core:$gosuRelease")
    kawaCompiler("com.github.arvyy:kawa:$kawaRelease")
    xtendCompiler("org.eclipse.xtend:org.eclipse.xtend.core:$xtendRelease")
}

// ---------------------------------------------------------------------------------------------
//  Tool downloads (Flix, Haxe, Fantom) -> .tools/
// ---------------------------------------------------------------------------------------------
val toolsDir = layout.projectDirectory.dir(".tools")
val langsDir = layout.buildDirectory.dir("langs")
val workRoot = layout.buildDirectory.dir("work")
val launcher21 = javaToolchains.launcherFor(java.toolchain)
val launcher8 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(8) }

val downloadFlix = tasks.register<DownloadTool>("downloadFlix") {
    url = "https://github.com/flix/flix/releases/download/v$flixRelease/flix.jar"
    target = toolsDir.file("flix-$flixRelease.jar")
}

val haxeArchive = when {
    HostOs.windows -> "haxe-$haxeRelease-win64.zip"
    HostOs.mac -> "haxe-$haxeRelease-osx.tar.gz"
    System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" } -> "haxe-$haxeRelease-linux-arm64.tar.gz"
    else -> "haxe-$haxeRelease-linux64.tar.gz"
}
val downloadHaxe = tasks.register<DownloadTool>("downloadHaxe") {
    url = "https://github.com/HaxeFoundation/haxe/releases/download/$haxeRelease/$haxeArchive"
    target = toolsDir.file(haxeArchive)
}
val extractHaxe = tasks.register<Sync>("extractHaxe") {
    from(downloadHaxe.flatMap { it.target }.map { f -> if (f.asFile.name.endsWith(".zip")) zipTree(f.asFile) else tarTree(resources.gzip(f.asFile)) })
    into(toolsDir.dir("haxe-$haxeRelease"))
}

val downloadFantom = tasks.register<DownloadTool>("downloadFantom") {
    url = "https://github.com/fantom-lang/fantom/releases/download/v$fantomRelease/fantom-$fantomRelease.zip"
    target = toolsDir.file("fantom-$fantomRelease.zip")
}
val extractFantom = tasks.register<Sync>("extractFantom") {
    from(downloadFantom.flatMap { it.target }.map { zipTree(it.asFile) })
    into(toolsDir.dir("fantom-$fantomRelease"))
}

// ---------------------------------------------------------------------------------------------
//  Tier 1: the pure layers.  Each one gets `api` and nothing else.
// ---------------------------------------------------------------------------------------------
val apiJar = tasks.register<Jar>("apiJar") {
    archiveBaseName = "api"
    destinationDirectory = layout.buildDirectory.dir("langs-jars")
    from(api.output)
}

// --- Frege: geometry -------------------------------------------------------------------------
val compileFrege = tasks.register<FregeCompile>("compileFrege") {
    sourceRoot = layout.projectDirectory.dir("src/main/frege")
    sources.from(fileTree("src/main/frege") { include("**/*.fr") })
    compilerClasspath.from(fregeCompiler)
    launcher = launcher21
    outputDir = langsDir.map { it.dir("frege") }
    workDir = workRoot.map { it.dir("frege") }
}

// Runs the QuickCheck properties (prop_*) of the geometry module.
val checkGeometry = tasks.register<JavaExec>("checkGeometry") {
    group = "verification"
    description = "Runs the QuickCheck properties of the Frege geometry module."
    classpath(fregeCompiler, compileFrege.flatMap { it.outputDir })
    mainClass = "frege.tools.Quick"
    jvmArgs("-Xss16m")
    args("me.leleawa.rotatedworld.geometry.Geometry")
    javaLauncher = launcher21
}

// --- Flix: block name rules + retention ------------------------------------------------------
val compileFlix = tasks.register<FlixCompile>("compileFlix") {
    dependsOn(apiJar)
    sourceRoot = layout.projectDirectory.dir("src/main/flix")
    sources.from(fileTree("src/main/flix") { include("**/*.flix") })
    flixJar = downloadFlix.flatMap { it.target }
    flixVersion = flixRelease
    entrypoint = "Plugin.init"
    libraries.from(apiJar)
    launcher = launcher21
    outputDir = langsDir.map { it.dir("flix") }
    workDir = workRoot.map { it.dir("flix") }
}

// --- Clojure: config -------------------------------------------------------------------------
val compileClojure = tasks.register<ClojureCompile>("compileClojure") {
    sourceRoot = layout.projectDirectory.dir("src/main/clojure")
    sources.from(fileTree("src/main/clojure") { include("**/*.clj") })
    compilerClasspath.from(clojureCompiler)
    libraries.from(api.output.classesDirs)
    launcher = launcher21
    outputDir = langsDir.map { it.dir("clojure") }
    workDir = workRoot.map { it.dir("clojure") }
}

// --- Haxe: light -----------------------------------------------------------------------------
val compileHaxe = tasks.register<HaxeCompile>("compileHaxe") {
    dependsOn(extractHaxe)
    haxeHome = layout.dir(extractHaxe.map { it.destinationDir })
    sourceRoot = layout.projectDirectory.dir("src/main/haxe")
    sources.from(fileTree("src/main/haxe") { include("**/*.hx") })
    legacyLauncher = launcher8
    launcher = launcher21
    outputDir = langsDir.map { it.dir("haxe") }
    workDir = workRoot.map { it.dir("haxe") }
}

// --- Fantom: spot search ---------------------------------------------------------------------
val fantomResources = langsDir.map { it.dir("fantom-resources") }
val compileFantom = tasks.register<FantomCompile>("compileFantom") {
    dependsOn(apiJar, extractFantom)
    fantomHome = layout.dir(extractFantom.map { it.destinationDir.resolve("fantom-$fantomRelease") })
    sourceRoot = layout.projectDirectory.dir("src/main/fantom")
    sources.from(fileTree("src/main/fantom") { include("**/*.fan") })
    libraries.from(apiJar)
    launcher = launcher21
    outputDir = langsDir.map { it.dir("fantom") }
    homeZip = fantomResources.map { it.file("fantom/home.zip") }
    workDir = workRoot.map { it.dir("fantom") }
}

// The pure layers are plain "classes on the classpath" for every tier above them - except Flix:
// it emits ~500 classes and packages at the root (`Main`, `Array`, `List` ...) that would shadow
// Scala's and Kotlin's own `Array` / `List`.  Nobody calls Flix directly anyway (only through
// `Services`), so Flix is on the runtime classpath and in the jar, but on no compile classpath.
val pureLayers = listOf(compileFrege, compileFlix, compileClojure, compileHaxe, compileFantom)
val pureClasses: FileCollection = files(
    listOf(compileFrege, compileClojure, compileHaxe, compileFantom).map { t -> t.flatMap { it.outputDir } },
).builtBy(pureLayers)
val flixClasses: FileCollection = files(compileFlix.flatMap { it.outputDir }).builtBy(compileFlix)

// ---------------------------------------------------------------------------------------------
//  Tier 2: kernel
// ---------------------------------------------------------------------------------------------
sourceSets.named("kernel") {
    compileClasspath += api.output
}

// ---------------------------------------------------------------------------------------------
//  Tier 3: engine
// ---------------------------------------------------------------------------------------------
val belowEngine: FileCollection = files(api.output, pureClasses, kernel.output)

// --- Scala 3 ---------------------------------------------------------------------------------
sourceSets.named("scalaLayer") {
    compileClasspath += belowEngine
    runtimeClasspath += belowEngine
}

// --- Gosu ------------------------------------------------------------------------------------
val compileGosu = tasks.register<GosuCompile>("compileGosu") {
    sourceRoot = layout.projectDirectory.dir("src/main/gosu")
    sources.from(fileTree("src/main/gosu") { include("**/*.gs", "**/*.gsx", "**/*.gst") })
    compilerClasspath.from(gosuCompiler)
    libraries.from(belowEngine, scalaLayer.output, server, shade)
    launcher = launcher21
    outputDir = langsDir.map { it.dir("gosu") }
    workDir = workRoot.map { it.dir("gosu") }
}

// --- Groovy ----------------------------------------------------------------------------------
val compileGroovy = tasks.register<GroovyLangCompile>("compileGroovy") {
    sources.from(fileTree("src/main/groovy") { include("**/*.groovy") })
    compilerClasspath.from(groovyCompiler)
    libraries.from(belowEngine, scalaLayer.output, compileGosu.flatMap { it.outputDir }, server, shade)
    launcher = launcher21
    outputDir = langsDir.map { it.dir("groovy") }
    workDir = workRoot.map { it.dir("groovy") }
}

// --- Kawa Scheme -----------------------------------------------------------------------------
val compileKawa = tasks.register<KawaCompile>("compileKawa") {
    sourceRoot = layout.projectDirectory.dir("src/main/kawa")
    sources.from(fileTree("src/main/kawa") { include("**/*.scm") })
    compilerClasspath.from(kawaCompiler)
    libraries.from(belowEngine, scalaLayer.output, compileGosu.flatMap { it.outputDir }, compileGroovy.flatMap { it.outputDir }, server, shade)
    launcher = launcher21
    outputDir = langsDir.map { it.dir("kawa") }
    workDir = workRoot.map { it.dir("kawa") }
}

// --- Xtend -----------------------------------------------------------------------------------
val compileXtend = tasks.register<XtendCompile>("compileXtend") {
    sourceRoot = layout.projectDirectory.dir("src/main/xtend")
    javaSourceRoots.from(layout.projectDirectory.dir("src/main/java"))
    sources.from(fileTree("src/main/xtend") { include("**/*.xtend") })
    compilerClasspath.from(xtendCompiler)
    libraries.from(belowEngine, scalaLayer.output, compileGosu.flatMap { it.outputDir }, compileGroovy.flatMap { it.outputDir }, compileKawa.flatMap { it.outputDir }, server, shade)
    launcher = launcher21
    outputDir = langsDir.map { it.dir("xtend-gen") }
    workDir = workRoot.map { it.dir("xtend") }
}

// ---------------------------------------------------------------------------------------------
//  Tier 4: main = Java + Kotlin (+ the Java generated by Xtend)
// ---------------------------------------------------------------------------------------------
val lowerLayers: FileCollection = files(belowEngine, scalaLayer.output, compileGosu.flatMap { it.outputDir }, compileGroovy.flatMap { it.outputDir }, compileKawa.flatMap { it.outputDir })
    .builtBy(api.classesTaskName, kernel.classesTaskName, pureLayers, "scalaLayerClasses", compileGosu, compileGroovy, compileKawa)

sourceSets.main {
    java.srcDir(compileXtend.flatMap { it.outputDir })
    resources.srcDir(fantomResources)
    compileClasspath += lowerLayers
    runtimeClasspath += lowerLayers + flixClasses
}
tasks.processResources { dependsOn(compileFantom) }

// the script layers are packed as resources; a file in <plugin data folder>/scripts/<dir>/ overrides them at run time
listOf("javascript", "kts", "jruby", "lua", "jython", "beanshell", "commonlisp", "prolog").forEach { dir ->
    tasks.processResources { from("src/main/$dir") { into("scripts/$dir") } }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.jar { enabled = false }

// Jython bundles its own jnr/jffi/jline/jansi, which clash with the (different) copies inside JRuby.
// Packing Jython separately with those packages relocated lets both interpreters keep the version they were built for.
val jythonIsolated = tasks.register<ShadowJar>("jythonIsolated") {
    configurations = listOf(jythonLib)
    archiveBaseName = "jython-isolated"
    archiveClassifier = ""
    archiveVersion = jythonRelease
    destinationDirectory = layout.buildDirectory.dir("langs-jars")
    listOf("jnr", "com.kenai", "org.fusesource", "jline", "org.jline", "com.google", "org.objectweb", "org.antlr", "org.apache", "org.yaml", "com.ibm", "com.sun.jna", "org.bouncycastle")
        .forEach { relocate(it, "me.leleawa.rotatedworld.shaded.jython.$it") }
}

tasks.shadowJar {
    dependsOn(jythonIsolated)
    from(jythonIsolated.map { zipTree(it.archiveFile) })
    isZip64 = true   // the script interpreters push the jar past 65535 entries
    // Paper rejects jars with duplicate entries.  Keep the first copy of every file, except the ones that
    // Shadow's transformers merge (service files, Kotlin module metadata).
    eachFile {
        if (!path.startsWith("META-INF/services/") && !path.endsWith(".kotlin_module")) {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
    archiveBaseName = rootProject.name
    archiveClassifier = ""
    mergeServiceFiles()
    from(lowerLayers, flixClasses)
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "**/*.java", "module-info.class", "Manifest.mf", "gw/Dummy.class", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES", "META-INF/maven/**")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
tasks.assemble { dependsOn(tasks.shadowJar) }

// Boots the pure layers from the finished jar (no server needed), compares them with the original
// Kotlin behaviour and link-checks the server-facing layers.
// The jar is copied into a directory with a non-ASCII name first: real servers live in paths like
// C:/Users/<name>/Desktop/原桌面/..., and some interpreters (Jython) choke on such paths.
val smokeJar = tasks.register<Copy>("smokeJar") {
    from(tasks.shadowJar)
    into(layout.buildDirectory.dir("smoke/插件 plugins"))
}
val smokeTest = tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    description = "Runs me.leleawa.rotatedworld.Smoke against the plugin jar (from a non-ASCII path)."
    dependsOn(smokeJar)
    classpath(smokeJar.map { it.destinationDir.resolve(tasks.shadowJar.get().archiveFileName.get()) }, server)
    mainClass = "me.leleawa.rotatedworld.Smoke"
    // ./gradlew smokeTest -PsmokeJava=25  - run it on the JDK the server uses
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(providers.gradleProperty("smokeJava").getOrElse("21").toInt())
    }
}
tasks.check { dependsOn(smokeTest, checkGeometry) }

tasks.runServer {
    minecraftVersion(minecraftRelease)
    javaLauncher = launcher21
}

// Makes the template yours:  ./gradlew initTemplate --package=com.acme.myplugin --plugin-name=MyPlugin
tasks.register<InitTemplate>("initTemplate") {
    group = "template"
    description = "Renames the root package and the plugin name of this template."
}
