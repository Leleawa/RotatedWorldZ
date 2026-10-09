# RotatedWorldZ

Eine 20-Sprachen-Version von RotatedWorld (ein Paper/Folia-Plugin, das mittels PacketEvents die gesamte Welt für den Spieler um 90° um die X-Achse dreht):
Kompiliert mit **Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe**,
zuzüglich 8 zur Laufzeit geladener Skriptschichten: **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog** – alles gebündelt in **einer einzigen JAR-Datei**.


## Wer macht was

| Schicht | Sprache | Zuständigkeit | Warum diese Sprache |
|---|---|---|---|
| 0 api | Java | Verträge (Contracts), die von der reinen Berechnungsschicht implementiert werden müssen (nur JDK-Typen) | Der kleinste gemeinsame Nenner, den alle Compiler verstehen |
| 1 Reine Berechnung | **Frege** | Koordinatenalgebra: die einzige Definition der Rotation nebst 10 QuickCheck-Eigenschaften | Reine Funktionen + integriertes QuickCheck; kompiliert zu `static int/float/double`-Methoden ohne Boxing |
| | **Flix** | Block-Namensregeln (stehend ↔ wandmontiert, Block-Entity-Typen); Cache-Eviktion | Regeltabellen + Effektsystem garantieren Reinheit; „Welche Chunks werden noch gebraucht“ ist eine **Datalog**-Regel |
| | **Clojure** | Konfigurations-Parsing: Konfigurationselemente als Schemadaten definiert | Datengetrieben: Neue Konfigurationsoption = eine Zeile Daten hinzufügen |
| | **Haxe** | Rotation von Licht-Nibble-Arrays | Verwendet nur `haxe.io.Bytes`, derselbe Code kompiliert nach JS/C++ (z. B. für Offline-Viewer) |
| | **Fantom** | Suchstrategie für sichere Standorte | Reiner Algorithmus, betrachtet die Welt über das `BlockProbe`-Interface, berührt Bukkit nicht |
| 2 kernel | Java | Spielerstatus `PlayerState`, `SerialExecutor`, `Host`- / `BlockRotator`-Schnittstellen | Nebenläufiger Shared State mit volatile / atomaren Variablen, in Java am direktesten |
| 3 Engine | **Scala 3** | Chunk-Cache `ChunkStore` + Sichtweiten-Engine `ViewManager` (Floating Origin, Prefetching, Senden von Chunks) | Performance-Kern, massive Array-Schleifen |
| | **Gosu** | `WorldProbe`: Bukkit-Block- / Hitbox-Prüfungen; Enhancements für `Material` | Enhancements fügen Bukkit-Typen Eigenschaften hinzu (`type.Hazard`) |
| | **Groovy** | Ablauf-Orchestrierung des `/rotate`-Befehls | Cold Path |
| | **Kawa Scheme** | Cache-Bereinigung: Sammelt den Sichtbereich jeder Welt und übergibt ihn an Flix' Datalog zur Beibehaltungsentscheidung (zuvor in Scalas `ViewManager`) | Ahead-of-Time-Kompilierung, Listenverarbeitung |
| | **Xtend** | `BlockRotation`: Rotationsregeln für BlockData | Automatisches Type-Casting bei `instanceof` + Property-Syntax; Regeln lesen sich wie Spezifikationen selbst |
| 4 Plugin | **Kotlin** | Paket-Umschreibung (Netty Hot Path), Bukkit-Events, PacketEvents-Vektorfassade | Ursprünglicher Code; PacketEvents lässt sich in Kotlin am elegantesten schreiben |
| | Java | Plugin-Einstiegspunkt (implementiert `Host`), Brücke zum Starten von Flix/Fantom/Clojure, Skript-Schicht-Loader | Einstiegspunkt und Assemblierung |
| 5 Skripte | **Kotlin Script** | Entscheidungen zur Neupositionierung des Floating Origin (zuvor in Scalas `ViewManager`) | Zur Laufzeit in Bytecode kompiliert, auch pro Tick aufgerufen nicht langsam |
| | **JRuby** | Sendereihenfolge der Chunk-Säulen (zuvor in Scalas `ViewManager`) | Eine einzige `product.each_with_index.sort_by`-Kette |
| | **JavaScript** | Freigabe bei Lag durch „zu schnelle Bewegung“ (zuvor im Kotlin-Listener) | Von Rhino interpretiert, seltene Events |
| | **Jython** | Rückstoß (zuvor im Kotlin-Listener) | Wird nur bei jedem Treffer aufgerufen |
| | **Lua** | Fallschaden (zuvor im Kotlin-Listener) | Chef-Geschworener |
| | **BeanShell** | Die Ausgabezeile für `/rotate status` (zuvor in Groovy) | Skript mit Java-Syntax |
| | **Common Lisp** | Welche Serverprüfungen für rotierte Spieler durchgewinkt werden (fehlgeschlagene Bewegung, Flug-Kick) (zuvor im Kotlin-Listener) | Eine `cond`-Entscheidungstabelle |
| | **Prolog** | Argumentsyntax für `/rotate`, formuliert als DCG (zuvor in Groovy) | Syntax gehört als DCG modelliert |
| | **Alle acht + Kawa** | **Fallschaden-Geschworenengericht**: Alle neun Sprachen berechnen ihn unabhängig; Mehrheitsentscheid, bei Stimmengleichstand entscheidet Lua, bei Uneinigkeit wird eine Warnung protokolliert | Unterhaltung |

**Schicht 1 kann Bukkit und PacketEvents baubedingt nicht sehen** (sie erhält nur `api`), und die fünf Sprachen können einander ebenfalls nicht sehen. Aufrufe nach unten sind gewöhnliche JVM-Aufrufe; Aufrufe nach oben erfolgen ausschließlich über Schnittstellen: Flix und Fantom übergeben ihre Implementierungen an `api.Services`, und die Hauptklasse des Plugins stellt über `kernel.Host` Dienste für Schicht 3 bereit.

## Skriptschichten

Skripte werden nicht vorkompiliert, sondern beim Start des Plugins von eingebetteten Interpretern geladen (`script.ScriptLayers`, 8 Interpreter starten parallel in ca. 4–5 Sekunden):

* Der Quellcode liegt unter `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*` und wird beim Build in `scripts/` innerhalb der JAR gepackt.
* Existiert `plugins/RotatedWorldZ/scripts/<Verzeichnis>/rules.*`, überschreibt dies die Datei in der JAR: **Regeländerungen erfordern keinen Rebuild, lediglich einen Serverneustart**.
* Die von Skripten implementierten Schnittstellen befinden sich in `api.ScriptedRules`. Der letzte Ausdruck in Kotlin Script muss ein Objekt sein, das gleichzeitig `Recenter` und `FallJuror` implementiert (und darf kein anonymes Objekt sein); alle anderen Skripte definieren lediglich globale Funktionen (bei Prolog Prädikate, deren letztes Argument das Ergebnis ist), die von Java in Schnittstellen gekapselt werden.
* Eingebettete Interpreter garantieren keine Thread-Sicherheit; Aufrufe an jedes Skript erfolgen sequentiell (`synchronized`). Daher befindet sich nur Kotlin Script (kompiliert ausgeführt) auf dem Hot Path jedes Ticks, alle anderen liegen auf dem Cold Path.
* Jythons mitgelieferte Bibliotheken wie jnr/jffi kollidieren versionsmäßig mit JRuby, weshalb Jython beim Build separat paketiert und diese Pakete verschoben/relokalisiert werden (`jythonIsolated`-Task, nach dem Vorbild der Upstream-Vorlage).

Einige Aspekte, bei denen sprachübergreifende Konsistenz gewährleistet wird:

* Die Abbildung der Blockseiten (UP→NORTH…) wird nicht mehr manuell geschrieben; Xtend (Bukkit `BlockFace`) und Kotlin (PacketEvents `BlockFace`) leiten sie beide von derselben Frege-Rotation ab.
* Die Koordinatenabbildung `Geometry.localIndex` (Frege) innerhalb einer Section und die Haxe-Lichtrotation verwenden dieselbe Abbildung; `smokeTest` vergleicht sie Zelle für Zelle.

## Build

```bash
./gradlew build          # Artefakt: build/libs/RotatedWorldZ-1.0.0.jar; enthält checkGeometry und smokeTest
./gradlew checkGeometry  # Führt nur die QuickCheck-Eigenschaften von Frege aus
./gradlew smokeTest      # Kein Server erforderlich: Startet die reine Berechnungsschicht aus der fertigen JAR, vergleicht sie mit dem ursprünglichen Kotlin-Verhalten und prüft die Verlinkung der übrigen Schichten
```

Zur Laufzeit muss PacketEvents auf dem Server installiert sein (`depend: [packetevents]`).

Der erste Build lädt Flix, Haxe und Fantom nach `.tools/` herunter (ca. 60 MB). Haxe benötigt zudem ein JDK 8 (ausschließlich für dessen `rt.jar`; falls die Gradle-Toolchain keines findet, wird es automatisch über Foojay heruntergeladen). Für alles andere wird JDK 21 verwendet.

## Fallstricke der einzelnen Sprachen

* **Flix**: Kann nur eine einzige Einstiegsfunktion exportieren, die in der Klasse `Main` des Default-Packages generiert wird (`FlixBridge` ruft sie per Reflection auf). Zudem werden im Root-Verzeichnis ca. 500 Klassen und Packages (`Array`, `List`…) erzeugt, die Scalas/Kotlins eigene `Array`/`List` überschatten würden; daher befindet sich die Flix-Ausgabe **auf keinem Compile-Classpath**, sondern gelangt nur in die Runtime und das JAR. Datalog-Guards erfassen maximal 5 Variablen. Java-Interfaces und Flix-Module dürfen nicht denselben Namen tragen.
* **Frege**: Parameter sind standardmäßig lazy (in der Java-Signatur `Lazy<Float>`); für strikte primitive Typen muss dem Pattern ein `!` vorangestellt werden.
* **Fantom**: Die Implementierung von Java-Interfaces mit Default-Methoden führt zu Abstürzen; daher haben Schnittstellen in `api` keine Default-Methoden. Java `long`/`double`/`boolean` entsprechen direkt Fantoms `Int`/`Float`/`Bool`, Referenztypen sind nullable. Fließkommaliterale müssen als `0.5f` geschrieben werden (`0.5` ist Decimal).
* **Gosu**: `block` ist ein Schlüsselwort (Gosus Lambda). gosuc löst Annotationstypen sofort auf, daher müssen die Paper-API-Abhängigkeiten `org.jetbrains:annotations` und `jspecify` im Compile-Classpath vorhanden sein.
* **Xtend**: Bukkits `Keyed` besitzt sowohl `key()` als auch `getKey()`; `b.key` ist mehrdeutig, daher muss `b.getKey` geschrieben werden. Wenn Feld und Methode denselben Namen haben, verweist der Name im Methodenkörper auf die Methode.
* **Haxe**: `--jvm`-Target; der externe Java-Classloader akzeptiert nur die `rt.jar` von JDK 8; `haxelib` im offiziellen Windows-Paket hängt von Neko ab, weshalb beim Build ein Stub in PATH platziert wird.
* **Clojure**: Verlässt sich zum Auffinden von Namespaces auf den Thread Context ClassLoader. `ClojureBridge` verweist während der Initialisierung und Aufrufe temporär auf den ClassLoader des Plugins.
* **Kotlin Script**: Das Skriptergebnis darf kein anonymes Objekt sein (`object : X {}` meldet "anonymous type"); es muss vorab eine benannte Klasse deklariert werden.
* **Kawa**: Javas `null` ist in Scheme ein Wahrheitswert (nur `#f` ist falsch), Vergleiche müssen `(eq? x #!null)` lauten; `obj:field` liest Felder, `(obj:method)` ruft Methoden auf.
* **Thread Context ClassLoader**: Der Context ClassLoader von Server-Threads (Haupt-Thread, Region-Threads, Netty, globaler Scheduler) sieht die Klassen des Plugins nicht, während Kawas und Clojures Runtimes Klassen genau darüber nach Namen suchen. Daher wird jeder Eintritt von Server-Threads in diese Runtimes mit `kernel.PluginContext` gekapselt (Kawa-Cache-Bereinigung, Geschworenengericht, alle Skriptaufrufe, Gosu/Fantom-Standortsuche, Groovy-Befehl). Der Haupt-Thread von `smokeTest` setzt daher den Context ClassLoader auf den Platform-ClassLoader, um Server-Threads zu simulieren.
* **Prolog**: tuProlog lädt DCG standardmäßig nicht; erst nach `loadLibrary(new DCGLibrary())` stehen `-->` und `phrase/2` zur Verfügung.
* **Jython**: `PyString` ist ein Byte-String aus Python 2; enthalten Pfade chinesische Zeichen, muss `Py.newStringOrUnicode` verwendet werden. `smokeTest` läuft daher von einem Nicht-ASCII-Pfad wie `build/smoke/插件 plugins/`.
* **Common Lisp (ABCL)**: Möchte bei der Initialisierung per Reflection die Virtual-Thread-Factory des JDK öffnen; ohne `--add-opens java.base/java.lang` wird eine Zeile auf `System.err` ausgegeben (Paper warnt dann mit "Plugin hat System.err verwendet"). Harmlos; `StderrFilter` fängt während der ABCL-Initialisierung lediglich diese eine Zeile ab.
* **Geschworenengericht**: `ceil(2.5 - 3)` ergibt `-0.0`. Das `max` von Python / Kotlin / Scheme behält dies unverändert bei; vor der Stimmenauszählung muss es zu `0.0` normalisiert werden, da sonst bei Stürzen aus 2–3 Blöcken fälschlicherweise "Uneinigkeit" gemeldet wird.
* **Gosu**: Sobald die Runtime initialisiert ist, wird `java.base` für Reflection geöffnet, wodurch nachfolgende Reflection-Probleme anderer Bibliotheken nicht mehr auffallen. Daher lädt `smokeTest` gemäß der `onEnable`-Reihenfolge des Plugins zuerst die Skriptschichten, bevor Gosu initialisiert wird. `./gradlew smokeTest -PsmokeJava=25` ermöglicht Tests mit dem vom Server verwendeten JDK.
* **JRuby**: `sort_by` ist nicht stabil; um die ursprüngliche Reihenfolge beizubehalten, muss der Index in den Sortierschlüssel aufgenommen werden.
* **Scala 3**: Nach dem Löschen von Quelldateien kann die inkrementelle Kompilierung alte `.tasty`-Dateien hinterlassen, die in den Build-Cache gelangen. Falls die JAR unerwünschte Klassen enthält, `./gradlew clean build --rerun-tasks` ausführen.

## Verifiziert / Nicht verifiziert

Verifiziert (Windows 11, JDK 21, Gradle 9.7.1):

* Alle 12 kompilierten Sprachen kompilieren fehlerfrei, alle 8 Skriptschichten laden erfolgreich, `./gradlew clean build --rerun-tasks` besteht.
* `checkGeometry`: 10 QuickCheck-Eigenschaften bestanden, einschließlich Roundtrip, Gitter-/Punkt-Konsistenz, Konsistenz zwischen Blickrichtungs- und Vektorrotation, `zBase` als abrundende Division.
* `smokeTest` (insgesamt 123.134 Prüfungen, ausgeführt in einem isolierten ClassLoader über einen Pfad mit chinesischen Zeichen zur Simulation des Plugin-Ladevorgangs; sowohl unter JDK 21 als auch JDK 25 getestet):
  * Flix-Regeln, Clojure-Konfiguration, Haxe-Beleuchtung und Fantom-Suche stimmen punktgenau mit der ursprünglichen Kotlin-Implementierung überein;
  * Ergebnisse der 8 Skripte und von Kawa stimmen punktgenau mit dem übernommenen Originalcode überein; alle 9 Stimmen der Jury einstimmig, keinerlei Warnungen protokolliert; beim Laden der Skriptschichten erfolgt keine Ausgabe auf `System.err`;
  * Schichten von Scala/Gosu/Groovy/Xtend/Kotlin verlinken sauber, die Laufzeitumgebungen von Gosu/Groovy/Scala initialisieren fehlerfrei.

**Nicht verifiziert**:

* Wurde nicht auf einem echten Paper-/Folia-Server betrieben und kein Client-Beitritt getestet (dies erfordert das Akzeptieren der Minecraft-EULA sowie die Installation von PacketEvents). Serverabhängige Pfade wie Paket-Rewriting, Chunk-Versand und Neupositionierung werden lediglich durch Kompilierungsprüfungen und eine „Zeile-für-Zeile-Portierung des Originals“ garantiert. Vor dem Produktiveinsatz bitte auf einem Testserver verifizieren.
* JAR ca. 231 MB groß (Original: 4,9 MB): enthält Kotlin-Compiler, JRuby, Jython, ABCL sowie die Runtimes von Gosu, Groovy, Scala, Clojure, Frege und Kawa. Der Plugin-Start dauert durch das Laden der Skriptschichten 4–5 Sekunden länger. Diese Runtimes sind unverändert ohne Relokation eingebunden: Falls andere Plugins auf demselben Server abweichende Versionen von Kotlin/Scala/Groovy mitbringen, kann es zu Konflikten kommen.

## Verzeichnisstruktur

```
src/api/java        Schicht 0: Verträge der reinen Berechnungsschicht (JDK-Typen)
src/kernel/java     Schicht 2: Shared State und Service-Schnittstellen (Bukkit + PacketEvents)
src/main/<Sprache>   Quellcode der jeweiligen Sprachen (Skriptschichten unter src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog})
src/main/resources  plugin.yml, config.yml
buildSrc            Gradle-Build-Tasks für die einzelnen Sprachen
build.gradle.kts    Schichtenarchitektur und Abhängigkeiten
```
