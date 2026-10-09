# RotatedWorldZ

Une version en 20 langages de RotatedWorld (un plugin Paper/Folia qui fait pivoter l'ensemble du monde de 90° autour de l'axe X pour le joueur via PacketEvents) :
Compilé avec **Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe**,
plus 8 couches de scripts chargées à l'exécution : **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog**, le tout packagé dans **un seul fichier JAR**.


## Qui fait quoi

| Couche | Langage | Responsabilité | Pourquoi ce langage |
|---|---|---|---|
| 0 api | Java | Contrats à implémenter par la couche de calcul pur (types JDK uniquement) | Le plus petit dénominateur commun compris par tous les compilateurs |
| 1 Calcul pur | **Frege** | Algèbre des coordonnées : l'unique définition de la rotation, plus 10 propriétés QuickCheck | Fonctions pures + QuickCheck intégré ; compilé en méthodes `static int/float/double` sans boxing |
| | **Flix** | Règles de nommage des blocs (sur pied ↔ mural, types de block entities) ; éviction du cache | Tables de règles + système d'effets garantissant la pureté ; « quels chunks sont encore requis » est une règle **Datalog** |
| | **Clojure** | Analyse de configuration : options définies sous forme de schéma de données | Piloté par les données : ajouter une option de configuration = ajouter une ligne de données |
| | **Haxe** | Rotation des tableaux de nibbles d'éclairage | Utilise uniquement `haxe.io.Bytes`, le même code compile vers JS/C++ (par ex. pour un prévisualiseur hors ligne) |
| | **Fantom** | Stratégie de recherche de points d'appui sécurisés | Algorithme pur, inspecte le monde via l'interface `BlockProbe`, ne touche pas à Bukkit |
| 2 kernel | Java | État joueur `PlayerState`, `SerialExecutor`, interfaces `Host` / `BlockRotator` | État partagé concurrent avec volatile / variables atomiques, le plus direct en Java |
| 3 Moteur | **Scala 3** | Cache de chunks `ChunkStore` + moteur de champ de vision `ViewManager` (origine flottante, préchargement, envoi des chunks) | Cœur de performance, boucles massives sur des tableaux |
| | **Gosu** | `WorldProbe` : vérification des blocs / boîtes de collision Bukkit ; enhancements de `Material` | Les enhancements ajoutent des propriétés aux types Bukkit (`type.Hazard`) |
| | **Groovy** | Orchestration du déroulement de la commande `/rotate` | Chemin froid (Cold Path) |
| | **Kawa Scheme** | Nettoyage du cache : collecte le champ de vision de chaque monde et le transmet au Datalog de Flix pour décider des chunks à conserver (auparavant dans le `ViewManager` Scala) | Compilation anticipée (AOT), traitement de listes |
| | **Xtend** | `BlockRotation` : règles de rotation de BlockData | Cast automatique sur `instanceof` + syntaxe de propriétés, les règles se lisent comme la spécification elle-même |
| 4 Plugin | **Kotlin** | Réécriture de paquets (chemin chaud Netty), événements Bukkit, façade vectorielle PacketEvents | Code d'origine ; PacketEvents s'écrit de la manière la plus fluide en Kotlin |
| | Java | Point d'entrée du plugin (implémente `Host`), passerelle d'initialisation Flix/Fantom/Clojure, chargeur des couches de scripts | Entrée et assemblage |
| 5 Scripts | **Kotlin Script** | Décision de repositionnement de l'origine flottante (auparavant dans le `ViewManager` Scala) | Compilé en bytecode au runtime, rapide même appelé à chaque tick |
| | **JRuby** | Ordre d'envoi des colonnes de chunks (auparavant dans le `ViewManager` Scala) | Une chaîne `product.each_with_index.sort_by` |
| | **JavaScript** | Autorisation des à-coups lors d'un « déplacement trop rapide » (auparavant dans le listener Kotlin) | Interprété par Rhino, événements peu fréquents |
| | **Jython** | Recul (Knockback ; auparavant dans le listener Kotlin) | Déclenché à chaque coup reçu |
| | **Lua** | Dégâts de chute (auparavant dans le listener Kotlin) | Juré en chef |
| | **BeanShell** | Ligne de texte affichée par `/rotate status` (auparavant dans Groovy) | Script avec syntaxe Java |
| | **Common Lisp** | Quelles vérifications serveur autoriser pour les joueurs tournés (mouvements échoués, expulsion pour vol) (auparavant dans le listener Kotlin) | Une table de décision `cond` |
| | **Prolog** | Syntaxe des arguments de `/rotate`, écrite en DCG (auparavant dans Groovy) | Une syntaxe doit être écrite avec une DCG |
| | **Les huit + Kawa** | **Jury des dégâts de chute** : Les neuf langages effectuent chacun le calcul, vote à la majorité, départage par Lua en cas d'égalité, avertissement consigné en cas de divergence | Divertissement |

**La couche 1 ne peut structurellement pas voir Bukkit ni PacketEvents lors du build** (elle ne reçoit que `api`), et les cinq langages ne peuvent pas non plus se voir entre eux. Les appels descendants sont des appels JVM standards ; les appels ascendants passent exclusivement par des interfaces : Flix et Fantom confient leurs implémentations à `api.Services`, et la classe principale du plugin fournit des services à la couche 3 via `kernel.Host`.

## Couches de scripts

Les scripts ne sont pas compilés dans le JAR, mais chargés par des interpréteurs intégrés au démarrage du plugin (`script.ScriptLayers`, 8 interpréteurs démarrés en parallèle en environ 4–5 secondes) :

* Le code source se trouve dans `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*`, packagé dans le répertoire `scripts/` du JAR lors du build.
* Si `plugins/RotatedWorldZ/scripts/<répertoire>/rules.*` existe, il écrase la version du JAR : **modifier les règles ne nécessite aucun re-build, un simple redémarrage du serveur suffit**.
* Les interfaces implémentées par les scripts se trouvent dans `api.ScriptedRules`. La dernière expression d'un Kotlin Script doit être un objet implémentant simultanément `Recenter` et `FallJuror`, sans être un objet anonyme ; les autres scripts se contentent de définir des fonctions globales (pour Prolog, des prédicats dont le dernier argument est le résultat), encapsulées en interfaces par Java.
* Les interpréteurs intégrés ne garantissent pas le thread-safety ; les appels à chaque script sont sérialisés (`synchronized`). Ainsi, seul Kotlin Script (exécuté compilé) se trouve sur le chemin chaud de chaque tick, les autres se trouvant sur le chemin froid.
* Les bibliothèques jnr/jffi intégrées à Jython entrent en conflit de version avec JRuby ; Jython est donc empaqueté séparément lors du build et ces paquets sont relocalisés (tâche `jythonIsolated`, reprenant la pratique du modèle amont).

Garanties de cohérence inter-langages :

* La correspondance des faces de blocs (UP→NORTH…) n'est plus écrite à la main : Xtend (Bukkit `BlockFace`) et Kotlin (PacketEvents `BlockFace`) sont tous deux dérivés de la même rotation Frege.
* La correspondance des coordonnées au sein d'une section `Geometry.localIndex` (Frege) et la rotation d'éclairage Haxe utilisent exactement la même projection, vérifiée bloc par bloc par `smokeTest`.

## Build

```bash
./gradlew build          # Artefact : build/libs/RotatedWorldZ-1.0.0.jar ; inclut checkGeometry et smokeTest
./gradlew checkGeometry  # Exécute uniquement les propriétés QuickCheck de Frege
./gradlew smokeTest      # Aucun serveur requis : démarre la couche de calcul pur depuis le JAR final, compare avec le comportement Kotlin d'origine et vérifie la liaison des autres couches
```

À l'exécution, PacketEvents doit être installé sur le serveur (`depend: [packetevents]`).

Le premier build télécharge Flix, Haxe et Fantom dans `.tools/` (environ 60 Mo) ; Haxe nécessite également un JDK 8 (uniquement pour son `rt.jar` ; si la toolchain Gradle ne le trouve pas, il sera téléchargé automatiquement via Foojay). Tout le reste utilise JDK 21.

## Pièges et particularités par langage

* **Flix** : Ne peut exporter qu'une seule fonction d'entrée, générée dans la classe `Main` du package par défaut (`FlixBridge` l'appelle par réflexion). Il génère également environ 500 classes et packages à la racine (`Array`, `List`…), risquant de masquer les `Array`/`List` propres à Scala/Kotlin ; la sortie de Flix n'est donc **présente sur aucun classpath de compilation**, uniquement au runtime et dans le JAR. Les guards Datalog capturent au maximum 5 variables. Les interfaces Java et les modules Flix ne peuvent pas porter le même nom.
* **Frege** : Les paramètres sont paresseux (lazy) par défaut (visibles sous la forme `Lazy<Float>` dans les signatures Java) ; pour imposer des primitives strictes, il faut ajouter `!` sur le pattern.
* **Fantom** : L'implémentation d'interfaces Java comportant des méthodes default provoque un crash ; les interfaces dans `api` ne comportent donc aucune méthode default. Les types Java `long`/`double`/`boolean` correspondent directement aux types Fantom `Int`/`Float`/`Bool`, et les types référence sont tous nullables. Les littéraux flottants doivent être écrits `0.5f` (`0.5` étant un Decimal).
* **Gosu** : `block` est un mot-clé (la lambda de Gosu). gosuc résout immédiatement les types d'annotations, nécessitant la présence sur le classpath de compilation des dépendances de Paper API `org.jetbrains:annotations` et `jspecify`.
* **Xtend** : `Keyed` de Bukkit possède à la fois `key()` et `getKey()` ; `b.key` étant ambigu, il faut écrire `b.getKey`. Lorsqu'un champ et une méthode portent le même nom, l'identifiant dans le corps de la méthode fait référence à la méthode.
* **Haxe** : Cible `--jvm` ; le chargeur de classes externes Java ne reconnaît que le `rt.jar` de JDK 8 ; `haxelib` dans le package officiel Windows dépend de Neko, un bouchon (stub) est donc placé dans le PATH lors du build.
* **Clojure** : S'appuie sur le Thread Context ClassLoader pour localiser les namespaces ; `ClojureBridge` bascule temporairement vers le ClassLoader du plugin lors de l'initialisation et des appels.
* **Kotlin Script** : Le résultat du script ne peut pas être un objet anonyme (`object : X {}` déclenche l'erreur "anonymous type") ; il est nécessaire de déclarer préalablement une classe nommée.
* **Kawa** : `null` en Java est une valeur vraie en Scheme (seul `#f` est faux) ; les vérifications s'écrivent `(eq? x #!null)` ; `obj:field` lit un champ, `(obj:method)` invoque une méthode.
* **Thread Context ClassLoader** : Le Thread Context ClassLoader des threads serveur (thread principal, threads régionaux, netty, planificateur global) ne voit pas les classes du plugin, alors que les runtimes de Kawa et Clojure l'utilisent précisément pour résoudre les classes par leur nom. Tout point d'entrée depuis un thread serveur vers ces runtimes est donc enveloppé dans `kernel.PluginContext` (nettoyage de cache Kawa, jury, tous les appels de scripts, recherche de points d'appui Gosu/Fantom, commandes Groovy). Le thread principal de `smokeTest` configure ainsi son Context ClassLoader sur le Platform ClassLoader pour simuler les threads serveur.
* **Prolog** : tuProlog ne charge pas DCG par défaut ; il faut exécuter `loadLibrary(new DCGLibrary())` pour disposer de `-->` et `phrase/2`.
* **Jython** : `PyString` est une chaîne d'octets Python 2 ; si le chemin contient des caractères non-ASCII (comme des caractères chinois), il faut utiliser `Py.newStringOrUnicode`. `smokeTest` s'exécute ainsi depuis un chemin non-ASCII tel que `build/smoke/插件 plugins/`.
* **Common Lisp (ABCL)** : Tente par réflexion à l'initialisation d'ouvrir la fabrique de threads virtuels du JDK ; sans `--add-opens java.base/java.lang`, il émet une ligne sur `System.err` (Paper émettant alors un avertissement « le plugin a utilisé System.err »). Inoffensif ; `StderrFilter` intercepte uniquement cette ligne durant l'initialisation d'ABCL.
* **Jury** : `ceil(2.5 - 3)` vaut `-0.0`, que `max` en Python / Kotlin / Scheme conserve tel quel ; avant le décompte des votes, il faut le normaliser en `0.0`, sous peine de faux positifs « avis divergent » lors de chutes de 2 à 3 blocs.
* **Gosu** : Dès son initialisation, le runtime ouvre `java.base` à la réflexion, masquant d'éventuels problèmes de réflexion ultérieurs dans d'autres bibliothèques. `smokeTest` respecte donc l'ordre de `onEnable` du plugin en chargeant d'abord les couches de scripts avant de toucher à Gosu. `./gradlew smokeTest -PsmokeJava=25` permet d'exécuter le test avec le JDK utilisé par le serveur.
* **JRuby** : `sort_by` n'est pas stable ; pour préserver l'ordre initial, l'index doit être inclus dans la clé de tri.
* **Scala 3** : Après suppression de fichiers sources, la compilation incrémentale peut laisser d'anciens fichiers `.tasty` qui s'infiltrent dans le cache de build. Si le JAR contient des classes inattendues, exécuter `./gradlew clean build --rerun-tasks`.

## Vérifié / Non vérifié

Vérifié (Windows 11, JDK 21, Gradle 9.7.1) :

* Les 12 langages compilés compilent tous sans erreur, les 8 couches de scripts se chargent toutes, `./gradlew clean build --rerun-tasks` réussit.
* `checkGeometry` : 10 propriétés QuickCheck validées, incluant l'aller-retour (roundtrip), la cohérence grille/point, la cohérence entre rotation de vue et rotation de vecteur, et `zBase` comme division entière avec arrondi vers le bas.
* `smokeTest` (123 134 vérifications au total, exécutées dans un ClassLoader isolé depuis un chemin contenant des caractères chinois pour simuler le chargement du plugin ; testé sous JDK 21 et JDK 25) :
  * Les règles Flix, la configuration Clojure, l'éclairage Haxe et la recherche Fantom sont rigoureusement identiques point par point à l'implémentation Kotlin d'origine ;
  * Les résultats des 8 scripts et de Kawa sont rigoureusement identiques au code d'origine qu'ils remplacent ; les neuf voix du jury concordent à l'unanimité sans aucun avertissement ; aucun affichage sur `System.err` lors du chargement des couches de scripts ;
  * Les couches Scala/Gosu/Groovy/Xtend/Kotlin s'éditent aux liens (link), les runtimes Gosu/Groovy/Scala s'initialisent correctement.

**Non vérifié** :

* Aucun test sur un serveur Paper / Folia réel ni connexion avec un client de jeu (cela nécessiterait d'accepter l'EULA de Minecraft et d'installer PacketEvents). Les chemins dépendant du serveur (réécriture de paquets, envoi de chunks, repositionnement) ne reposent que sur les vérifications de compilation et une « transposition ligne à ligne du code d'origine ». Veuillez tester sur un serveur dédié avant mise en production.
* JAR d'environ 231 Mo (4,9 Mo pour l'original) : comprend le compilateur Kotlin, JRuby, Jython, ABCL, ainsi que les runtimes Gosu, Groovy, Scala, Clojure, Frege et Kawa. Le plugin prend 4 à 5 secondes supplémentaires au démarrage pour charger les couches de scripts. Ces runtimes sont intégrés tels quels sans relocalisation : si un autre plugin sur le même serveur embarque des versions différentes de Kotlin/Scala/Groovy, des conflits peuvent survenir.

## Répertoires

```
src/api/java        Couche 0 : contrats de la couche de calcul pur (types JDK)
src/kernel/java     Couche 2 : état partagé et interfaces de service (Bukkit + PacketEvents)
src/main/<langage>  Sources des différents langages (couches de scripts dans src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog})
src/main/resources  plugin.yml, config.yml
buildSrc            Tâches de compilation Gradle par langage
build.gradle.kts    Architecture des couches et dépendances
```
