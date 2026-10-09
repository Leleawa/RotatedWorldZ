# RotatedWorldZ

Una versión en 20 lenguajes de RotatedWorld (un plugin para Paper/Folia que rota todo el mundo 90° alrededor del eje X para el jugador utilizando PacketEvents):
Compilado con **Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe**,
más 8 capas de scripting cargadas en tiempo de ejecución: **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog**, todo empaquetado en **un único archivo JAR**.


## Quién se encarga de qué

| Capa | Lenguaje | Responsabilidad | Por qué este lenguaje |
|---|---|---|---|
| 0 api | Java | Contratos que debe implementar la capa de cálculo puro (solo tipos del JDK) | El mínimo común denominador que entienden todos los compiladores |
| 1 Cálculo puro | **Frege** | Álgebra de coordenadas: la única definición de rotación, más 10 propiedades de QuickCheck | Funciones puras + QuickCheck integrado; compila a métodos `static int/float/double` sin boxing |
| | **Flix** | Reglas de nomenclatura de bloques (vertical ↔ pared, tipos de block entities); desalojo de caché | Tablas de reglas + sistema de efectos que garantizan pureza; «qué chunks siguen siendo necesarios» es una regla **Datalog** |
| | **Clojure** | Análisis de configuración: opciones definidas como datos de esquema | Dirigido por datos: añadir una opción = añadir una línea de datos |
| | **Haxe** | Rotación de arrays nibble de iluminación | Solo usa `haxe.io.Bytes`, el mismo código compila a JS/C++ (p. ej., para un visor sin conexión) |
| | **Fantom** | Estrategia de búsqueda de apoyos seguros | Algoritmo puro, observa el mundo a través de la interfaz `BlockProbe`, no toca Bukkit |
| 2 kernel | Java | Estado del jugador `PlayerState`, `SerialExecutor`, interfaces `Host` / `BlockRotator` | Estado compartido concurrente con volatile / variables atómicas, lo más directo en Java |
| 3 Motor | **Scala 3** | Caché de chunks `ChunkStore` + motor de campo visual `ViewManager` (origen flotante, precarga, envío de chunks) | Núcleo de rendimiento, bucles masivos sobre arrays |
| | **Gosu** | `WorldProbe`: comprobaciones de bloques / hitboxes de Bukkit; enhancements de `Material` | Los enhancements agregan propiedades a los tipos de Bukkit (`type.Hazard`) |
| | **Groovy** | Orquestación del flujo del comando `/rotate` | Ruta fría (Cold Path) |
| | **Kawa Scheme** | Limpieza de caché: recopila el rango visual de cada mundo y lo entrega al Datalog de Flix para decidir cuáles conservar (antes en el `ViewManager` de Scala) | Compilación anticipada (AOT), procesamiento de listas |
| | **Xtend** | `BlockRotation`: reglas de rotación de BlockData | Autocasting en `instanceof` + sintaxis de propiedades; las reglas se leen como la especificación misma |
| 4 Plugin | **Kotlin** | Reescribir paquetes (ruta caliente de Netty), eventos de Bukkit, fachada de vectores de PacketEvents | Código original; la sintaxis de PacketEvents en Kotlin es la más fluida |
| | Java | Punto de entrada del plugin (implementa `Host`), puente de arranque para Flix/Fantom/Clojure, cargador de capas de script | Entrada y ensamblaje |
| 5 Scripts | **Kotlin Script** | Decisión de reposicionamiento del origen flotante (antes en el `ViewManager` de Scala) | Compilado a bytecode en tiempo de ejecución, rápido incluso ejecutándose cada tick |
| | **JRuby** | Orden de envío de columnas de chunks (antes en el `ViewManager` de Scala) | Una cadena `product.each_with_index.sort_by` |
| | **JavaScript** | Aprobación de tirones por «movimiento excesivamente rápido» (antes en el listener de Kotlin) | Interpretado por Rhino, eventos muy infrecuentes |
| | **Jython** | Retroceso (Knockback; antes en el listener de Kotlin) | Ejecutado una vez por cada golpe recibido |
| | **Lua** | Daño por caída (antes en el listener de Kotlin) | Jurado principal |
| | **BeanShell** | La línea de texto de `/rotate status` (antes en Groovy) | Script con sintaxis Java |
| | **Common Lisp** | Qué comprobaciones del servidor se autorizan para jugadores rotados (movimientos fallidos, expulsión por vuelo) (antes en el listener de Kotlin) | Una tabla de decisión `cond` |
| | **Prolog** | Sintaxis de argumentos de `/rotate`, escrita en DCG (antes en Groovy) | La sintaxis debe escribirse con DCG |
| | **Los ocho + Kawa** | **Jurado de daño por caída**: Nueve lenguajes calculan de forma independiente, voto mayoritario, desempate a cargo de Lua, se registra advertencia si hay discrepancias | Entretenimiento |

**La capa 1 no puede ver Bukkit ni PacketEvents a nivel de compilación** (solo recibe `api`), y los cinco lenguajes tampoco se ven entre sí. Las llamadas hacia abajo son llamadas normales de la JVM; las llamadas hacia arriba solo pueden realizarse mediante interfaces: Flix y Fantom entregan sus implementaciones a `api.Services`, y la clase principal del plugin proporciona servicios a la capa 3 mediante `kernel.Host`.

## Capas de scripting

Los scripts no se compilan en el JAR, sino que se cargan mediante intérpretes integrados al iniciar el plugin (`script.ScriptLayers`, 8 intérpretes iniciados en paralelo en unos 4–5 segundos):

* El código fuente reside en `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*` y se empaqueta en `scripts/` dentro del JAR durante la compilación.
* Si existe `plugins/RotatedWorldZ/scripts/<directorio>/rules.*`, sobrescribe el archivo del JAR: **modificar reglas no requiere recompilar, basta con reiniciar el servidor**.
* Las interfaces implementadas por los scripts están en `api.ScriptedRules`. La última expresión de Kotlin Script debe ser un objeto que implemente tanto `Recenter` como `FallJuror`, y no puede ser anónimo; el resto de los scripts solo definen funciones globales (en Prolog son predicados cuyo último argumento es el resultado), que Java envuelve en interfaces.
* Ninguno de los intérpretes integrados garantiza seguridad de subprocesos (thread safety); las llamadas a cada script son secuenciales (`synchronized`). Por lo tanto, solo Kotlin Script (ejecutado compilado) está en la ruta caliente de cada tick, mientras que el resto está en rutas frías.
* Las librerías jnr/jffi incluidas en Jython tienen conflictos de versión con JRuby, por lo que Jython se empaqueta por separado durante la compilación y se reubican estos paquetes (tarea `jythonIsolated`, siguiendo la práctica de la plantilla upstream).

Garantías de coherencia entre lenguajes:

* El mapeo de caras de bloques (UP→NORTH…) ya no se escribe a mano; tanto Xtend (Bukkit `BlockFace`) como Kotlin (PacketEvents `BlockFace`) se deducen de la misma rotación en Frege.
* El mapeo de coordenadas dentro de una sección `Geometry.localIndex` (Frege) y la rotación de iluminación en Haxe utilizan exactamente el mismo mapeo, contrastado bloque por bloque en `smokeTest`.

## Build

```bash
./gradlew build          # Artefacto: build/libs/RotatedWorldZ-1.0.0.jar; incluye checkGeometry y smokeTest
./gradlew checkGeometry  # Ejecuta solo las propiedades de QuickCheck de Frege
./gradlew smokeTest      # No requiere servidor: inicia la capa de cálculo puro desde el JAR terminado, compara con el comportamiento original de Kotlin y verifica el enlazado de las demás capas
```

En tiempo de ejecución se requiere tener PacketEvents instalado en el servidor (`depend: [packetevents]`).

La primera compilación descargará Flix, Haxe y Fantom en `.tools/` (unos 60 MB); Haxe requiere además un JDK 8 (únicamente por su `rt.jar`; si la toolchain de Gradle no lo encuentra, lo descargará automáticamente mediante Foojay). Para todo lo demás se utiliza JDK 21.

## Particularidades y trampas de cada lenguaje

* **Flix**: Solo puede exportar una función de entrada, generada en la clase `Main` del paquete por defecto (`FlixBridge` la invoca por reflexión). También genera unos 500 paquetes y clases en la raíz (`Array`, `List`…), lo que ocultaría los propios `Array`/`List` de Scala/Kotlin; por ello, la salida de Flix **no está en ningún classpath de compilación**, solo entra al runtime y al JAR. Los guards de Datalog capturan como máximo 5 variables. Las interfaces de Java y los módulos de Flix no pueden llamarse igual.
* **Frege**: Los argumentos son perezosos (lazy) por defecto (en las firmas de Java aparecen como `Lazy<Float>`); para parámetros primitivos estrictos se debe añadir `!` en el patrón.
* **Fantom**: Implementar interfaces de Java con métodos default provoca fallos; por ello, las interfaces en `api` no tienen métodos default. Los tipos `long`/`double`/`boolean` de Java se corresponden directamente con `Int`/`Float`/`Bool` de Fantom, y todos los tipos de referencia aceptan nulos (nullable). Los literales de coma flotante deben escribirse como `0.5f` (`0.5` es Decimal).
* **Gosu**: `block` es una palabra clave (la lambda de Gosu). gosuc resuelve los tipos de anotaciones de inmediato, por lo que el classpath de compilación debe incluir las dependencias de la Paper API `org.jetbrains:annotations` y `jspecify`.
* **Xtend**: `Keyed` de Bukkit tiene tanto `key()` como `getKey()`; `b.key` es ambiguo, por lo que debe escribirse `b.getKey`. Cuando un campo y un método comparten nombre, el identificador en el cuerpo del método apunta al método.
* **Haxe**: Destino `--jvm`; el cargador de clases externas de Java solo reconoce el `rt.jar` de JDK 8; el `haxelib` del paquete oficial de Windows depende de Neko, por lo que durante la compilación se coloca un stub en el PATH.
* **Clojure**: Depende del Thread Context ClassLoader para localizar espacios de nombres; `ClojureBridge` apunta temporalmente al classloader del plugin durante la inicialización y las invocaciones.
* **Kotlin Script**: El resultado del script no puede ser un objeto anónimo (`object : X {}` genera el error "anonymous type"); debe declararse antes una clase con nombre.
* **Kawa**: `null` de Java se evalúa como verdadero en Scheme (solo `#f` es falso); las comprobaciones se escriben `(eq? x #!null)`; `obj:field` lee campos y `(obj:method)` invoca métodos.
* **Thread Context ClassLoader**: El context classloader de los hilos del servidor (hilo principal, hilos de región, Netty, programador global) no ve las clases del plugin, pero los entornos de ejecución de Kawa y Clojure lo utilizan precisamente para buscar clases por nombre. Por ello, cualquier entrada desde un hilo del servidor a estos entornos se envuelve con `kernel.PluginContext` (limpieza de caché en Kawa, jurado, todas las llamadas a scripts, búsqueda de apoyos en Gosu/Fantom, comandos de Groovy). El hilo principal de `smokeTest` configura en consecuencia el context classloader con el cargador de la plataforma para simular los hilos del servidor.
* **Prolog**: tuProlog no carga DCG por defecto; es necesario ejecutar `loadLibrary(new DCGLibrary())` para disponer de `-->` y `phrase/2`.
* **Jython**: `PyString` es una cadena de bytes de Python 2; si la ruta contiene caracteres chinos, se debe usar `Py.newStringOrUnicode`. Por este motivo, `smokeTest` se ejecuta desde una ruta no ASCII como `build/smoke/插件 plugins/`.
* **Common Lisp (ABCL)**: Durante la inicialización intenta usar reflexión para abrir la factoría de hilos virtuales del JDK; sin `--add-opens java.base/java.lang`, imprime una línea en `System.err` (lo que hace que Paper advierta «el plugin usó System.err»). Es inofensivo; `StderrFilter` intercepta únicamente esa línea durante la inicialización de ABCL.
* **Jurado**: `ceil(2.5 - 3)` es `-0.0`, y el `max` de Python / Kotlin / Scheme lo conserva tal cual; antes de contar votos debe normalizarse a `0.0`, ya que de lo contrario generará falsos avisos de «discrepancia» en caídas de 2 a 3 bloques.
* **Gosu**: En cuanto se inicializa su runtime, abre `java.base` a la reflexión, tras lo cual los problemas de reflexión de otras librerías pasan desapercibidos. Por ello, `smokeTest` sigue el orden de `onEnable` del plugin, cargando primero las capas de scripts antes de tocar Gosu. `./gradlew smokeTest -PsmokeJava=25` permite ejecutarlo con el JDK utilizado por el servidor.
* **JRuby**: `sort_by` no es estable; para preservar el orden original debe incluirse el índice en la clave de ordenación.
* **Scala 3**: Al eliminar archivos fuente, la compilación incremental puede dejar archivos `.tasty` obsoletos que entran en la caché de build. Si aparecen clases indebidas en el JAR, ejecute `./gradlew clean build --rerun-tasks`.

## Verificado / No verificado

Verificado (Windows 11, JDK 21, Gradle 9.7.1):

* Los 12 lenguajes compilados compilan con éxito, las 8 capas de scripting se cargan correctamente, `./gradlew clean build --rerun-tasks` pasa sin errores.
* `checkGeometry`: 10 propiedades de QuickCheck aprobadas, incluyendo ida y vuelta (roundtrip), coherencia celda/punto, coherencia entre rotación de vista y de vectores, y `zBase` como división entera hacia abajo.
* `smokeTest` (123.134 comprobaciones en total, ejecutadas en un classloader aislado desde una ruta con caracteres chinos simulando la carga del plugin; probado en JDK 21 y JDK 25):
  * Las reglas de Flix, configuración de Clojure, iluminación de Haxe y búsqueda de Fantom coinciden punto por punto con la implementación original en Kotlin;
  * Los resultados de los 8 scripts y Kawa coinciden punto por punto con el código original que reemplazan; los nueve votos del jurado son unánimes sin registrar ninguna advertencia; la carga de las capas de scripts no produce salidas en `System.err`;
  * Las capas de Scala/Gosu/Groovy/Xtend/Kotlin enlazan correctamente y los entornos de ejecución de Gosu/Groovy/Scala se inicializan sin fallos.

**No verificado**:

* No se ha ejecutado en un servidor Paper / Folia real ni se ha probado el ingreso con un cliente de juego (requiere aceptar el EULA de Minecraft e instalar PacketEvents). Las rutas dependientes del servidor (reescritura de paquetes, envío de chunks, reposicionamiento) solo cuentan con comprobaciones de compilación y la garantía de una «migración línea por línea del código original». Pruebe en un servidor de pruebas antes de llevarlo a producción.
* JAR de aprox. 231 MB (frente a los 4.9 MB originales): incluye el compilador de Kotlin, JRuby, Jython, ABCL, más los runtimes de Gosu, Groovy, Scala, Clojure, Frege y Kawa. El inicio del plugin tarda entre 4 y 5 segundos adicionales en cargar las capas de scripts. Estos entornos de ejecución se empaquetan tal cual sin reubicar (no relocate): si en el mismo servidor conviven otros plugins con versiones diferentes de Kotlin/Scala/Groovy, podrían surgir conflictos.

## Directorios

```
src/api/java        Capa 0: contratos de la capa de cálculo puro (tipos del JDK)
src/kernel/java     Capa 2: estado compartido e interfaces de servicio (Bukkit + PacketEvents)
src/main/<lenguaje> Código fuente de cada lenguaje (capas de script en src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog})
src/main/resources  plugin.yml, config.yml
buildSrc            Tareas de compilación de Gradle para cada lenguaje
build.gradle.kts    Capas y dependencias
```
