# RotatedWorldZ

Wersja RotatedWorld w 20 językach (plugin dla Paper/Folia, który obraca cały świat o 90° wokół osi X dla graczy przy użyciu PacketEvents):
**Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe** skompilowane razem,
oraz 8 warstw skryptowych ładowanych w czasie wykonywania: **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog**, wszystko spakowane w **jeden plik jar**.


## Kto za co odpowiada

| Warstwa | Język | Odpowiedzialność | Dlaczego ten język? |
|---|---|---|---|
| 0 api | Java | Kontrakty do zaimplementowania przez warstwę czystych obliczeń (tylko typy JDK) | Najmniejszy wspólny mianownik zrozumiały dla wszystkich kompilatorów |
| 1 czyste obliczenia | **Frege** | Algebra współrzędnych: kanoniczna definicja obrotu plus 10 właściwości QuickCheck | Czyste funkcje + wbudowany QuickCheck; kompiluje się do metod `static int/float/double`, bez boxingu |
| | **Flix** | Reguły nazewnictwa bloków (stojące ↔ ścienne, typy tile entities); odzyskiwanie pamięci podręcznej | Tablice reguł + system efektów gwarantują czystość; „które chunki są nadal potrzebne” to reguła **Datalog** |
| | **Clojure** | Parsowanie konfiguracji: opcje konfiguracyjne zapisane jako dane schematu | Sterowanie danymi (data-driven): dodanie opcji konfiguracji = dodanie jednego wiersza danych |
| | **Haxe** | Obrót tablicy nibbli oświetlenia | Używa wyłącznie `haxe.io.Bytes`, ten sam kod kompiluje się do JS/C++ (np. podgląd offline) |
| | **Fantom** | Strategia wyszukiwania bezpiecznego oparcia pod nogami | Czysty algorytm, patrzy na świat przez interfejs `BlockProbe`, bez dotykania Bukkita |
| 2 kernel | Java | Stan gracza `PlayerState`, `SerialExecutor`, interfejsy `Host` / `BlockRotator` | Współbieżny stan współdzielony z `volatile` / zmiennymi atomowymi, najbardziej bezpośredni w Javie |
| 3 silnik | **Scala 3** | Pamięć podręczna chunków `ChunkStore` + silnik widoku `ViewManager` (pływający punkt odniesienia, prefetch, wysyłanie chunków) | Rdzeń wydajnościowy, intensywne pętle na tablicach |
| | **Gosu** | `WorldProbe`: sprawdzanie bloków / bounding boxów Bukkita; enhancement dla `Material` | Enhancements dodają właściwości do typów Bukkita (`type.Hazard`) |
| | **Groovy** | Orkiestracja przepływu komendy `/rotate` | Ścieżka rzadko wykonywana (cold path) |
| | **Kawa Scheme** | Czyszczenie pamięci podręcznej: zbiera zasięg widzenia każdego świata i przekazuje do Dataloga we Flixie w celu podjęcia decyzji (pierwotnie w Scala `ViewManager`) | Kompilacja AOT (ahead-of-time), przetwarzanie list |
| | **Xtend** | `BlockRotation`: reguły obrotu BlockData | Automatyczne rzutowanie przy `instanceof` + składnia właściwości; reguły czyta się jak samą specyfikację |
| 4 plugin | **Kotlin** | Przepisywanie pakietów (hot path Netty), zdarzenia Bukkita, fasada wektorowa PacketEvents | Oryginalny kod; składnia Kotlina jest najbardziej płynna z PacketEvents |
| | Java | Punkt wejścia pluginu (implementuje `Host`), mostki uruchamiające Flix/Fantom/Clojure, loader warstw skryptowych | Punkt wejścia i składanie całości |
| 5 skrypty | **Kotlin Script** | Decyzja o re-centrowaniu pływającego punktu odniesienia (pierwotnie w Scala `ViewManager`) | Kompilowany do bajtkodu w runtime, wystarczająco szybki do wywołań co tick |
| | **JRuby** | Kolejność wysyłania kolumn chunków (pierwotnie w Scala `ViewManager`) | Pojedynczy łańcuch `product.each_with_index.sort_by` |
| | **JavaScript** | Pomijanie cofnięć (rubberband) przy „zbyt szybkim ruchu” (pierwotnie w listenerze Kotlina) | Interpretowany przez Rhino, rzadkie zdarzenia |
| | **Jython** | Odrzut (knockback) (pierwotnie w listenerze Kotlina) | Wykonywany raz przy każdym otrzymaniu obrażeń |
| | **Lua** | Obrażenia od upadku (pierwotnie w listenerze Kotlina) | Główny przysięgły |
| | **BeanShell** | Linijka tekstu dla `/rotate status` (pierwotnie w Groovy) | Skrypty ze składnią Javy |
| | **Common Lisp** | Które kontrole serwera pominąć dla obróconych graczy (nieudane ruchy, kicki za latanie) (pierwotnie w listenerze Kotlina) | Tablica decyzyjna `cond` |
| | **Prolog** | Gramatyka argumentów `/rotate` zapisana jako DCG (pierwotnie w Groovy) | Gramatyki powinny być pisane w DCG |
| | **Wszystkie osiem + Kawa** | **Ława przysięgłych obrażeń od upadku**: dziewięć języków liczy po razie, decyduje większość, w razie remisu rozstrzyga Lua, w razie rozbieżności logowane jest ostrzeżenie | Rozrywka |

**Warstwa 1 na etapie budowania nie ma wglądu w Bukkita ani PacketEvents** (otrzymuje wyłącznie `api`), a pięć języków nie widzi się wzajemnie. Wywołania w dół to zwykłe wywołania JVM; wywołania w górę mogą odbywać się wyłącznie przez interfejsy: Flix i Fantom przekazują implementacje do `api.Services`, a główna klasa pluginu dostarcza usługi dla Warstwy 3 poprzez `kernel.Host`.

## Warstwa skryptowa

Skrypty nie są wkompilowane, lecz ładowane podczas uruchamiania pluginu przez wbudowane interpretery (`script.ScriptLayers`, 8 interpreterów uruchamianych równolegle, około 4–5 sekund):

* Kod źródłowy znajduje się w `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*`, pakowany podczas budowania do katalogu `scripts/` w pliku jar.
* Jeśli plik `plugins/RotatedWorldZ/scripts/<katalog>/rules.*` istnieje, nadpisuje wersję z jara: **zmiana reguł nie wymaga przebudowywania projektu, wystarczy zrestartować serwer**.
* Interfejsy implementowane przez skrypty znajdują się w `api.ScriptedRules`. Ostatnim wyrażeniem w Kotlin Script musi być obiekt implementujący jednocześnie `Recenter` oraz `FallJuror`, i nie może to być obiekt anonimowy; pozostałe skrypty definiują wyłącznie funkcje globalne (Prolog używa predykatów, gdzie ostatni argument to wynik), opakowywane przez Javę w interfejsy.
* Żaden z wbudowanych interpreterów nie gwarantuje bezpieczeństwa wątkowego, więc wywołania każdego skryptu są serializowane (`synchronized`). Dlatego tylko Kotlin Script (wykonywany po skompilowaniu) znajduje się na gorącej ścieżce (hot path) co tick; pozostałe znajdują się na zimnych ścieżkach.
* Dołączone biblioteki Jytona (jnr/jffi itp.) kolidują wersjami z JRuby, dlatego proces budowania najpierw pakuje Jytona osobno i relokuje te pakiety (zadanie `jythonIsolated`, zgodnie ze wzorcem nadrzędnego szablonu).

Punkty gwarantujące spójność między językami:

* Mapowanie stron bloków (UP→NORTH……) nie jest już pisane ręcznie: Xtend (`BlockFace` z Bukkita) i Kotlin (`BlockFace` z PacketEvents) są wyprowadzane z tego samego obrotu w Frege.
* Mapowanie współrzędnych wewnątrz section `Geometry.localIndex` (Frege) oraz obrót oświetlenia w Haxe używają dokładnie tego samego odwzorowania, porównywanego blok po bloku w `smokeTest`.

## Budowanie

```bash
./gradlew build          # Wynik: build/libs/RotatedWorldZ-1.0.0.jar; zawiera checkGeometry oraz smokeTest
./gradlew checkGeometry  # Uruchamia wyłącznie testy właściwości QuickCheck w Frege
./gradlew smokeTest      # Nie wymaga serwera: uruchamia warstwę czystych obliczeń z gotowego jara i porównuje z oryginalnym zachowaniem w Kotlinie, sprawdza dowiązania pozostałych warstw
```

W czasie działania serwer wymaga zainstalowanego PacketEvents (`depend: [packetevents]`).

Pierwsze budowanie pobiera Flixa, Haxe i Fantoma do `.tools/` (ok. 60 MB); Haxe wymaga ponadto JDK 8 (używa tylko jego `rt.jar`; jeśli Gradle toolchain go nie znajdzie, zostanie pobrany automatycznie przez foojay). Cała reszta korzysta z JDK 21.

## Pułapki i specyfika języków

* **Flix**: Może wyeksportować tylko jedną funkcję wejściową, wygenerowaną w klasie `Main` pakietu domyślnego (`FlixBridge` wywołuje ją przez refleksję). Generuje także około 500 klas i pakietów w katalogu głównym (`Array`, `List`……), co przesłoniłoby `Array`/`List` ze Scali/Kotlina; dlatego wyjście Flixa **nie znajduje się na żadnym classpathie kompilacji**, trafia jedynie do środowiska uruchomieniowego i jara. Strażnicy (guards) w Datalogu przechwytują maksymalnie 5 zmiennych. Interfejsy Javy i moduły Flixa nie mogą mieć takich samych nazw.
* **Frege**: Parametry są domyślnie leniwe (lazy) (w sygnaturze Javy pojawiają się jako `Lazy<Float>`); aby uzyskać ścisłe parametry typów pierwotnych, dodaj `!` we wzorcu.
* **Fantom**: Implementacja interfejsów Javy z metodami `default` powoduje awarię; z tego powodu interfejsy w `api` nie posiadają metod `default`. Typy Javy `long`/`double`/`boolean` odpowiadają bezpośrednio typom Fantoma `Int`/`Float`/`Bool`; wszystkie typy referencyjne są nullowalne (nullable). Literały zmiennoprzecinkowe muszą być zapisywane jako `0.5f` (`0.5` oznacza Decimal).
* **Gosu**: `block` jest słowem kluczowym (lambda w Gosu). gosuc natychmiast rozwiązuje typy adnotacji, więc na classpathie kompilacji wymagane są zależności Paper API: `org.jetbrains:annotations` oraz `jspecify`.
* **Xtend**: Bukkitowy `Keyed` posiada zarówno `key()`, jak i `getKey()`; `b.key` jest niejednoznaczne i należy pisać `b.getKey`. Gdy pole i metoda mają tę samą nazwę, identyfikator w ciele metody odnosi się do metody.
* **Haxe**: Cel `--jvm`; zewnętrzny class loader Javy rozpoznaje wyłącznie `rt.jar` z JDK 8; oficjalny pakiet `haxelib` dla Windows zależy od Neko, dlatego podczas budowania umieszczany jest stub w PATH.
* **Clojure**: Polega na kontekstowym class loaderze wątku (TCCL) przy wyszukiwaniu przestrzeni nazw; `ClojureBridge` podczas inicjalizacji i wywołań tymczasowo wskazuje na class loader pluginu.
* **Kotlin Script**: Wynik skryptu nie może być obiektem anonimowym (`object : X {}` zgłasza błąd "anonymous type"); należy wcześniej zadeklarować nazwaną klasę.
* **Kawa**: Javowy `null` jest w Scheme wartością prawdziwą (tylko `#f` oznacza fałsz); sprawdzanie należy zapisywać jako `(eq? x #!null)`; `obj:field` odczytuje pole, a `(obj:method)` wywołuje metodę.
* **Kontekstowy class loader wątku**: Wątki serwera (główny wątek, wątki regionalne, netty, globalny harmonogram) mają kontekstowy class loader, który nie widzi klas pluginu, podczas gdy środowiska uruchomieniowe Kawy i Clojure używają go do wyszukiwania klas po nazwie. Dlatego każde wywołanie tych środowisk z wątków serwera jest opakowane w `kernel.PluginContext` (czyszczenie cache w Kawie, ława przysięgłych, wszystkie wywołania skryptów, wyszukiwanie oparcia w Gosu/Fantomie, komendy Groovy). Z tego powodu główny wątek w `smokeTest` ustawia kontekstowy class loader na platform loader, symulując wątek serwera.
* **Prolog**: tuProlog domyślnie nie ładuje DCG; należy wywołać `loadLibrary(new DCGLibrary())`, aby uzyskać dostęp do `-->` oraz `phrase/2`.
* **Jython**: `PyString` to ciąg bajtów Pythona 2; gdy w ścieżce występują znaki spoza ASCII, należy użyć `Py.newStringOrUnicode`. Z tego powodu `smokeTest` jest uruchamiany ze ścieżki zawierającej znaki nie-ASCII, takiej jak `build/smoke/插件 plugins/`.
* **Common Lisp (ABCL)**: Podczas inicjalizacji próbuje użyć refleksji, aby otworzyć fabrykę wątków wirtualnych JDK; bez flagi `--add-opens java.base/java.lang` wypisuje jedną linię do `System.err` (Paper z tego powodu ostrzega: „plugin użył System.err”). Nieszkodliwe; `StderrFilter` przechwytuje wyłącznie tę linię podczas inicjalizacji ABCL.
* **Ława przysięgłych**: `ceil(2.5 - 3)` daje `-0.0`; funkcja `max` w Pythonie / Kotlinie / Scheme zachowuje tę wartość bez zmian. Przed podliczeniem głosów należy ją znormalizować do `0.0`, w przeciwnym razie upadek z 2–3 bloków fałszywie zgłaszałby „rozbieżność zdań”.
* **Gosu**: Zaraz po zainicjowaniu runtime otwiera pakiet `java.base` dla refleksji, co maskuje ewentualne późniejsze problemy z refleksją w innych bibliotekach. Dlatego `smokeTest` postępuje zgodnie z kolejnością `onEnable` pluginu: najpierw ładuje warstwę skryptową, a dopiero potem dotyka Gosu. Polecenie `./gradlew smokeTest -PsmokeJava=25` pozwala na uruchomienie testu z wersją JDK używaną na serwerze.
* **JRuby**: `sort_by` nie jest stabilne; aby zachować pierwotną kolejność, należy włączyć indeks do klucza sortowania.
* **Scala 3**: Po usunięciu plików źródłowych kompilacja przyrostowa może pozostawić stare pliki `.tasty`, które trafią do pamięci podręcznej budowania. W przypadku pojawienia się niepożądanych klas w jarze, uruchom `./gradlew clean build --rerun-tasks`.

## Zweryfikowane / Niezweryfikowane

Zweryfikowane (Windows 11, JDK 21, Gradle 9.7.1):

* Wszystkie 12 języków kompilowanych kompiluje się pomyślnie, wszystkie 8 warstw skryptowych ładuje się poprawnie, `./gradlew clean build --rerun-tasks` przechodzi pomyślnie.
* `checkGeometry`: 10 właściwości QuickCheck zaliczonych, w tym round-trip, spójność blok/punkt, spójność obrotu widoku i obrotu wektorów, `zBase` jako dzielenie całkowite zaokrąglane w dół.
* `smokeTest` (łącznie 123 134 sprawdzenia, w izolowanym class loaderze, uruchomione ze ścieżki zawierającej chińskie znaki w celu symulacji ładowania pluginu; przetestowane zarówno na JDK 21, jak i 25):
  * Wyniki reguł Flixa, konfiguracji Clojure, oświetlenia Haxe i wyszukiwania Fantoma są w pełni zgodne punkt po punkcie z oryginalną implementacją w Kotlinie;
  * Wyniki 8 skryptów oraz Kawy są punkt po punkcie zgodne z oryginalnym kodem, który zastępują; ława przysięgłych jednogłośnie oddaje 9 na 9 głosów bez żadnych ostrzeżeń; warstwy skryptowe nie wypisują niczego do `System.err` podczas ładowania;
  * Warstwy Scali/Gosu/Groovy/Xtenda/Kotlina pomyślnie łączą się (link), a środowiska uruchomieniowe Gosu/Groovy/Scali poprawnie się inicjalizują.

**Niezweryfikowane**:

* Nie testowano na prawdziwym serwerze Paper / Folia ani nie dołączano klientem do gry (wymagałoby to zaakceptowania EULA Minecrafta i zainstalowania PacketEvents). Ścieżki zależne od serwera, takie jak przepisywanie pakietów, wysyłanie chunków i re-centrowanie, są objęte jedynie kontrolą kompilacji oraz gwarancją „przeniesienia linijka po linijce z oryginału”. Przed wdrożeniem na serwer produkcyjny należy przeprowadzić weryfikację na serwerze testowym.
* Plik jar waży około 231 MB (oryginał: 4,9 MB): kompilator Kotlina, JRuby, Jython, ABCL, plus środowiska uruchomieniowe Gosu, Groovy, Scali, Clojure, Frege, Kawy. Uruchamianie pluginu trwa o 4–5 sekund dłużej ze względu na ładowanie warstw skryptowych. Runtimy te zostały spakowane w postaci nienaruszonej, bez relokacji (relocate): jeśli na tym samym serwerze inny plugin zawiera inne wersje Kotlina/Scali/Groovy, mogą wystąpić konflikty.

## Struktura katalogów

```
src/api/java        Warstwa 0: Kontrakty warstwy czystych obliczeń (typy JDK)
src/kernel/java     Warstwa 2: Stan współdzielony i interfejsy usług (Bukkit + PacketEvents)
src/main/<język>    Kody źródłowe poszczególnych języków (warstwy skryptowe w src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog})
src/main/resources  plugin.yml, config.yml
buildSrc            Zadania kompilacji Gradle dla poszczególnych języków
build.gradle.kts    Podział na warstwy i zależności
```
