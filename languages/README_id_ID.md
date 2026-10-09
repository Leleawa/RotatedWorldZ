# RotatedWorldZ

Versi 20 bahasa dari RotatedWorld (plugin Paper/Folia yang memutar seluruh dunia 90° di sekitar sumbu X bagi pemain menggunakan PacketEvents):
**Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe** dikompilasi ke dalamnya,
ditambah 8 lapisan skrip yang dimuat saat runtime: **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog**, semuanya dikemas menjadi **satu jar tunggal**.


## Siapa Bertanggung Jawab Atas Apa

| Lapisan | Bahasa | Tanggung Jawab | Mengapa Bahasa Ini |
|---|---|---|---|
| 0 api | Java | Kontrak yang harus diimplementasikan oleh lapisan komputasi murni (hanya tipe JDK) | Faktor persekutuan terkecil yang dipahami oleh semua kompilator |
| 1 Komputasi murni | **Frege** | Aljabar koordinat: satu-satunya definisi rotasi kanonikal, ditambah 10 properti QuickCheck | Fungsi murni + bawaan QuickCheck; dikompilasi menjadi metode `static int/float/double` tanpa boxing |
| | **Flix** | Aturan penamaan blok (varian tegak ↔ dinding, tipe block entity); reklamasi cache | Tabel aturan + sistem efek menjamin kemurnian; "chunk mana yang masih dibutuhkan" adalah sebuah aturan **Datalog** |
| | **Clojure** | Penguraian konfigurasi: item konfigurasi ditulis sebagai data skema | Berbasis data (data-driven): menambah opsi konfigurasi = menambah satu baris data |
| | **Haxe** | Rotasi array nibble pencahayaan | Hanya menggunakan `haxe.io.Bytes`, kode yang sama dapat dikompilasi ke JS/C++ (misalnya penampil pratinjau luring) |
| | **Fantom** | Strategi pencarian pijakan yang aman | Algoritma murni, mengamati dunia melalui antarmuka `BlockProbe`, tanpa menyentuh Bukkit |
| 2 kernel | Java | Status pemain `PlayerState`, `SerialExecutor`, antarmuka `Host` / `BlockRotator` | Status bersama konkuren dengan volatile / variabel atomik, paling langsung ditulis dalam Java |
| 3 Engine | **Scala 3** | Cache chunk `ChunkStore` + engine viewport `ViewManager` (floating origin, prefetch, pengiriman chunk) | Inti performa, perulangan array intensif |
| | **Gosu** | `WorldProbe`: pemeriksaan blok / hitbox Bukkit; enhancement `Material` | Enhancement menambahkan properti ke tipe Bukkit (`type.Hazard`) |
| | **Groovy** | Orkestrasi alur perintah `/rotate` | Jalur dingin (cold path) |
| | **Kawa Scheme** | Pembersihan cache: mengumpulkan rentang viewport setiap dunia, menyerahkannya ke Datalog milik Flix untuk menentukan mana yang disimpan (sebelumnya di Scala `ViewManager`) | Dikompilasi lebih awal (AOT), pemrosesan daftar |
| | **Xtend** | `BlockRotation`: aturan rotasi BlockData | Smart casting otomatis `instanceof` + sintaks properti, aturan terbaca persis seperti aturan itu sendiri |
| 4 Plugin | **Kotlin** | Penulisan ulang paket (jalur panas netty), event Bukkit, facade vektor PacketEvents | Kode sumber asli; penulisan PacketEvents paling mulus di Kotlin |
| | Java | Titik masuk plugin (mengimplementasikan `Host`), jembatan startup Flix/Fantom/Clojure, loader lapisan skrip | Titik masuk dan perakitan |
| 5 Skrip | **Kotlin Script** | Keputusan relokasi floating origin (sebelumnya di Scala `ViewManager`) | Dikompilasi ke bytecode saat runtime, pemanggilan per tick tidak lambat |
| | **JRuby** | Urutan pengiriman kolom chunk (sebelumnya di Scala `ViewManager`) | Rantai pipa `product.each_with_index.sort_by` |
| | **JavaScript** | Pelonggaran jeda lag "bergerak terlalu cepat" (sebelumnya di listener Kotlin) | Interpretasi Rhino, event sangat jarang |
| | **Jython** | Knockback (sebelumnya di listener Kotlin) | Dijalankan sekali setiap kali terkena serangan |
| | **Lua** | Kerusakan jatuh (fall damage) (sebelumnya di listener Kotlin) | Ketua juri |
| | **BeanShell** | Baris teks untuk `/rotate status` (sebelumnya di Groovy) | Skrip dengan sintaks Java |
| | **Common Lisp** | Pemeriksaan sisi server mana yang dilewati untuk pemain terotasi (gerakan gagal, tendangan terbang) (sebelumnya di listener Kotlin) | Tabel keputusan `cond` |
| | **Prolog** | Tata bahasa argumen `/rotate`, ditulis sebagai DCG (sebelumnya di Groovy) | Tata bahasa memang seharusnya menggunakan DCG |
| | **Kedelapan skrip + Kawa** | **Juri kerusakan jatuh**: sembilan bahasa masing-masing menghitung secara independen, suara mayoritas menang, seri mengikuti Lua, catat peringatan jika ada ketidaksepakatan | Hiburan |

**Lapisan 1 pada waktu build sama sekali tidak dapat melihat Bukkit maupun PacketEvents** (hanya menerima `api`), dan kelima bahasa tersebut juga tidak dapat melihat satu sama lain. Pemanggilan ke bawah adalah pemanggilan JVM biasa; pemanggilan ke atas hanya dapat melalui antarmuka: Flix dan Fantom menyerahkan implementasi ke `api.Services`, kelas utama plugin menyediakan layanan ke Lapisan 3 melalui `kernel.Host`.

## Lapisan Skrip

Skrip tidak dikompilasi ke dalam jar, melainkan dimuat oleh interpreter tersemat saat plugin dimulai (`script.ScriptLayers`, 8 interpreter dimulai secara paralel, sekitar 4–5 detik):

* Kode sumber berada di `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*`, saat build dikemas ke dalam `scripts/` di dalam jar.
* Jika `plugins/RotatedWorldZ/scripts/<direktori>/rules.*` ada, file tersebut menimpa versi di dalam jar: **mengubah aturan tidak memerlukan build ulang, cukup restart server**.
* Antarmuka yang diimplementasikan skrip berada di `api.ScriptedRules`. Ekspresi terakhir dari Kotlin Script harus berupa objek yang mengimplementasikan `Recenter` dan `FallJuror` sekaligus, dan tidak boleh berupa objek anonim; skrip lainnya hanya mendefinisikan fungsi global (Prolog berupa predikat, parameter terakhir adalah hasil), yang dibungkus menjadi antarmuka oleh Java.
* Interpreter tersemat tidak menjamin keamanan thread (thread-safe), pemanggilan setiap skrip bersifat serial (`synchronized`). Oleh karena itu, hanya Kotlin Script (eksekusi terkompilasi) yang berada pada jalur panas setiap tick, sisanya berada pada jalur dingin.
* Pustaka bawaan Jython seperti jnr/jffi bentrok versi dengan JRuby, sehingga saat build, Jython dikemas terpisah terlebih dahulu dan paket-paket ini direlokasi (tugas `jythonIsolated`, mengikuti pendekatan templat hulu).

Beberapa area yang memastikan konsistensi lintas-bahasa:

* Pemetaan sisi blok (UP→NORTH……) tidak lagi ditulis manual; Xtend (Bukkit `BlockFace`) dan Kotlin (PacketEvents `BlockFace`) keduanya diturunkan dari rotasi yang sama persis di Frege.
* Pemetaan koordinat dalam section `Geometry.localIndex` (Frege) dan rotasi pencahayaan Haxe menggunakan pemetaan yang sama persis, diverifikasi sel demi sel dalam `smokeTest`.

## Build

```bash
./gradlew build          # Hasil: build/libs/RotatedWorldZ-1.0.0.jar; mencakup checkGeometry dan smokeTest
./gradlew checkGeometry  # Hanya menjalankan pengujian properti QuickCheck Frege
./gradlew smokeTest      # Tanpa server: memulai lapisan komputasi murni dari jar jadi dan membandingkannya dengan perilaku asli Kotlin, memeriksa tautan lapisan lainnya
```

Saat runtime, server memerlukan PacketEvents (`depend: [packetevents]`).

Build pertama kali akan mengunduh Flix, Haxe, Fantom ke `.tools/` (sekitar 60 MB), Haxe juga membutuhkan JDK 8 (hanya menggunakan `rt.jar`-nya, jika Gradle toolchain tidak menemukannya, akan diunduh otomatis melalui foojay). Sisanya semua menggunakan JDK 21.

## Catatan Khusus Tiap Bahasa

* **Flix**: Hanya dapat mengekspor satu fungsi titik masuk (entry point), yang dihasilkan di kelas `Main` pada default package (`FlixBridge` memanggilnya melalui refleksi). Flix juga menghasilkan sekitar 500 kelas dan paket di direktori root (`Array`, `List`……), yang dapat menutupi `Array`/`List` milik Scala/Kotlin sendiri, sehingga output Flix **tidak berada di classpath kompilasi mana pun**, hanya dimasukkan ke runtime dan jar. Guard Datalog menangkap paling banyak 5 variabel. Antarmuka Java dan modul Flix tidak boleh memiliki nama yang sama.
* **Frege**: Parameter secara default bersifat malas (lazy, dalam tanda tangan Java berupa `Lazy<Float>`), untuk memaksakan parameter tipe primitif yang ketat (strict), tambahkan `!` pada pola.
* **Fantom**: Mengimplementasikan antarmuka Java dengan metode default akan menyebabkan crash, sehingga antarmuka di `api` tidak memiliki metode default. `long`/`double`/`boolean` Java langsung dipetakan ke `Int`/`Float`/`Bool` Fantom, semua tipe referensi dapat bernilai null (nullable). Literal floating-point harus ditulis `0.5f` (`0.5` adalah Decimal).
* **Gosu**: `block` adalah kata kunci (lambda milik Gosu). gosuc segera menyelesaikan tipe anotasi, sehingga classpath kompilasi harus menyertakan dependensi Paper API yaitu `org.jetbrains:annotations` dan `jspecify`.
* **Xtend**: `Keyed` milik Bukkit memiliki `key()` dan `getKey()` sekaligus, sehingga `b.key` bermakna ambigu; harus ditulis `b.getKey`. Ketika nama field dan metode sama, nama di dalam tubuh metode merujuk ke metode.
* **Haxe**: Target `--jvm`; class loader eksternal Java hanya mengenali `rt.jar` milik JDK 8; `haxelib` pada paket resmi Windows bergantung pada Neko, sehingga stub diletakkan di PATH saat proses build.
* **Clojure**: Mengandalkan Thread Context ClassLoader untuk menemukan namespace, `ClojureBridge` sementara mengarahkannya ke class loader plugin selama inisialisasi dan pemanggilan.
* **Kotlin Script**: Hasil skrip tidak boleh berupa objek anonim (`object : X {}` melaporkan kesalahan "anonymous type"), harus mendeklarasikan kelas bernama terlebih dahulu.
* **Kawa**: Nilai `null` Java di Scheme dianggap bernilai kebenaran / truthy (hanya `#f` yang bernilai salah), pemeriksaan harus ditulis `(eq? x #!null)`; `obj:field` membaca field, `(obj:method)` memanggil metode.
* **Thread Context ClassLoader**: Context class loader dari thread server (thread utama, thread region, netty, scheduler global) tidak dapat melihat kelas plugin, padahal runtime Kawa dan Clojure mencarinya berdasarkan nama melalui loader tersebut. Oleh karena itu, setiap titik masuk dari thread server ke runtime ini dibungkus dengan `kernel.PluginContext` (pembersihan cache Kawa, juri, semua pemanggilan skrip, pencarian pijakan Gosu/Fantom, perintah Groovy). Thread utama `smokeTest` sengaja menyetel context class loader-nya ke platform loader untuk mensimulasikan thread server.
* **Prolog**: tuProlog secara default tidak memuat DCG, harus memanggil `loadLibrary(new DCGLibrary())` agar `-->` dan `phrase/2` tersedia.
* **Jython**: `PyString` adalah byte string Python 2; jika path berisi karakter bahasa Tionghoa / non-ASCII harus menggunakan `Py.newStringOrUnicode`. Oleh sebab itu, `smokeTest` dijalankan dari path non-ASCII seperti `build/smoke/插件 plugins/`.
* **Common Lisp (ABCL)**: Saat inisialisasi mencoba menggunakan refleksi untuk membuka factory thread virtual JDK; tanpa `--add-opens java.base/java.lang`, satu baris akan dicetak ke `System.err` (menyebabkan Paper memperingatkan bahwa "plugin menggunakan System.err"). Ini tidak berbahaya, `StderrFilter` mencegat baris tunggal ini selama inisialisasi ABCL.
* **Juri**: `ceil(2.5 - 3)` menghasilkan `-0.0`, fungsi `max` pada Python / Kotlin / Scheme mempertahankan nilai ini apa adanya; sebelum penghitungan suara harus dinormalisasi menjadi `0.0`, jika tidak, jatuh 2–3 blok akan salah memicu peringatan "perbedaan pendapat".
* **Gosu**: Begitu runtime diinisialisasi, runtime akan membuka `java.base` terhadap refleksi, setelah itu masalah refleksi pustaka lain tidak dapat terdeteksi. Maka dari itu, `smokeTest` mengikuti urutan `onEnable` plugin: memuat lapisan skrip terlebih dahulu sebelum menyentuh Gosu. `./gradlew smokeTest -PsmokeJava=25` dapat digunakan untuk menjalankan pengujian dengan JDK yang dipakai di server.
* **JRuby**: `sort_by` tidak stabil (unstable); untuk menjaga urutan asli, indeks harus dimasukkan ke dalam kunci pengurutan.
* **Scala 3**: Setelah menghapus file sumber, kompilasi inkremental dapat menyisakan file `.tasty` lama yang masuk ke build cache. Jika menemukan kelas yang tidak diinginkan dalam jar, jalankan `./gradlew clean build --rerun-tasks` sekali.

## Terverifikasi / Belum Terverifikasi

Terverifikasi (Windows 11, JDK 21, Gradle 9.7.1):

* Seluruh 12 bahasa terkompilasi berhasil dikompilasi, seluruh 8 lapisan skrip berhasil dimuat, `./gradlew clean build --rerun-tasks` lulus.
* `checkGeometry`: 10 properti QuickCheck lulus, termasuk roundtrip, konsistensi kotak/titik, rotasi sudut pandang sesuai dengan rotasi vektor, `zBase` adalah pembagian pembulatan ke bawah (floor division).
* `smokeTest` (total 123.134 pemeriksaan, dijalankan dalam class loader terisolasi, dari path yang memuat karakter bahasa Tionghoa untuk mensimulasikan pemuatan plugin; telah diuji pada JDK 21 dan 25):
  * Hasil aturan Flix, konfigurasi Clojure, pencahayaan Haxe, pencarian Fantom cocok butir demi butir dengan implementasi asli Kotlin;
  * Hasil 8 skrip dan Kawa cocok butir demi butir dengan kode asli yang digantikannya, 9 suara juri sepenuhnya sepakat tanpa mencatat peringatan apa pun; pemuatan lapisan skrip tidak mencetak apa pun ke `System.err`;
  * Lapisan Scala/Gosu/Groovy/Xtend/Kotlin dapat ditautkan dengan baik, runtime Gosu/Groovy/Scala dapat diinisialisasi dengan normal.

**Belum terverifikasi**:

* Belum dijalankan pada server Paper / Folia sungguhan, dan belum ada klien yang masuk ke server (hal tersebut membutuhkan persetujuan EULA Minecraft dan instalasi PacketEvents). Jalur yang bergantung pada server seperti penulisan ulang paket, pengiriman chunk, dan relokasi hanya dijamin oleh pemeriksaan kompilasi dan "porting baris demi baris dari versi asli". Silakan uji di server pengetesan terlebih dahulu sebelum diterapkan di server produksi.
* Ukuran jar sekitar 231 MB (asli: 4,9 MB): berisi kompilator Kotlin, JRuby, Jython, ABCL, ditambah runtime Gosu, Groovy, Scala, Clojure, Frege, Kawa. Startup plugin membutuhkan tambahan 4–5 detik untuk memuat lapisan skrip. Runtime ini dikemas apa adanya tanpa relokasi: jika server yang sama memiliki plugin lain yang membawa versi Kotlin/Scala/Groovy berbeda, konflik dapat terjadi.

## Struktur Direktori

```
src/api/java        Lapisan 0: Kontrak lapisan komputasi murni (tipe JDK)
src/kernel/java     Lapisan 2: Status bersama dan antarmuka layanan (Bukkit + PacketEvents)
src/main/<bahasa>   Kode sumber tiap bahasa (lapisan skrip di src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog})
src/main/resources  plugin.yml, config.yml
buildSrc            Tugas kompilasi Gradle untuk tiap bahasa
build.gradle.kts    Pelapisan dan dependensi
```
