# RotatedWorldZ

Versione in 20 linguaggi di RotatedWorld (un plugin Paper/Folia che ruota l'intero mondo di 90° attorno all'asse X per i giocatori usando PacketEvents):
**Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe** compilati insieme,
più 8 layer di scripting caricati a runtime: **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog**, il tutto impacchettato in **un unico jar**.


## Chi è responsabile di cosa

| Layer | Linguaggio | Responsabilità | Perché questo linguaggio? |
|---|---|---|---|
| 0 api | Java | Contratti che il layer di puro calcolo deve implementare (solo tipi JDK) | Il minimo comune denominatore compreso da tutti i compilatori |
| 1 puro calcolo | **Frege** | Algebra delle coordinate: definizione canonica della rotazione, più 10 proprietà QuickCheck | Funzioni pure + QuickCheck integrato; compila in metodi `static int/float/double`, senza boxing |
| | **Flix** | Regole di denominazione dei blocchi (da terra ↔ a parete, tipi di tile entity); recupero della cache | Tabelle di regole + effect system garantiscono la purezza; "quali chunk servono ancora" è una regola **Datalog** |
| | **Clojure** | Parsing della configurazione: opzioni di configurazione scritte come dati di schema | Data-driven: aggiungere un'opzione di configurazione = aggiungere una riga di dati |
| | **Haxe** | Rotazione dell'array di nibble di luce | Usa solo `haxe.io.Bytes`, lo stesso codice compila in JS/C++ (es. visualizzatore offline) |
| | **Fantom** | Strategia di ricerca per appoggi sicuri | Algoritmo puro, osserva il mondo tramite l'interfaccia `BlockProbe`, senza toccare Bukkit |
| 2 kernel | Java | Stato del giocatore `PlayerState`, `SerialExecutor`, interfacce `Host` / `BlockRotator` | Stato condiviso concorrente con `volatile` / tipi atomici, più diretto da scrivere in Java |
| 3 engine | **Scala 3** | Cache dei chunk `ChunkStore` + motore di visuale `ViewManager` (origine fluttuante, prefetch, invio chunk) | Nucleo delle prestazioni, molti cicli su array |
| | **Gosu** | `WorldProbe`: controlli su blocchi Bukkit / bounding box; enhancement di `Material` | Gli enhancement aggiungono proprietà ai tipi Bukkit (`type.Hazard`) |
| | **Groovy** | Orchestrazione del flusso del comando `/rotate` | Cold path |
| | **Kawa Scheme** | Pulizia della cache: raccoglie la portata visiva di ogni mondo e la passa al Datalog di Flix per decidere cosa mantenere (in origine nel `ViewManager` di Scala) | Compilazione AOT (ahead-of-time), elaborazione di liste |
| | **Xtend** | `BlockRotation`: regole di rotazione per BlockData | Auto-cast con `instanceof` + sintassi delle proprietà, le regole si leggono come la specifica stessa |
| 4 plugin | **Kotlin** | Riscrittura dei pacchetti (hot path di Netty), eventi Bukkit, facciata per i vettori di PacketEvents | Codice originale; la sintassi Kotlin è la più naturale con PacketEvents |
| | Java | Entry point del plugin (implementa `Host`), bridge di avvio per Flix/Fantom/Clojure, loader dei layer di script | Entry point e assemblaggio |
| 5 script | **Kotlin Script** | Decisione di ricentramento dell'origine fluttuante (in origine nel `ViewManager` di Scala) | Compilato in bytecode a runtime, sufficientemente veloce per l'invocazione a ogni tick |
| | **JRuby** | Ordine di invio delle colonne di chunk (in origine nel `ViewManager` di Scala) | Una singola catena `product.each_with_index.sort_by` |
| | **JavaScript** | Bypass del rubberband per "movimento troppo veloce" (in origine nel listener Kotlin) | Interpretato tramite Rhino, eventi rari |
| | **Jython** | Knockback (in origine nel listener Kotlin) | Eseguito una volta per ogni colpo subito |
| | **Lua** | Danno da caduta (in origine nel listener Kotlin) | Giurato capo |
| | **BeanShell** | La riga di output per `/rotate status` (in origine in Groovy) | Scripting con sintassi Java |
| | **Common Lisp** | Quali controlli lato server bypassare per i giocatori ruotati (movimenti falliti, kick per volo) (in origine nel listener Kotlin) | Una tabella decisionale `cond` |
| | **Prolog** | Grammatica degli argomenti di `/rotate`, scritta come DCG (in origine in Groovy) | Le grammatiche vanno scritte in DCG |
| | **Tutti gli otto + Kawa** | **Giuria del danno da caduta**: nove linguaggi calcolano ciascuno una volta, vince la maggioranza, in caso di parità decide Lua, warning registrato in caso di disaccordo | Intrattenimento |

**Il Layer 1 a livello di build non vede né Bukkit né PacketEvents** (riceve solo `api`), e i cinque linguaggi non si vedono tra loro. Le chiamate verso il basso sono normali chiamate JVM; le chiamate verso l'alto passano unicamente tramite interfacce: Flix e Fantom passano le implementazioni a `api.Services`, e la classe principale del plugin fornisce servizi al Layer 3 tramite `kernel.Host`.

## Layer di scripting

Gli script non vengono compilati dentro il jar, ma caricati all'avvio del plugin dagli interpreti incorporati (`script.ScriptLayers`, 8 interpreti avviati in parallelo, circa 4–5 secondi):

* Il codice sorgente si trova in `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*`, impacchettato durante il build sotto `scripts/` nel jar.
* Se esiste `plugins/RotatedWorldZ/scripts/<cartella>/rules.*`, questo sovrascrive quello nel jar: **per modificare le regole non serve ricompilare, basta riavviare il server**.
* Le interfacce implementate dagli script si trovano in `api.ScriptedRules`. L'ultima espressione di Kotlin Script deve essere un oggetto che implementa contemporaneamente `Recenter` e `FallJuror`, e non può essere un oggetto anonimo; gli altri script definiscono solo funzioni globali (Prolog usa predicati, con l'ultimo argomento come risultato), che Java incapsula in interfacce.
* Nessun interprete incorporato garantisce la thread safety, quindi le invocazioni di ciascuno script sono serializzate (`synchronized`). Di conseguenza, solo Kotlin Script (compilato ed eseguito) si trova sull'hot path a ogni tick, mentre tutti gli altri sono su cold path.
* Le librerie incluse di Jython (jnr/jffi, ecc.) sono in conflitto di versione con JRuby, quindi il build impacchetta prima Jython separatamente e rilocare questi package (task `jythonIsolated`, seguendo il modello upstream).

Punti in cui la coerenza cross-language è garantita:

* La mappatura delle facce dei blocchi (UP→NORTH……) non è più scritta a mano: Xtend (`BlockFace` di Bukkit) e Kotlin (`BlockFace` di PacketEvents) sono entrambi derivati dalla stessa rotazione definita in Frege.
* La mappatura delle coordinate all'interno della section `Geometry.localIndex` (Frege) e la rotazione della luce in Haxe usano esattamente la stessa mappatura, verificata blocco per blocco in `smokeTest`.

## Build

```bash
./gradlew build          # Output: build/libs/RotatedWorldZ-1.0.0.jar; include checkGeometry e smokeTest
./gradlew checkGeometry  # Esegue solo le proprietà QuickCheck di Frege
./gradlew smokeTest      # Non richiede un server: avvia il layer di calcolo puro dal jar finito e confronta con il comportamento Kotlin originale, verifica i collegamenti degli altri layer
```

A runtime è necessario che sul server sia installato PacketEvents (`depend: [packetevents]`).

Il primo build scarica Flix, Haxe e Fantom in `.tools/` (circa 60 MB); Haxe richiede inoltre un JDK 8 (usa solo il suo `rt.jar`; se la toolchain Gradle non lo trova, lo scarica automaticamente tramite foojay). Tutto il resto usa JDK 21.

## Insidie dei vari linguaggi

* **Flix**: Può esportare una sola funzione di entry point, generata nella classe `Main` del package predefinito (`FlixBridge` la invoca tramite reflection). Genera inoltre circa 500 classi e package nella root (`Array`, `List`……), che oscurerebbero `Array`/`List` di Scala/Kotlin; perciò l'output di Flix **non si trova su alcun classpath di compilazione**, ma entra solo a runtime e nel jar. I guard di Datalog catturano al massimo 5 variabili. Le interfacce Java e i moduli Flix non possono avere lo stesso nome.
* **Frege**: I parametri sono lazy per impostazione predefinita (nella firma Java appaiono come `Lazy<Float>`); per avere parametri stretti di tipo primitivo, aggiungere `!` nel pattern.
* **Fantom**: L'implementazione di interfacce Java con metodi `default` provoca un crash, quindi le interfacce in `api` non hanno metodi `default`. `long`/`double`/`boolean` di Java corrispondono direttamente a `Int`/`Float`/`Bool` di Fantom; tutti i tipi di riferimento sono nullable. I letterali a virgola mobile vanno scritti come `0.5f` (`0.5` è Decimal).
* **Gosu**: `block` è una parola chiave (le lambda di Gosu). gosuc risolve immediatamente i tipi di annotazione, quindi il classpath di compilazione richiede le dipendenze Paper API `org.jetbrains:annotations` e `jspecify`.
* **Xtend**: `Keyed` di Bukkit ha sia `key()` sia `getKey()`; `b.key` è ambiguo e va scritto `b.getKey`. Quando un campo e un metodo hanno lo stesso nome, l'identificatore all'interno del corpo del metodo fa riferimento al metodo.
* **Haxe**: Target `--jvm`; il class loader esterno Java riconosce solo l'`rt.jar` di JDK 8; il pacchetto ufficiale Windows per `haxelib` dipende da Neko, quindi durante il build viene inserito uno stub nel PATH.
* **Clojure**: Si affida al thread context class loader per trovare i namespace; `ClojureBridge` punta temporaneamente al class loader del plugin durante l'inizializzazione e le chiamate.
* **Kotlin Script**: Il risultato dello script non può essere un oggetto anonimo (`object : X {}` genera l'errore "anonymous type"); è necessario dichiarare preventivamente una classe con nome.
* **Kawa**: Il `null` di Java è un valore vero (truthy) in Scheme (solo `#f` è falso); i controlli vanno scritti come `(eq? x #!null)`; `obj:field` legge i campi, `(obj:method)` invoca i metodi.
* **Thread context class loader**: I thread del server (thread principale, thread regionali, netty, scheduler globale) hanno un context class loader che non vede le classi del plugin, mentre i runtime di Kawa e Clojure usano proprio quello per trovare le classi per nome. Pertanto, qualsiasi punto in cui si entra in questi runtime da un thread del server viene incapsulato con `kernel.PluginContext` (pulizia della cache di Kawa, giuria, tutte le chiamate agli script, ricerca degli appoggi in Gosu/Fantom, comandi Groovy). Nel `smokeTest`, il thread principale imposta il context class loader sul platform loader per simulare un thread del server.
* **Prolog**: tuProlog non carica DCG per impostazione predefinita; occorre chiamare `loadLibrary(new DCGLibrary())` per avere a disposizione `-->` e `phrase/2`.
* **Jython**: `PyString` è una stringa di byte di Python 2; quando il percorso contiene caratteri non ASCII, occorre usare `Py.newStringOrUnicode`. Il `smokeTest` viene quindi eseguito da un percorso non ASCII come `build/smoke/插件 plugins/`.
* **Common Lisp (ABCL)**: All'inizializzazione tenta di usare la reflection per aprire la factory dei thread virtuali di JDK; in assenza di `--add-opens java.base/java.lang`, stampa una riga su `System.err` (Paper per questo emette l'avviso "il plugin ha usato System.err"). Innocuo; `StderrFilter` intercetta solo questa riga durante l'inizializzazione di ABCL.
* **Giuria**: `ceil(2.5 - 3)` restituisce `-0.0`; il `max` di Python / Kotlin / Scheme lo preserva inalterato. Deve essere normalizzato a `0.0` prima del conteggio dei voti, altrimenti cadute di 2–3 blocchi segnalerebbero erroneamente un "disaccordo".
* **Gosu**: Non appena il runtime si inizializza, apre `java.base` alla reflection, mascherando eventuali problemi di reflection di altre librerie in seguito. Perciò `smokeTest` segue l'ordine di `onEnable` del plugin: carica prima il layer di script, poi tocca Gosu. `./gradlew smokeTest -PsmokeJava=25` consente di eseguire il test con il JDK usato dal server.
* **JRuby**: `sort_by` è instabile; per mantenere l'ordine originale, l'indice va inserito nella chiave di ordinamento.
* **Scala 3**: Dopo aver eliminato file sorgente, la compilazione incrementale può lasciare vecchi file `.tasty`, che finiscono nella build cache. Se nel jar compaiono classi indesiderate, eseguire `./gradlew clean build --rerun-tasks`.

## Verificato / Non verificato

Verificato (Windows 11, JDK 21, Gradle 9.7.1):

* Tutti i 12 linguaggi compilati compilano con successo, tutti gli 8 layer di script si caricano correttamente, `./gradlew clean build --rerun-tasks` supera il build.
* `checkGeometry`: 10 proprietà QuickCheck superate, inclusi round-trip, coerenza blocco/punto, coerenza tra rotazione della visuale e rotazione dei vettori, `zBase` come divisione intera arrotondata per difetto.
* `smokeTest` (123.134 verifiche complessive, eseguite in un class loader isolato, avviato da un percorso contenente caratteri cinesi, simulando il caricamento del plugin; eseguito sia su JDK 21 che su 25):
  * I risultati delle regole Flix, della configurazione Clojure, della luce Haxe e della ricerca Fantom corrispondono punto per punto all'implementazione Kotlin originale;
  * I risultati degli 8 script e di Kawa corrispondono punto per punto al codice originale sostituito; la giuria vota all'unanimità (9 voti su 9) senza registrare alcun warning; nessun output su `System.err` durante il caricamento dei layer di script;
  * I layer Scala/Gosu/Groovy/Xtend/Kotlin si collegano correttamente, e i runtime di Gosu/Groovy/Scala si inizializzano senza errori.

**Non verificato**:

* Non è stato testato su un server Paper / Folia reale, né vi è stato effettuato l'accesso con un client (ciò richiederebbe l'accettazione dell'EULA di Minecraft e l'installazione di PacketEvents). I percorsi dipendenti dal server, come la riscrittura dei pacchetti, l'invio dei chunk e il ricentramento, sono coperti solo da verifiche in fase di compilazione e dalla garanzia del "porting riga per riga dall'originale". Verificare su un server di test prima dell'uso in produzione.
* Il jar è di circa 231 MB (l'originale era di 4,9 MB): compilatore Kotlin, JRuby, Jython, ABCL, più i runtime di Gosu, Groovy, Scala, Clojure, Frege, Kawa. L'avvio del plugin richiede 4–5 secondi in più per caricare i layer di script. Questi runtime sono inclusi così come sono, senza relocation: se sullo stesso server altri plugin includono versioni diverse di Kotlin/Scala/Groovy, potrebbero verificarsi conflitti.

## Struttura delle directory

```
src/api/java        Layer 0: Contratti del layer di puro calcolo (tipi JDK)
src/kernel/java     Layer 2: Stato condiviso e interfacce di servizio (Bukkit + PacketEvents)
src/main/<linguaggio> Sorgenti di ciascun linguaggio (layer di script in src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog})
src/main/resources  plugin.yml, config.yml
buildSrc            Task Gradle di compilazione per ciascun linguaggio
build.gradle.kts    Suddivisione in layer e dipendenze
```
