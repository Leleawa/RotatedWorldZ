# RotatedWorldZ

RotatedWorld'ün (oyuncular için PacketEvents kullanarak tüm dünyayı X ekseni etrafında 90° döndüren bir Paper/Folia eklentisi) 20 dilli sürümü:
**Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe** birlikte derlenmiş,
artı çalışma zamanında yüklenen 8 betik katmanı: **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog**, hepsi **tek bir jar** dosyasında paketlenmiştir.


## Kim Neyden Sorumlu?

| Katman | Dil | Sorumluluk | Neden bu dil? |
|---|---|---|---|
| 0 api | Java | Saf hesaplama katmanının uygulayacağı sözleşmeler (yalnızca JDK türleri) | Tüm derleyicilerin anlayabildiği en küçük ortak payda |
| 1 saf hesaplama | **Frege** | Koordinat cebiri: döndürmenin kurallı tanımı, artı 10 QuickCheck özelliği | Saf fonksiyonlar + yerleşik QuickCheck; kutulama (boxing) olmaksızın `static int/float/double` metodlarına derlenir |
| | **Flix** | Blok adlandırma kuralları (dikey/ayaklı ↔ duvar, blok entity türleri); önbellek temizleme / geri kazanımı | Kural tabloları + efekt sistemi saflığı garanti eder; "hangi chunk'lara hâlâ ihtiyaç var" bir **Datalog** kuralıdır |
| | **Clojure** | Yapılandırma ayrıştırma: yapılandırma seçenekleri bir şema verisi olarak yazılmıştır | Veri odaklı: yapılandırma seçeneği eklemek = tek bir veri satırı eklemek |
| | **Haxe** | Işık nibble dizisinin döndürülmesi | Yalnızca `haxe.io.Bytes` kullanır, aynı kod JS/C++'a da derlenebilir (ör. çevrimdışı önizleyici) |
| | **Fantom** | Güvenli basamak arama stratejisi | Saf algoritma, dünyaya `BlockProbe` arayüzü üzerinden bakar, Bukkit'e dokunmaz |
| 2 kernel | Java | Oyuncu durumu `PlayerState`, `SerialExecutor`, `Host` / `BlockRotator` arayüzleri | `volatile` / atomik değişkenlerle eşzamanlı paylaşılan durum, Java'da en dolaysız şekilde yazılır |
| 3 motor | **Scala 3** | Chunk önbelleği `ChunkStore` + görüş motoru `ViewManager` (yüzen orijin, önceden getirme, chunk gönderme) | Performans çekirdeği, yoğun dizi döngüleri |
| | **Gosu** | `WorldProbe`: Bukkit blok / çarpışma kutusu (bounding box) kontrolleri; `Material` için enhancement | Enhancement'lar Bukkit türlerine özellikler ekler (`type.Hazard`) |
| | **Groovy** | `/rotate` komutunun akış orkestrasyonu | Soğuk yol (cold path) |
| | **Kawa Scheme** | Önbellek temizliği: her dünyanın görüş mesafesini toplar ve hangilerinin saklanacağına karar vermesi için Flix'in Datalog'una iletir (aslen Scala `ViewManager` içindeydi) | Önceden derlenmiş (AOT), liste işleme |
| | **Xtend** | `BlockRotation`: BlockData döndürme kuralları | `instanceof` otomatik tür dönüşümü + özellik (property) sözdizimi; kurallar doğrudan şartnamenin kendisi gibi okunur |
| 4 eklenti | **Kotlin** | Paket yeniden yazımı (Netty sıcak yolu / hot path), Bukkit olayları, PacketEvents vektör cephesi (facade) | Orijinal kod; PacketEvents sözdizimi Kotlin ile en akıcıdır |
| | Java | Eklenti giriş noktası (`Host` uygular), Flix/Fantom/Clojure başlatma köprüleri, betik katmanı yükleyicisi | Giriş noktası ve montaj |
| 5 betikler | **Kotlin Script** | Yüzen orijinin yeniden merkezleme kararı (aslen Scala `ViewManager` içindeydi) | Çalışma zamanında baytkoda derlenir, her tick'te çağrılacak kadar hızlıdır |
| | **JRuby** | Chunk sütunlarının gönderim sırası (aslen Scala `ViewManager` içindeydi) | Tek bir `product.each_with_index.sort_by` zinciri |
| | **JavaScript** | "Çok hızlı hareket edildi" takılma/geri çekme (rubberband) istisnası (aslen Kotlin dinleyicisindeydi) | Rhino ile yorumlanır, nadir olaylar |
| | **Jython** | Geri savurma (knockback) (aslen Kotlin dinleyicisindeydi) | Alınan her darbede bir kez çalışır |
| | **Lua** | Düşme hasarı (aslen Kotlin dinleyicisindeydi) | Baş jüri üyesi |
| | **BeanShell** | `/rotate status` çıktısındaki o tek satır metin (aslen Groovy içindeydi) | Java sözdizimli betik |
| | **Common Lisp** | Döndürülmüş oyuncular için hangi sunucu kontrollerinin atlanacağı (başarısız hareketler, uçma sebebiyle atılma) (aslen Kotlin dinleyicisindeydi) | Bir `cond` karar tablosu |
| | **Prolog** | DCG olarak yazılmış `/rotate` argüman grameri (aslen Groovy içindeydi) | Gramerler DCG ile yazılmalıdır |
| | **Sekizinin hepsi + Kawa** | **Düşme hasarı jürisi**: dokuz dilin her biri bir kez hesaplar, çoğunluk kararı geçerlidir, eşitlikte Lua belirler, anlaşmazlık durumunda uyarı kaydedilir | Eğlence |

**Katman 1, derleme aşamasında Bukkit ve PacketEvents'i görmez** (yalnızca `api` alır) ve beş dil birbirini de görmez. Aşağıya doğru çağrılar normal JVM çağrılarıdır; yukarıya doğru çağrılar ise yalnızca arayüzler üzerinden yapılabilir: Flix ve Fantom uygulamalarını `api.Services`'e devreder, eklenti ana sınıfı ise `kernel.Host` aracılığıyla Katman 3'e hizmet sağlar.

## Betik Katmanı

Betikler derlenerek dahil edilmez, eklenti başlatılırken gömülü yorumlayıcılar tarafından yüklenir (`script.ScriptLayers`, paralel başlatılan 8 yorumlayıcı, yaklaşık 4–5 saniye):

* Kaynak kodlar `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*` dizinindedir ve derleme sırasında jar içindeki `scripts/` altına paketlenir.
* Eğer `plugins/RotatedWorldZ/scripts/<dizin>/rules.*` mevcutsa, jar içindeki sürümün üzerine yazar: **kuralları değiştirmek için projeyi yeniden derlemek gerekmez, sunucuyu yeniden başlatmak yeterlidir**.
* Betiklerin uyguladığı arayüzler `api.ScriptedRules` içindedir. Kotlin Script'in son ifadesi, aynı anda hem `Recenter` hem de `FallJuror` arayüzlerini uygulayan bir nesne olmalı ve anonim nesne olmamalıdır; diğer betikler ise yalnızca global fonksiyonlar tanımlar (Prolog'da yüklemlerdir ve son argüman sonuçtur), bunlar Java tarafından arayüz olarak sarılır.
* Gömülü yorumlayıcıların hiçbiri iş parçacığı güvenliği (thread-safety) garantisi vermez; her betiğin çağrıları sıralıdır (`synchronized`). Bu nedenle yalnızca Kotlin Script (derlenerek çalıştırılır) her tick'teki sıcak yoldadır (hot path), diğerleri ise soğuk yoldadır.
* Jython'un dahili jnr/jffi vb. kütüphaneleri JRuby sürümleriyle çakışır; bu nedenle derleme sırasında Jython önce ayrı olarak paketlenir ve bu paketler yeniden konumlandırılır (`jythonIsolated` görevi, yukarı akış şablon yaklaşımı takip edilerek).

Diller arasında tutarlılığın garanti edildiği bazı noktalar:

* Blok yüzlerinin eşlemesi (UP→NORTH……) artık elle yazılmaz; Xtend (Bukkit `BlockFace`) ve Kotlin (PacketEvents `BlockFace`), Frege'deki aynı döndürme işleminden türetilir.
* section içindeki koordinat eşlemesi `Geometry.localIndex` (Frege) ile Haxe ışık döndürmesi birebir aynı eşlemeyi kullanır; `smokeTest` blok blok karşılaştırır.

## Derleme

```bash
./gradlew build          # Çıktı: build/libs/RotatedWorldZ-1.0.0.jar; checkGeometry ve smokeTest'i içerir
./gradlew checkGeometry  # Yalnızca Frege'nin QuickCheck özelliklerini çalıştırır
./gradlew smokeTest      # Sunucu gerektirmez: tamamlanmış jar'dan saf hesaplama katmanını başlatır ve orijinal Kotlin davranışıyla karşılaştırır, diğer katmanların bağlantılarını denetler
```

Çalışma zamanında sunucuda PacketEvents kurulu olmalıdır (`depend: [packetevents]`).

İlk derlemede Flix, Haxe ve Fantom `.tools/` dizinine indirilir (yaklaşık 60 MB); Haxe ayrıca bir JDK 8 gerektirir (yalnızca `rt.jar` dosyasını kullanır; Gradle toolchain bulamazsa foojay üzerinden otomatik indirilir). Geri kalan her şey JDK 21 kullanır.

## Dillerin Püf Noktaları ve Tuzakları

* **Flix**: Yalnızca tek bir giriş fonksiyonu dışa aktarabilir, varsayılan paketin `Main` sınıfında üretilir (`FlixBridge` bunu yansıma/reflection ile çağırır). Ayrıca kök dizinde yaklaşık 500 sınıf ve paket (`Array`, `List`……) üretir ve bunlar Scala/Kotlin'in kendi `Array`/`List` sınıflarını gölgeler; bu nedenle Flix çıktısı **hiçbir derleme classpath'inde yer almaz**, yalnızca çalışma zamanına ve jar'a dahil edilir. Datalog korumaları (guard) en fazla 5 değişken yakalayabilir. Java arayüzleri ile Flix modülleri aynı ada sahip olamaz.
* **Frege**: Parametreler varsayılan olarak tembeldir (lazy) (Java imzasında `Lazy<Float>` olarak görünür); katı ilkel tür parametreleri için desene `!` ekleyin.
* **Fantom**: `default` metodları olan Java arayüzlerini uygulamak çökmeye neden olur; bu nedenle `api` içindeki arayüzlerde `default` metod bulunmaz. Java'nın `long`/`double`/`boolean` türleri doğrudan Fantom'un `Int`/`Float`/`Bool` türlerine karşılık gelir; referans türlerinin tümü null atanabilirdir (nullable). Kayan noktalı sabitler `0.5f` olarak yazılmalıdır (`0.5` Decimal'dir).
* **Gosu**: `block` bir anahtar kelimedir (Gosu'nun lambda'sı). gosuc ek açıklama (annotation) türlerini anında çözümler; bu nedenle derleme classpath'inde Paper API bağımlılıkları olan `org.jetbrains:annotations` ve `jspecify` bulunmalıdır.
* **Xtend**: Bukkit'in `Keyed` arayüzü hem `key()` hem de `getKey()` metodlarına sahiptir; `b.key` belirsizdir, bu yüzden `b.getKey` yazılmalıdır. Bir alan (field) ile bir metod aynı ada sahip olduğunda, metod gövdesindeki ad metoda işaret eder.
* **Haxe**: `--jvm` hedefi; Java harici sınıf yükleyicisi yalnızca JDK 8'in `rt.jar` dosyasını tanır; `haxelib`'in resmi Windows paketi Neko'ya bağımlıdır, bu nedenle derleme sırasında PATH'e bir saplama (stub) yerleştirilir.
* **Clojure**: Ad alanlarını (namespace) bulmak için iş parçacığı bağlam sınıf yükleyicisine (TCCL) güvenir; `ClojureBridge`, başlatma ve çağrılar sırasında geçici olarak eklentinin sınıf yükleyicisine işaret eder.
* **Kotlin Script**: Betik sonucu anonim bir nesne olamaz (`object : X {}` "anonymous type" hatası verir); önceden adlandırılmış bir sınıf bildirilmelidir.
* **Kawa**: Java'nın `null` değeri Scheme'de doğru (truthy) değerdir (yalnızca `#f` yanlıştır); kontroller `(eq? x #!null)` olarak yazılmalıdır; `obj:field` alanları okur, `(obj:method)` metodları çağırır.
* **İş parçacığı bağlam sınıf yükleyicisi**: Sunucu iş parçacıklarının (ana iş parçacığı, bölge iş parçacıkları, netty, global zamanlayıcı) bağlam sınıf yükleyicisi eklentinin sınıflarını göremez; oysa Kawa ve Clojure çalışma zamanları sınıfları ada göre ararken tam olarak bunu kullanır. Bu nedenle sunucu iş parçacıklarından bu çalışma zamanlarına girilen her yer `kernel.PluginContext` ile sarılır (Kawa önbellek temizliği, jüri, tüm betik çağrıları, Gosu/Fantom basamak araması, Groovy komutları). Bu nedenle `smokeTest` ana iş parçacığı, sunucu iş parçacıklarını simüle etmek için bağlam sınıf yükleyicisini platform yükleyicisi olarak ayarlar.
* **Prolog**: tuProlog varsayılan olarak DCG yüklemez; `-->` ve `phrase/2` elde etmek için `loadLibrary(new DCGLibrary())` çağrılmalıdır.
* **Jython**: `PyString`, Python 2'nin bayt dizisidir; yolda ASCII dışı karakterler olduğunda `Py.newStringOrUnicode` kullanılmalıdır. Bu nedenle `smokeTest`, `build/smoke/插件 plugins/` gibi ASCII dışı bir yoldan çalıştırılır.
* **Common Lisp (ABCL)**: Başlatma sırasında reflection kullanarak JDK'nin sanal iş parçacığı fabrikasını açmaya çalışır; `--add-opens java.base/java.lang` olmadan `System.err`'e tek bir satır yazdırır (Paper bu yüzden "eklenti System.err kullandı" uyarısı verir). Zararsızdır; `StderrFilter` ABCL başlatması sırasında yalnızca bu satırı engeller.
* **Jüri**: `ceil(2.5 - 3)` ifadesi `-0.0` üretir; Python / Kotlin / Scheme'in `max` fonksiyonu bunu olduğu gibi korur. Oylar sayılmadan önce `0.0` olarak normalleştirilmelidir, aksi takdirde 2–3 blokluk düşüşlerde yanlışlıkla "görüş ayrılığı" bildirilir.
* **Gosu**: Çalışma zamanı başlatılır başlatılmaz `java.base` paketini reflection'a açar, bu da sonrasında diğer kütüphanelerin reflection sorunlarının fark edilmesini engeller. Bu yüzden `smokeTest`, eklentinin `onEnable` sırasını takip eder: önce betik katmanını yükler, ardından Gosu'ya dokunur. `./gradlew smokeTest -PsmokeJava=25` ile sunucuda kullanılan JDK sürümüyle çalıştırılabilir.
* **JRuby**: `sort_by` kararlı değildir; orijinal sırayı korumak için dizin (index) sıralama anahtarına dahil edilmelidir.
* **Scala 3**: Kaynak dosyalar silindikten sonra artımlı derleme eski `.tasty` dosyalarını geride bırakabilir ve bunlar derleme önbelleğine girebilir. Jar içinde olmaması gereken sınıflarla karşılaşıldığında `./gradlew clean build --rerun-tasks` çalıştırın.

## Doğrulananlar / Doğrulanmayanlar

Doğrulananlar (Windows 11, JDK 21, Gradle 9.7.1):

* 12 derlenen dilin tümü derlendi, 8 betik katmanının tümü yüklendi, `./gradlew clean build --rerun-tasks` başarıyla geçti.
* `checkGeometry`: Gidiş-dönüş (round-trip), blok/nokta tutarlılığı, bakış açısı döndürme ile vektör döndürme tutarlılığı, `zBase`'in tabana yuvarlanan bölme olması dahil 10 QuickCheck özelliği başarıyla geçti.
* `smokeTest` (Eklenti yüklemesini simüle etmek için yalıtılmış bir sınıf yükleyicide ve Çince karakterler içeren bir yoldan çalıştırılan toplam 123.134 denetim; hem JDK 21 hem de 25 ile çalıştırıldı):
  * Flix kuralları, Clojure yapılandırması, Haxe ışıklandırması ve Fantom arama sonuçları, orijinal Kotlin uygulamasıyla madde madde tam olarak uyuşuyor;
  * 8 betik ve Kawa'nın sonuçları, devraldıkları orijinal kodla madde madde tam olarak uyuşuyor; jüri 9 oyun 9'unda da oybirliği sağladı ve hiçbir uyarı kaydedilmedi; betik katmanları yükleme sırasında `System.err`'e hiçbir şey yazdırmadı;
  * Scala/Gosu/Groovy/Xtend/Kotlin katmanları bağlanabiliyor (link), Gosu/Groovy/Scala çalışma zamanları başarıyla başlatılabiliyor.

**Doğrulanmayanlar**:

* Gerçek bir Paper / Folia sunucusunda çalıştırılmadı ve istemci ile oyuna girilmedi (bu, Minecraft EULA'sını kabul etmeyi ve PacketEvents kurmayı gerektirir). Paket yeniden yazımı, chunk gönderimi, yeniden merkezleme gibi sunucuya bağımlı yollar yalnızca derleme denetimleri ve "orijinalinden satır satır taşınma" güvencesine sahiptir. Canlıya almadan önce lütfen bir test sunucusunda doğrulayın.
* Jar boyutu yaklaşık 231 MB'tır (orijinali 4,9 MB): Kotlin derleyicisi, JRuby, Jython, ABCL, artı Gosu, Groovy, Scala, Clojure, Frege, Kawa çalışma zamanları. Eklenti başlangıcı betik katmanlarını yüklemek için fazladan 4–5 saniye sürer. Bu çalışma zamanları yeniden konumlandırılmadan (relocate edilmeden) olduğu gibi eklenmiştir: aynı sunucuda farklı Kotlin/Scala/Groovy sürümleri içeren başka eklentiler varsa çakışmalar yaşanabilir.

## Dizin Yapısı

```
src/api/java        Katman 0: Saf hesaplama katmanının sözleşmeleri (JDK türleri)
src/kernel/java     Katman 2: Paylaşılan durum ve servis arayüzleri (Bukkit + PacketEvents)
src/main/<dil>      Her dilin kaynak kodları (betik katmanları src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog} altında)
src/main/resources  plugin.yml, config.yml
buildSrc            Her dil için Gradle derleme görevleri
build.gradle.kts    Katmanlar ve bağımlılıklar
```
