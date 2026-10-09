# RotatedWorldZ

RotatedWorld（用 PacketEvents 把整個世界繞 X 軸轉 90° 給玩家看的 Paper/Folia 插件）的 20 語言版本：
**Java、Kotlin、Scala 3、Gosu、Groovy、Kawa Scheme、Xtend、Fantom、Flix、Frege、Clojure、Haxe** 編譯進來，
外加 8 個執行階段載入的指令碼層 **Kotlin Script、JRuby、JavaScript、Jython、Lua、BeanShell、Common Lisp、Prolog**，全部打包成**單一 jar 檔**。


## 誰負責什麼

| 層級 | 語言 | 負責項目 | 為什麼選它 |
|---|---|---|---|
| 0 api | Java | 純計算層需實現的契約（僅使用 JDK 類型） | 所有編譯器都能理解的最小公約數 |
| 1 純計算 | **Frege** | 座標代數：旋轉的唯一性定義，外加 10 條 QuickCheck 性質 | 純函數 + 自帶 QuickCheck；編譯後為 `static int/float/double` 方法，無裝箱開銷 |
| | **Flix** | 方塊命名規則（立式 ↔ 牆式、方塊實體類型）；快取回收 | 規則表 + 效果系統保證純粹性；「哪些區塊還有人需要」是一條 **Datalog** 規則 |
| | **Clojure** | 配置解析：配置項目寫成一份 schema 資料 | 資料驅動：新增配置項 = 新增一行資料 |
| | **Haxe** | 光照 nibble 陣列的旋轉 | 僅使用 `haxe.io.Bytes`，同一份程式碼可編譯至 JS/C++（例如離線預覽器） |
| | **Fantom** | 安全落腳點的搜尋策略 | 純演算法，透過 `BlockProbe` 介面觀察世界，完全不接觸 Bukkit |
| 2 kernel | Java | 玩家狀態 `PlayerState`、`SerialExecutor`、`Host` / `BlockRotator` 介面 | 含有 volatile / 原子變數的並行共享狀態，用 Java 撰寫最直接 |
| 3 引擎 | **Scala 3** | 區塊快取 `ChunkStore` + 視野引擎 `ViewManager`（浮動原點、預取、發送區塊） | 效能核心，存在大量陣列迴圈 |
| | **Gosu** | `WorldProbe`：Bukkit 方塊 / 碰撞箱檢查；`Material` 的 enhancement 擴充 | enhancement 可為 Bukkit 類型擴展屬性（`type.Hazard`） |
| | **Groovy** | `/rotate` 指令的流程編排 | 冷路徑 |
| | **Kawa Scheme** | 快取清理：收集每個世界的視野範圍，交由 Flix 的 Datalog 決定保留哪些（原先位於 Scala `ViewManager`） | 提前編譯，串列處理 |
| | **Xtend** | `BlockRotation`：BlockData 的旋轉規則 | `instanceof` 自動轉型 + 屬性語法，規則閱讀起來就像規則本身 |
| 4 外掛 | **Kotlin** | 封包改寫（Netty 熱路徑）、Bukkit 事件、PacketEvents 向量門面 | 原專案程式碼；PacketEvents 的 Kotlin 寫法最順暢 |
| | Java | 外掛進入點（實現 `Host`）、啟動 Flix/Fantom/Clojure 的橋接層、指令碼層載入器 | 進入點與組裝 |
| 5 指令碼 | **Kotlin Script** | 浮動原點的重新定位決策（原先位於 Scala `ViewManager`） | 執行階段編譯為位元組碼，每 tick 呼叫也不慢 |
| | **JRuby** | 區塊柱的發送順序（原先位於 Scala `ViewManager`） | 一條 `product.each_with_index.sort_by` 鏈 |
| | **JavaScript** | 「移動過快」的卡頓放行（原先位於 Kotlin 監聽器） | Rhino 直譯執行，觸發事件極少 |
| | **Jython** | 擊退（原先位於 Kotlin 監聽器） | 每次被打呼叫一次 |
| | **Lua** | 摔落傷害（原先位於 Kotlin 監聽器） | 首席陪審員 |
| | **BeanShell** | `/rotate status` 的那一行文字（原先位於 Groovy） | Java 語法的指令碼 |
| | **Common Lisp** | 哪些伺服端檢查對旋轉玩家放行（失敗的移動、飛行踢出）（原先位於 Kotlin 監聽器） | 一張 `cond` 決策表 |
| | **Prolog** | `/rotate` 的參數語法，撰寫為 DCG（原先位於 Groovy） | 語法分析天生就該用 DCG |
| | **全部八個 + Kawa** | **摔落傷害陪審團**：九種語言各算一遍，少數服從多數，平手時聽 Lua 的，意見不一致時記錄警告 | 娛樂 |

**第 1 層在建置期完全看不到 Bukkit 和 PacketEvents**（僅能存取 `api`），五種語言之間也互相看不到。向下呼叫為標準 JVM 呼叫；向上呼叫只能透過介面：Flix、Fantom 將實作交由 `api.Services`，外掛主類別則透過 `kernel.Host` 為第 3 層提供服務。

## 指令碼層

指令碼並非編譯進來的，而是在外掛啟動時由內嵌直譯器載入（`script.ScriptLayers`，8 個直譯器並行啟動，大約需 4–5 秒）：

* 原始碼位於 `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*`，建置時打包進 jar 的 `scripts/` 目錄下。
* 若 `plugins/RotatedWorldZ/scripts/<目錄>/rules.*` 存在，則會覆蓋 jar 內的檔案：**修改規則無需重新建置，重啟伺服器即可生效**。
* 指令碼實作的介面定義於 `api.ScriptedRules`。Kotlin Script 的最後一個運算式必須是同時實作 `Recenter` 與 `FallJuror` 的物件，且不能為匿名物件；其餘指令碼僅定義全域函數（Prolog 則為述詞，最後一個參數為結果），由 Java 包裝為介面。
* 內嵌直譯器均不保證執行緒安全，每個指令碼的呼叫皆為循序串行（`synchronized`）。因此僅有 Kotlin Script（編譯執行）位於每 tick 的熱路徑上，其餘皆在冷路徑。
* Jython 內建的 jnr/jffi 等程式庫與 JRuby 的版本衝突，因此建置時先將 Jython 單獨打包並對這些套件進行重定位（`jythonIsolated` 任務，沿用上游範本的做法）。

跨語言保證一致的幾個關鍵點：

* 方塊朝向的對應（UP→NORTH……）不再透過手寫對應表，Xtend（Bukkit `BlockFace`）與 Kotlin（PacketEvents `BlockFace`）皆由 Frege 的同一個旋轉定義推導而得。
* section 內的座標對應 `Geometry.localIndex`（Frege）與 Haxe 光照旋轉使用同一套對應關係，於 `smokeTest` 中逐格比對驗證。

## 建置

```bash
./gradlew build          # 產物：build/libs/RotatedWorldZ-1.0.0.jar；包含 checkGeometry 與 smokeTest
./gradlew checkGeometry  # 僅執行 Frege 的 QuickCheck 性質測試
./gradlew smokeTest      # 無需伺服器：直接從成品 jar 啟動純計算層並與原 Kotlin 行為比對，並檢查其餘各層的鏈結
```

執行階段伺服器需安裝 PacketEvents（`depend: [packetevents]`）。

首次建置會將 Flix、Haxe、Fantom 下載至 `.tools/`（約 60 MB），Haxe 還額外需要 JDK 8（僅使用其 `rt.jar`，若 Gradle toolchain 找不到將透過 foojay 自動下載）。其餘部分全數採用 JDK 21。

## 各語言注意事項與踩坑記錄

* **Flix**：僅能匯出單一進入點函數，產生於預設套件的 `Main` 類別中（`FlixBridge` 透過反射呼叫）。此外，它會在根目錄產生約 500 個類別與套件（`Array`、`List`……），會遮蔽 Scala/Kotlin 自身的 `Array`/`List`，因此 Flix 的編譯輸出**不置於任何編譯 classpath 上**，僅打包進執行階段與 jar。Datalog 的 guard 最多僅能擷取 5 個變數。Java 介面與 Flix 模組名稱不可相同。
* **Frege**：參數預設為惰性求值（Java 方法簽名中為 `Lazy<Float>`），若需嚴格求值的原始型別參數，需在模式上加上 `!`。
* **Fantom**：實作含有 default 方法的 Java 介面會導致當機，因此 `api` 中的介面未包含任何 default 方法。Java 的 `long`/`double`/`boolean` 直接對應至 Fantom 的 `Int`/`Float`/`Bool`，所有參考型別皆為可為空（nullable）。浮點數常值需書寫為 `0.5f`（`0.5` 視為 Decimal）。
* **Gosu**：`block` 為保留關鍵字（Gosu 的 lambda 語法）。gosuc 會立即解析註解類型，因此編譯 classpath 上必須具備 Paper API 所依賴的 `org.jetbrains:annotations` 與 `jspecify`。
* **Xtend**：Bukkit 的 `Keyed` 同時擁有 `key()` 與 `getKey()`，導致 `b.key` 存在歧義，必須明確書寫為 `b.getKey`。當欄位與方法同名時，方法主體內的識別名稱將指向方法。
* **Haxe**：採用 `--jvm` 目標；Java 外部類別載入器僅識別 JDK 8 的 `rt.jar`；官方 Windows 安裝套件中的 `haxelib` 依賴 Neko，因此建置時需在 PATH 中放置一個 stub 樁程式。
* **Clojure**：依賴執行緒上下文類別載入器（TCCL）尋找命名空間，`ClojureBridge` 於初始化與呼叫時會暫時將 TCCL 指向外掛的類別載入器。
* **Kotlin Script**：指令碼的求值結果不可為匿名物件（`object : X {}` 會拋出 "anonymous type" 錯誤），必須先宣告具名類別。
* **Kawa**：Java 的 `null` 在 Scheme 中被視為真值（僅有 `#f` 為偽），null 判定需寫成 `(eq? x #!null)`；存取欄位使用 `obj:field`，呼叫方法使用 `(obj:method)`。
* **執行緒上下文類別載入器（TCCL）**：伺服器執行緒（主執行緒、區域執行緒、Netty、全域排程器）的上下文類別載入器無法看見外掛的類別，而 Kawa 與 Clojure 的執行階段依名稱尋找類別時所使用的正是 TCCL。因此，凡是從伺服器執行緒進入這些執行階段的進入點，均需使用 `kernel.PluginContext` 包裹一層（Kawa 快取清理、陪審團、所有指令碼呼叫、Gosu/Fantom 尋找落腳點、Groovy 指令）。`smokeTest` 的主執行緒亦因此將上下文類別載入器設為 platform 載入器，以精確模擬伺服器執行緒環境。
* **Prolog**：tuProlog 預設不載入 DCG，必須呼叫 `loadLibrary(new DCGLibrary())` 才能使用 `-->` 與 `phrase/2`。
* **Jython**：`PyString` 為 Python 2 的位元組字串，路徑若包含中文等非 ASCII 字元時需使用 `Py.newStringOrUnicode`。`smokeTest` 因而特意從 `build/smoke/插件 plugins/` 這類非 ASCII 路徑下執行以驗證此行為。
* **Common Lisp (ABCL)**：初始化時會嘗試以反射開啟 JDK 的虛擬執行緒工廠，若未加上 `--add-opens java.base/java.lang`，則會向 `System.err` 輸出訊息（導致 Paper 發出「外掛使用了 System.err」的警告）。此訊息並無危害，`StderrFilter` 於 ABCL 初始化期間僅會攔截並過濾此行輸出。
* **陪審團**：`ceil(2.5 - 3)` 計算結果為 `-0.0`，Python / Kotlin / Scheme 的 `max` 函式會原樣保留負零，在計票前必須正規化為 `0.0`，否則在跌落 2～3 格時會誤報「意見不一致」。
* **Gosu**：執行階段一旦初始化，便會使 `java.base` 對反射全面開放，進而掩蓋其他函式庫的反射潛在問題。因此 `smokeTest` 嚴格依照外掛 `onEnable` 的順序，先載入指令碼層，最後才調用 Gosu。亦可使用 `./gradlew smokeTest -PsmokeJava=25` 切換為伺服器實際使用的 JDK 執行測試。
* **JRuby**：`sort_by` 屬於不穩定排序，若需維持原始順序，必須將索引一併放入排序鍵中。
* **Scala 3**：刪除原始碼檔案後，增量編譯可能會殘留舊的 `.tasty` 檔案並進入建置快取中。若發現最終 jar 檔中包含不該出現的類別，請執行一次 `./gradlew clean build --rerun-tasks`。

## 已驗證 / 未驗證

已驗證（Windows 11，JDK 21，Gradle 9.7.1）：

* 12 種編譯語言全部編譯成功、8 個指令碼層全部順利載入，`./gradlew clean build --rerun-tasks` 通過。
* `checkGeometry`：10 條 QuickCheck 性質全數通過，包含往返一致性、網格／點一致性、視角旋轉與向量旋轉一致性、`zBase` 向下取整除法。
* `smokeTest`（共 123,134 項檢查，在隔離的類別載入器中、從包含中文的路徑執行以模擬外掛載入；於 JDK 21 與 25 均通過測試）：
  * Flix 規則、Clojure 配置、Haxe 光照、Fantom 搜尋的結果，與原 Kotlin 實作逐項一致；
  * 8 個指令碼與 Kawa 的結果與其接管的原始程式碼逐項一致，陪審團九票全數一致、未記錄任何警告；指令碼層載入時不向 `System.err` 輸出任何內容；
  * Scala/Gosu/Groovy/Xtend/Kotlin 各層可順利連結，Gosu/Groovy/Scala 的執行階段可正常初始化。

**未驗證**：

* 尚未在真實的 Paper / Folia 伺服器上運作，亦未透過客戶端連線進服（這需要同意 Minecraft EULA 並安裝 PacketEvents）。封包改寫、區塊發送、重新定位等依賴伺服器的路徑，僅有編譯檢查與「自原版逐行移植」作為保證。實際上線前請務必先於測試伺服器驗證。
* jar 檔約 231 MB（原版為 4.9 MB）：包含 Kotlin 編譯器、JRuby、Jython、ABCL，外加 Gosu、Groovy、Scala、Clojure、Frege、Kawa 的執行階段庫。外掛啟動需額外耗費 4–5 秒載入指令碼層。這些執行階段庫均為原樣打包、未進行重定位（relocate）：若伺服器上其他外掛攜帶了不同版本的 Kotlin/Scala/Groovy，可能發生衝突。

## 目錄結構

```
src/api/java        第 0 層：純計算層契約（JDK 類型）
src/kernel/java     第 2 層：共享狀態與服務介面（Bukkit + PacketEvents）
src/main/<語言>      各語言原始碼（指令碼層位於 src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog}）
src/main/resources  plugin.yml、config.yml
buildSrc            各語言的 Gradle 編譯任務
build.gradle.kts    分層與依賴設定
```
