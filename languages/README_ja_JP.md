# RotatedWorldZ

RotatedWorld（PacketEvents を用いてプレイヤー向けにワールド全体を X 軸周りに 90° 回転させて見せる Paper/Folia プラグイン）の 20 言語版：
**Java、Kotlin、Scala 3、Gosu、Groovy、Kawa Scheme、Xtend、Fantom、Flix、Frege、Clojure、Haxe** をコンパイルして組み込み、
さらに実行時に読み込まれる 8 つのスクリプト層 **Kotlin Script、JRuby、JavaScript、Jython、Lua、BeanShell、Common Lisp、Prolog** を加え、すべてを**単一の jar** にパッケージング。


## 各言語の役割分担

| レイヤー | 言語 | 担当 | 採用理由 |
|---|---|---|---|
| 0 api | Java | 純粋計算層が実装すべき規約（JDK 型のみ使用） | すべてのコンパイラが解釈できる最小公約数 |
| 1 純粋計算 | **Frege** | 座標代数：回転の一意な定義、および 10 個の QuickCheck 特性 | 純粋関数＋標準 QuickCheck。static int/float/double メソッドにコンパイルされ、ボクシングなし |
| | **Flix** | ブロック命名規則（自立式 ↔ 壁掛け式、ブロックエンティティ型）、キャッシュ破棄 | ルールテーブル＋エフェクトシステムによる純粋性の保証。「どのチャンクがまだ必要か」を **Datalog** ルールで記述 |
| | **Clojure** | 設定パース：設定項目をスキーマデータとして定義 | データ駆動：設定項目の追加＝データ1行の追加 |
| | **Haxe** | 明るさ（nibble 配列）の回転 | haxe.io.Bytes のみを使用し、同一コードを JS/C++（オフラインプレビューア等）へコンパイル可能 |
| | **Fantom** | 安全な足場の探索戦略 | 純粋なアルゴリズム。BlockProbe インターフェース経由でワールドを参照し、Bukkit に一切触れない |
| 2 kernel | Java | プレイヤー状態 `PlayerState`、`SerialExecutor`、`Host` / `BlockRotator` インターフェース | volatile やアトミック変数を含む並行共有状態を扱うため、Java での実装が最も直接的 |
| 3 エンジン | **Scala 3** | チャンクキャッシュ `ChunkStore` ＋ 描画距離エンジン `ViewManager`（浮動原点、プリフェッチ、チャンク送信） | パフォーマンスの要。大量の配列ループ処理 |
| | **Gosu** | `WorldProbe`：Bukkit ブロック／当たり判定チェック、`Material` の enhancement | enhancement により Bukkit 型へプロパティを追加（`type.Hazard`） |
| | **Groovy** | `/rotate` コマンドのフロー制御 | コールドパス |
| | **Kawa Scheme** | キャッシュクリーンアップ：ワールドごとの描画範囲を収集し、Flix の Datalog に保持判定を委譲（元は Scala の `ViewManager`） | 事前コンパイル、リスト処理 |
| | **Xtend** | `BlockRotation`：BlockData の回転ルール | `instanceof` 自動キャスト＋プロパティ構文により、ルール定義が直感的に読める |
| 4 プラグイン | **Kotlin** | パケット書き換え（Netty ホットパス）、Bukkit イベント、PacketEvents ベクトルファサード | オリジナルコード。PacketEvents の記述が最も自然 |
| | Java | プラグインエントリーポイント（`Host` を実装）、Flix/Fantom/Clojure の起動ブリッジ、スクリプト層ローダー | エントリーポイントと配線 |
| 5 スクリプト | **Kotlin Script** | 浮動原点の再センタリング判定（元は Scala の `ViewManager`） | 実行時にバイトコードへコンパイルされるため、毎 tick の呼び出しでも高速 |
| | **JRuby** | チャンク列の送信順序（元は Scala の `ViewManager`） | 1本の `product.each_with_index.sort_by` チェーン |
| | **JavaScript** | 「移動が速すぎる」による巻き戻しの免除判定（元は Kotlin リスナー） | Rhino によるインタプリタ実行。発生頻度が極めて低い |
| | **Jython** | ノックバック（元は Kotlin リスナー） | 被ダメージごとに1回実行 |
| | **Lua** | 落下ダメージ（元は Kotlin リスナー） | 首席陪審員 |
| | **BeanShell** | `/rotate status` のステータス表示文字列（元は Groovy） | Java 構文のスクリプト |
| | **Common Lisp** | 回転プレイヤーに対して免除するサーバー側チェックの判定（無効な移動、飛行キック）（元は Kotlin リスナー） | 1つの `cond` 決定表 |
| | **Prolog** | `/rotate` の引数文法を DCG として記述（元は Groovy） | 文法解析には DCG が最適 |
| | **全8言語 ＋ Kawa** | **落下ダメージ陪審団**：9言語でそれぞれ計算し多数決で決定。同数の場合は Lua の判断に従い、不一致時は警告を記録 | エンタメ |

**レイヤー 1 はビルド構成上 Bukkit や PacketEvents を一切参照できず**（`api` のみ参照可能）、5 つの言語間も互いに独立しています。下位レイヤーへの呼び出しは通常の JVM 呼び出しであり、上位レイヤーへの呼び出しはインターフェース経由に限定されます。Flix と Fantom は `api.Services` に実装を委ね、プラグインメインクラスは `kernel.Host` を介してレイヤー 3 にサービスを提供します。

## スクリプト層

スクリプトはコンパイルして組み込まれるのではなく、プラグイン起動時に内蔵インタプリタによってロードされます（`script.ScriptLayers`、8 つのインタプリタが並行起動し、約 4〜5 秒）：

* ソースコードは `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*` に配置され、ビルド時に jar 内の `scripts/` 配下に格納されます。
* `plugins/RotatedWorldZ/scripts/<ディレクトリ>/rules.*` が存在する場合、jar 内の同名ファイルを上書きします。**ルール変更時に再ビルドは不要で、サーバーを再起動するだけで反映されます**。
* スクリプトが実装するインターフェースは `api.ScriptedRules` に定義されています。Kotlin Script の最終式は `Recenter` と `FallJuror` の両方を実装するオブジェクトである必要があり、匿名オブジェクトは使用できません。その他のスクリプトはグローバル関数（Prolog の場合は述語、最後の引数が結果）のみを定義し、Java 側でインターフェースにラップされます。
* 内蔵インタプリタはいずれもスレッドセーフを保証しないため、各スクリプトの呼び出しは直列化（`synchronized`）されます。そのため、毎 tick 実行されるホットパス上にあるのは Kotlin Script（コンパイル実行）のみであり、他はすべてコールドパスです。
* Jython に同梱されている jnr/jffi 等のライブラリが JRuby のバージョンと競合するため、ビルド時に Jython を単独でパッケージングし、これらのパッケージをリロケーションしています（上流テンプレートの手法に倣った `jythonIsolated` タスク）。

言語間の一貫性を保証している点：

* ブロック面の対応関係（UP→NORTH 等）は手動マッピングを行わず、Xtend（Bukkit `BlockFace`）および Kotlin（PacketEvents `BlockFace`）の双方が Frege の同一の回転定義から導出されます。
* セクション内の座標マッピング `Geometry.localIndex`（Frege）と Haxe の光回転処理は同一のマッピングを使用しており、`smokeTest` で 1 ブロックずつ照合検証されます。

## ビルド

```bash
./gradlew build          # 成果物：build/libs/RotatedWorldZ-1.0.0.jar。checkGeometry および smokeTest を含む
./gradlew checkGeometry  # Frege の QuickCheck 特性のみを実行
./gradlew smokeTest      # サーバー不要：完成した jar から純粋計算層を起動して元の Kotlin の挙動と比較し、全レイヤーのリンクを検証
```

実行環境のサーバーには PacketEvents の導入が必要です（`depend: [packetevents]`）。

初回のビルド時に Flix、Haxe、Fantom が `.tools/` 配下にダウンロードされます（約 60 MB）。また、Haxe は JDK 8 を必要とします（`rt.jar` のみを使用。Gradle toolchain で検出できない場合は foojay 経由で自動ダウンロードされます）。その他はすべて JDK 21 を使用します。

## 各言語の落とし穴・注意点

* **Flix**：エクスポートできるエントリー関数は1つのみで、デフォルトパッケージの `Main` クラス内に生成されます（`FlixBridge` からリフレクションで呼び出し）。また、ルートディレクトリに約500個のクラスとパッケージ（`Array`、`List` 等）を生成し、Scala や Kotlin 独自の `Array`/`List` を隠蔽してしまうため、Flix の出力は**コンパイル時の classpath には一切含めず**、実行時および jar にのみ含めています。Datalog のガードでキャプチャできる変数は最大5個です。Java インターフェースと Flix モジュールを同名にすることはできません。
* **Frege**：引数はデフォルトで遅延評価されます（Java のシグネチャ上は `Lazy<Float>`）。厳密なプリミティブ型の引数を要求する場合は、パターンに `!` を付与します。
* **Fantom**：default メソッドを持つ Java インターフェースを実装するとクラッシュするため、`api` 内のインターフェースには default メソッドを持たせていません。Java の `long`/`double`/`boolean` は Fantom の `Int`/`Float`/`Bool` に直接対応し、参照型はすべて nullable となります。浮動小数点リテラルは `0.5f` と記述する必要があります（`0.5` は Decimal 扱い）。
* **Gosu**：`block` は予約語です（Gosu のラムダ構文）。gosuc はアノテーション型を即座に解決するため、コンパイル classpath 上に Paper API が依存する `org.jetbrains:annotations` および `jspecify` が必要です。
* **Xtend**：Bukkit の `Keyed` には `key()` と `getKey()` の両方が存在するため、`b.key` は曖昧（ambiguous）となり、`b.getKey` と記述する必要があります。フィールドとメソッドが同名の場合、メソッド本体内の識別名はメソッドを指します。
* **Haxe**：`--jvm` ターゲットを使用。Java の外部クラスローダーは JDK 8 の `rt.jar` のみを認識します。公式 Windows パッケージの `haxelib` は Neko に依存するため、ビルド時に PATH 上へスタブを配置します。
* **Clojure**：スレッドコンテキストクラスローダー（TCCL）を用いて名前空間を検索するため、`ClojureBridge` は初期化時および呼び出し時にプラグインのクラスローダーを一時的に TCCL へ設定します。
* **Kotlin Script**：スクリプトの評価結果に無名オブジェクトを指定することはできません（`object : X {}` は "anonymous type" エラー）。事前に名前付きクラスを宣言する必要があります。
* **Kawa**：Java の `null` は Scheme では真（truthy）として評価されます（`#f` のみが偽）。null 判定は `(eq? x #!null)` と書く必要があります。フィールドアクセスは `obj:field`、メソッド呼び出しは `(obj:method)` です。
* **スレッドコンテキストクラスローダー**：サーバーのスレッド（メインスレッド、リージョンスレッド、Netty、グローバルスケジューラ）のコンテキストクラスローダーからはプラグインのクラスが見えません。しかし、Kawa や Clojure のランタイムがクラス名から検索する際には TCCL が使用されます。そのため、サーバースレッドからこれらのランタイムに入るすべての箇所は `kernel.PluginContext` でラップしています（Kawa キャッシュクリーンアップ、陪審団、すべてのスクリプト呼び出し、Gosu/Fantom の足場探索、Groovy コマンド）。これに伴い、`smokeTest` のメインスレッドではコンテキストクラスローダーを platform ローダーに設定し、サーバースレッドの挙動を模倣しています。
* **Prolog**：tuProlog はデフォルトで DCG をロードしないため、`-->` や `phrase/2` を有効にするには `loadLibrary(new DCGLibrary())` が必要です。
* **Jython**：`PyString` は Python 2 のバイト文字列です。パスにマルチバイト文字（中国語など）が含まれる場合は `Py.newStringOrUnicode` を使用する必要があります。このため、`smokeTest` は `build/smoke/插件 plugins/` のような非 ASCII パス上で実行されます。
* **Common Lisp (ABCL)**：初期化時にリフレクションを用いて JDK の仮想スレッドファクトリへアクセスを試みます。`--add-opens java.base/java.lang` がないと `System.err` に1行出力されます（これにより Paper が「プラグインが System.err を使用しました」と警告します）。実害はないため、`StderrFilter` で ABCL の初期化中のみこの1行をインターセプトして抑制しています。
* **陪審団**：`ceil(2.5 - 3)` の結果は `-0.0` になります。Python、Kotlin、Scheme の `max` はこれをそのまま保持するため、集計前に `0.0` へ正規化しないと、2〜3ブロック落下した際に誤って「意見の不一致」と判定されてしまいます。
* **Gosu**：ランタイムが初期化されると `java.base` がリフレクションに対して無制限に開放され、以降他のライブラリで発生するリフレクションの問題が隠蔽されてしまいます。そのため、`smokeTest` ではプラグインの `onEnable` の順序に従い、スクリプト層を先にロードしてから Gosu を呼び出します。`./gradlew smokeTest -PsmokeJava=25` を指定することで、サーバー用の JDK に切り替えて実行できます。
* **JRuby**：`sort_by` は安定ソートではないため、元の順序を維持するにはソートキーにインデックスを含める必要があります。
* **Scala 3**：ソースファイルを削除した後、増分コンパイルによって古い `.tasty` ファイルが残り、ビルドキャッシュに入り込むことがあります。jar 内に不要なクラスが含まれている場合は、`./gradlew clean build --rerun-tasks` を実行してください。

## 検証済み / 未検証

検証済み（Windows 11、JDK 21、Gradle 9.7.1）：

* 12 種類のコンパイル言語がすべて正常にコンパイルされ、8 つのスクリプト層がすべてロード完了。`./gradlew clean build --rerun-tasks` に合格。
* `checkGeometry`：往復変換の一致、グリッド／座標点の一致、視線回転とベクトル回転の一致、`zBase` の切り捨て除算を含む 10 個の QuickCheck 特性に合格。
* `smokeTest`（全 123,134 項目の検査。プラグイン読み込みをシミュレートするため、隔離されたクラスローダー下かつ非 ASCII 文字（中国語等）を含むパスから実行。JDK 21 および 25 の両方で実行済み）：
  * Flix のルール、Clojure の設定、Haxe の光計算、Fantom の探索結果が、元の Kotlin 実装と完全に一致。
  * 8 つのスクリプトおよび Kawa の結果が、担当箇所の元コードと完全に一致。陪審団の投票は 9 票すべて一致し、警告の記録なし。スクリプト層のロード時に `System.err` への出力なし。
  * Scala / Gosu / Groovy / Xtend / Kotlin の各層がリンク可能であり、Gosu / Groovy / Scala のランタイムが正常に初期化可能。

**未検証**：

* 実際の Paper / Folia サーバーでの実機稼働、およびクライアントでのログイン接続は未実施（Minecraft EULA への同意と PacketEvents の導入が必要）。パケット書き換え、チャンク送信、再センタリングなどのサーバー依存パスは、コンパイルチェックと「元コードからの忠実な逐行移植」のみが担保となります。本番導入前に必ずテストサーバーで検証してください。
* jar のサイズは約 231 MB（オリジナルは 4.9 MB）：Kotlin コンパイラ、JRuby、Jython、ABCL に加え、Gosu、Groovy、Scala、Clojure、Frege、Kawa のランタイムを内包。スクリプト層のロードによりプラグイン起動時に 4〜5 秒追加で要します。これらのランタイムは relocate（再配置）を行わずそのまま同梱されているため、同一サーバー上の他プラグインが異なるバージョンの Kotlin / Scala / Groovy を内包している場合、競合する可能性があります。

## ディレクトリ構成

```
src/api/java        レイヤー 0：純粋計算層の規約（JDK 型）
src/kernel/java     レイヤー 2：共有状態およびサービスインターフェース（Bukkit ＋ PacketEvents）
src/main/<言語>      各言語のソースコード（スクリプト層は src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog}）
src/main/resources  plugin.yml、config.yml
buildSrc            各言語の Gradle ビルドタスク
build.gradle.kts    レイヤー構成と依存関係
```
