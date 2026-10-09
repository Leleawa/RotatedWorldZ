# RotatedWorldZ

De 20-talige versie van RotatedWorld (een Paper/Folia-plugin die de hele wereld via PacketEvents 90° om de X-as draait voor spelers):
**Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe** meegecompileerd,
plus 8 scriptlagen geladen tijdens runtime: **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog**, allemaal gebundeld in **één enkele jar**.


## Wie is waarvoor verantwoordelijk

| Laag | Taal | Verantwoordelijkheid | Waarom deze taal? |
|---|---|---|---|
| 0 api | Java | Contracten die geïmplementeerd moeten worden door de pure rekenlaag (alleen JDK-typen) | De kleinste gemene deler die alle compilers begrijpen |
| 1 puur rekenen | **Frege** | Coördinatenalgebra: de canonieke definitie van rotatie, plus 10 QuickCheck-eigenschappen | Pure functies + ingebouwde QuickCheck; compileert naar `static int/float/double`-methoden, zonder boxing |
| | **Flix** | Bloknaamgevingsregels (staand ↔ muur, block entity-typen); cache-reclamatie | Regeltabellen + effectsysteem garanderen puurheid; "welke chunks nog nodig zijn" is een **Datalog**-regel |
| | **Clojure** | Configuratieparsing: configuratieopties geschreven als schema-data | Data-gedreven: configuratieoptie toevoegen = één regel data toevoegen |
| | **Haxe** | Rotatie van licht-nibble-arrays | Gebruikt alleen `haxe.io.Bytes`, dezelfde code compileert naar JS/C++ (bijv. offline previewer) |
| | **Fantom** | Zoekstrategie voor veilige landingsposities | Puur algoritme, bekijkt de wereld via de `BlockProbe`-interface, raakt Bukkit niet aan |
| 2 kernel | Java | Spelerstatus `PlayerState`, `SerialExecutor`, interfaces `Host` / `BlockRotator` | Concurrente gedeelde status met `volatile` / atomaire variabelen, het meest direct in Java |
| 3 engine | **Scala 3** | Chunk-cache `ChunkStore` + zichtengine `ViewManager` (zwevende oorsprong, prefetching, chunks versturen) | Prestatiekern, zware array-lussen |
| | **Gosu** | `WorldProbe`: Bukkit-blok- / bounding box-controles; enhancement voor `Material` | Enhancements voegen eigenschappen toe aan Bukkit-typen (`type.Hazard`) |
| | **Groovy** | Workflow-orkestratie voor het `/rotate`-commando | Koude pad (cold path) |
| | **Kawa Scheme** | Cache-opschoning: verzamelt het zichtbereik van elke wereld en laat Flix's Datalog bepalen wat behouden blijft (oorspronkelijk in Scala `ViewManager`) | Ahead-of-time (AOT) gecompileerd, lijstverwerking |
| | **Xtend** | `BlockRotation`: rotatieregels voor BlockData | Automatisch casten via `instanceof` + eigenschapssyntaxis; regels lezen als de specificatie zelf |
| 4 plugin | **Kotlin** | Packet-herschrijving (Netty hot path), Bukkit-events, PacketEvents vector-façade | Oorspronkelijke code; PacketEvents-idiomen zijn het meest natuurlijk in Kotlin |
| | Java | Plugin-entrypoint (implementeert `Host`), opstartbridges voor Flix/Fantom/Clojure, loader voor scriptlagen | Entrypoint en assemblage |
| 5 scripts | **Kotlin Script** | Recenter-beslissing voor zwevende oorsprong (oorspronkelijk in Scala `ViewManager`) | Runtime-gecompileerd naar bytecode, snel genoeg voor aanroep per tick |
| | **JRuby** | Volgorde van verzenden van chunk-kolommen (oorspronkelijk in Scala `ViewManager`) | Eén enkele `product.each_with_index.sort_by`-keten |
| | **JavaScript** | Rubberband-bypass voor "te snel bewogen" (oorspronkelijk in Kotlin-listener) | Geïnterpreteerd via Rhino, zeldzame gebeurtenissen |
| | **Jython** | Knockback (terugslag) (oorspronkelijk in Kotlin-listener) | Eén keer uitgevoerd per ontvangen treffer |
| | **Lua** | Valschade (oorspronkelijk in Kotlin-listener) | Hoofd-jurylid |
| | **BeanShell** | De tekstregel van `/rotate status` (oorspronkelijk in Groovy) | Scripting met Java-syntaxis |
| | **Common Lisp** | Welke servercontroles worden omzeild voor geroteerde spelers (mislukte bewegingen, vlieg-kicks) (oorspronkelijk in Kotlin-listener) | Een `cond`-beslissingstabel |
| | **Prolog** | Argumentgrammatica voor `/rotate`, geschreven als DCG (oorspronkelijk in Groovy) | Grammatica's horen thuis in DCG |
| | **Alle acht + Kawa** | **Valschade-jury**: negen talen berekenen elk één keer, meerderheid beslist, bij staking van stemmen beslist Lua, waarschuwing gelogd bij onenigheid | Vermaak |

**Laag 1 heeft tijdens de build geen zicht op Bukkit of PacketEvents** (ze ontvangen alleen `api`), en de vijf talen zien elkaar onderling evenmin. Aanroepen naar beneden zijn normale JVM-aanroepen; aanroepen naar boven verlopen uitsluitend via interfaces: Flix en Fantom dragen implementaties over aan `api.Services`, en de hoofdklasse van de plugin levert services aan Laag 3 via `kernel.Host`.

## Scriptlagen

Scripts worden niet meegecompileerd, maar tijdens het opstarten van de plugin geladen door ingebouwde interpreters (`script.ScriptLayers`, 8 interpreters parallel opgestart, ca. 4–5 seconden):

* Broncode bevindt zich in `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*`, tijdens de build verpakt onder `scripts/` in de jar.
* Als `plugins/RotatedWorldZ/scripts/<map>/rules.*` bestaat, overschrijft dit de versie in de jar: **regels aanpassen vereist geen herbouw, een serverherstart volstaat**.
* De interfaces die door scripts worden geïmplementeerd staan in `api.ScriptedRules`. De laatste expressie van Kotlin Script moet een object zijn dat tegelijkertijd `Recenter` en `FallJuror` implementeert, en mag geen anoniem object zijn; de overige scripts definiëren uitsluitend globale functies (Prolog gebruikt predicaten, waarbij het laatste argument het resultaat is), die door Java als interfaces worden ingekapseld.
* Geen van de ingebouwde interpreters garandeert thread-safety; aanroepen naar elk script worden geserialiseerd (`synchronized`). Daarom bevindt alleen Kotlin Script (gecompileerd uitgevoerd) zich op het hete pad per tick; de rest bevindt zich op koude paden.
* De meegeleverde bibliotheken van Jython (jnr/jffi, etc.) conflicteren qua versie met JRuby. Daarom verpakt de build Jython eerst afzonderlijk en relocateert deze packages (taak `jythonIsolated`, volgens de upstream-sjabloonwerkwijze).

Punten waar cross-taal-consistentie gegarandeerd is:

* De mapping van blokvlakken (UP→NORTH……) wordt niet meer handmatig geschreven; Xtend (Bukkits `BlockFace`) en Kotlin (PacketEvents' `BlockFace`) worden beide afgeleid van dezelfde rotatie in Frege.
* De coördinatenmapping binnen de section `Geometry.localIndex` (Frege) en de Haxe-lichtrotatie gebruiken exact dezelfde mapping, blok voor blok vergeleken in `smokeTest`.

## Bouwen

```bash
./gradlew build          # Uitvoer: build/libs/RotatedWorldZ-1.0.0.jar; bevat checkGeometry en smokeTest
./gradlew checkGeometry  # Voert alleen Frege's QuickCheck-eigenschappen uit
./gradlew smokeTest      # Geen server nodig: start de pure rekenlaag vanuit de voltooide jar en vergelijkt met origineel Kotlin-gedrag; link-controle voor alle overige lagen
```

Tijdens runtime moet PacketEvents op de server geïnstalleerd zijn (`depend: [packetevents]`).

De eerste build downloadt Flix, Haxe en Fantom naar `.tools/` (ongeveer 60 MB); Haxe vereist tevens een JDK 8 (gebruikt uitsluitend diens `rt.jar`; als de Gradle-toolchain deze niet vindt, wordt deze automatisch gedownload via foojay). Al het overige gebruikt JDK 21.

## Eigenheden en valkuilen per taal

* **Flix**: Kan slechts één entrypoint-functie exporteren, gegenereerd in de klasse `Main` van het standaardpakket (`FlixBridge` roept deze aan via reflectie). Het genereert tevens ongeveer 500 klassen en packages in de root (`Array`, `List`……), wat de `Array`/`List` van Scala/Kotlin zou overschaduwen; daarom bevindt de output van Flix zich **op geen enkele compilatie-classpath**, maar komt alleen terecht in runtime en de jar. Datalog-guards leggen maximaal 5 variabelen vast. Java-interfaces en Flix-modules mogen niet dezelfde naam hebben.
* **Frege**: Parameters zijn standaard lui (lazy) (in de Java-signatuur verschijnen ze als `Lazy<Float>`); voeg `!` toe aan het patroon voor strikte primitieve parameters.
* **Fantom**: Het implementeren van Java-interfaces met `default`-methoden crasht; daarom hebben interfaces in `api` geen `default`-methoden. Java's `long`/`double`/`boolean` corresponderen direct met Fantoms `Int`/`Float`/`Bool`; referentietypen zijn allemaal nullable. Drijvende-kommagetallen moeten als `0.5f` worden geschreven (`0.5` is Decimal).
* **Gosu**: `block` is een gereserveerd trefwoord (Gosu's lambda). gosuc lost annotatietypen onmiddellijk op, dus het compilatie-classpath vereist de Paper API-afhankelijkheden `org.jetbrains:annotations` en `jspecify`.
* **Xtend**: Bukkits `Keyed` heeft zowel `key()` als `getKey()`; `b.key` is dubbelzinnig en moet als `b.getKey` geschreven worden. Wanneer een veld en een methode dezelfde naam hebben, verwijst de naam binnen het methodelichaam naar de methode.
* **Haxe**: `--jvm`-target; Java's externe class loader herkent alleen de `rt.jar` van JDK 8; het officiële Windows-pakket van `haxelib` vereist Neko, dus tijdens de build wordt een stub in PATH geplaatst.
* **Clojure**: Vertrouwt op de thread context class loader om namespaces te vinden; `ClojureBridge` verwijst tijdens initialisatie en aanroepen tijdelijk naar de class loader van de plugin.
* **Kotlin Script**: Het scriptresultaat mag geen anoniem object zijn (`object : X {}` geeft de foutmelding "anonymous type"); er moet eerst een benoemde klasse worden gedeclareerd.
* **Kawa**: Java's `null` is in Scheme een waarheidswaarde (truthy) (alleen `#f` is onwaar); controles moeten geschreven worden als `(eq? x #!null)`; `obj:field` leest velden, `(obj:method)` roept methoden aan.
* **Thread context class loader**: Server-threads (hoofdthread, regiothreads, netty, globale scheduler) hebben een context class loader die de klassen van de plugin niet kan zien, terwijl de runtimes van Kawa en Clojure juist deze loader gebruiken om klassen op naam te zoeken. Daarom wordt elke overgang van server-threads naar deze runtimes ingekapseld met `kernel.PluginContext` (Kawa cache-opschoning, jury, alle scriptaanroepen, Gosu/Fantom landingsposities zoeken, Groovy-commando's). De hoofdthread van `smokeTest` stelt om deze reden de context class loader in op de platform loader om server-threads te simuleren.
* **Prolog**: tuProlog laadt standaard geen DCG; `loadLibrary(new DCGLibrary())` moet worden aangeroepen om `-->` en `phrase/2` beschikbaar te maken.
* **Jython**: `PyString` is een byte-string in Python 2; bij niet-ASCII-tekens in het pad moet `Py.newStringOrUnicode` gebruikt worden. `smokeTest` wordt om die reden uitgevoerd vanuit een niet-ASCII-pad zoals `build/smoke/插件 plugins/`.
* **Common Lisp (ABCL)**: Probeert bij initialisatie via reflectie de virtual thread factory van de JDK te openen; zonder `--add-opens java.base/java.lang` schrijft het een regel naar `System.err` (Paper waarschuwt daarom dat de "plugin System.err heeft gebruikt"). Onschadelijk; `StderrFilter` onderschept uitsluitend deze regel tijdens de ABCL-initialisatie.
* **Jury**: `ceil(2.5 - 3)` resulteert in `-0.0`; `max` in Python / Kotlin / Scheme behoudt dit intact. Het moet genormaliseerd worden naar `0.0` vóór het tellen van de stemmen, anders zouden valafstanden van 2–3 blokken valselijk "onenigheid" melden.
* **Gosu**: Zodra de runtime initialiseert, stelt deze `java.base` open voor reflectie, waardoor eventuele reflectieproblemen van andere bibliotheken daarna onzichtbaar worden. Daarom volgt `smokeTest` de volgorde van `onEnable` van de plugin: eerst de scriptlaag laden, daarna pas Gosu aanraken. Met `./gradlew smokeTest -PsmokeJava=25` kan de test worden uitgevoerd met de JDK die door de server wordt gebruikt.
* **JRuby**: `sort_by` is niet stabiel; om de oorspronkelijke volgorde te behouden, moet de index worden opgenomen in de sorteersleutel.
* **Scala 3**: Na het verwijderen van bronbestanden kan incrementele compilatie oude `.tasty`-bestanden achterlaten, die in de build cache terechtkomen. Als er ongewenste klassen in de jar opduiken, voer dan `./gradlew clean build --rerun-tasks` uit.

## Geverifieerd / Niet geverifieerd

Geverifieerd (Windows 11, JDK 21, Gradle 9.7.1):

* Alle 12 gecompileerde talen compileren succesvol, alle 8 scriptlagen laden correct, `./gradlew clean build --rerun-tasks` slaagt.
* `checkGeometry`: 10 QuickCheck-eigenschappen slagen, inclusief round-trip, blok/punt-consistentie, gezichtsveldrotatie en vectorrotatie-consistentie, en `zBase` als naar beneden afgeronde deling.
* `smokeTest` (in totaal 123.134 controles, uitgevoerd in een geïsoleerde class loader vanuit een pad met Chinese tekens om plugin-loading te simuleren; uitgevoerd op zowel JDK 21 als 25):
  * Resultaten van Flix-regels, Clojure-configuratie, Haxe-belichting en Fantom-zoekacties komen item voor item overeen met de oorspronkelijke Kotlin-implementatie;
  * Resultaten van de 8 scripts en Kawa komen item voor item overeen met de originele code die ze vervangen; de jury stemt 9 uit 9 unaniem zonder waarschuwingen te loggen; scriptlagen printen niets naar `System.err` tijdens het laden;
  * Lagen van Scala/Gosu/Groovy/Xtend/Kotlin kunnen linken, en de runtimes van Gosu/Groovy/Scala initialiseren foutloos.

**Niet geverifieerd**:

* Niet getest op een echte Paper- / Folia-server, noch ingelogd met een client (daarvoor moet de Minecraft EULA geaccepteerd worden en PacketEvents geïnstalleerd zijn). Serverafhankelijke paden zoals packet-herschrijving, chunk-verzending en herpositionering worden uitsluitend gedekt door compilatiecontroles en de garantie van een "regel-voor-regel-port van het origineel". Test op een testserver vóór ingebruikname.
* De jar is ongeveer 231 MB (origineel 4,9 MB): Kotlin-compiler, JRuby, Jython, ABCL, plus de runtimes van Gosu, Groovy, Scala, Clojure, Frege, Kawa. Het opstarten van de plugin kost 4–5 seconden extra om de scriptlagen te laden. Deze runtimes zijn ongewijzigd ingesloten, zonder relocation: als er op dezelfde server andere plugins draaien met verschillende versies van Kotlin/Scala/Groovy, kunnen er conflicten optreden.

## Mappenstructuur

```
src/api/java        Laag 0: Contracten van de pure rekenlaag (JDK-typen)
src/kernel/java     Laag 2: Gedeelde status en service-interfaces (Bukkit + PacketEvents)
src/main/<taal>     Broncode per taal (scriptlagen in src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog})
src/main/resources  plugin.yml, config.yml
buildSrc            Gradle-compilatietaken voor elke taal
build.gradle.kts    Lagen en afhankelijkheden
```
