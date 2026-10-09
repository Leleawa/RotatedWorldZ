# RotatedWorldZ

RotatedWorld(PacketEvents를 사용하여 플레이어에게 전체 월드를 X축 기준으로 90° 회전시켜 보여주는 Paper/Folia 플러그인)의 20개 언어 버전:
**Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe**를 컴파일하여 포함하고,
여기에 런타임에 로드되는 8개의 스크립트 계층인 **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog**를 더해 모두 **단 하나의 jar**로 패키징했습니다.


## 언어별 역할 분담

| 레이어 | 언어 | 담당 | 선정 이유 |
|---|---|---|---|
| 0 api | Java | 순수 계산 계층이 구현해야 하는 계약(JDK 타입만 사용) | 모든 컴파일러가 해석할 수 있는 최소공약수 |
| 1 순수 계산 | **Frege** | 좌표 대수: 회전의 유일한 정의 및 10가지 QuickCheck 속성 | 순수 함수 + 내장 QuickCheck; 박싱 없는 `static int/float/double` 메서드로 컴파일됨 |
| | **Flix** | 블록 명명 규칙(직립형 ↔ 벽 부착형, 블록 엔티티 타입), 캐시 회수 | 규칙 테이블 + 이펙트 시스템으로 순수성 보장; "어떤 청크가 아직 필요한가"를 **Datalog** 규칙으로 작성 |
| | **Clojure** | 설정 파싱: 설정 항목을 schema 데이터로 정의 | 데이터 주도: 설정 항목 추가 = 데이터 한 줄 추가 |
| | **Haxe** | 조명 nibble 배열 회전 | `haxe.io.Bytes`만 사용하며, 동일한 코드를 JS/C++(예: 오프라인 미리보기)로 컴파일 가능 |
| | **Fantom** | 안전한 착지 지점 탐색 전략 | 순수 알고리즘, `BlockProbe` 인터페이스를 통해 월드를 확인하며 Bukkit에 일절 접근하지 않음 |
| 2 kernel | Java | 플레이어 상태 `PlayerState`, `SerialExecutor`, `Host` / `BlockRotator` 인터페이스 | volatile / 원자적 변수를 사용하는 동시성 공유 상태로, Java로 작성하는 것이 가장 직관적임 |
| 3 엔진 | **Scala 3** | 청크 캐시 `ChunkStore` + 시야 엔진 `ViewManager`(부동 원점, 프리페치, 청크 전송) | 성능의 핵심, 대량의 배열 루프 처리 |
| | **Gosu** | `WorldProbe`: Bukkit 블록 / 히트박스 검사; `Material`의 enhancement 확장 | enhancement를 통해 Bukkit 타입에 프로퍼티 확장(`type.Hazard`) |
| | **Groovy** | `/rotate` 명령어의 흐름 제어(오케스트레이션) | 콜드 패스 |
| | **Kawa Scheme** | 캐시 정리: 각 월드의 시야 범위를 수집하여 Flix의 Datalog에 유지 여부 결정을 위임(원래는 Scala `ViewManager`에 위치) | 사전 컴파일, 리스트 처리 |
| | **Xtend** | `BlockRotation`: BlockData의 회전 규칙 | `instanceof` 스마트 캐스트 + 프로퍼티 문법으로 규칙 그 자체처럼 직관적으로 읽힘 |
| 4 플러그인 | **Kotlin** | 패킷 변조(Netty 핫 패스), Bukkit 이벤트, PacketEvents 벡터 퍼사드 | 원본 코드; PacketEvents를 Kotlin으로 다루는 것이 가장 매끄러움 |
| | Java | 플러그인 진입점(`Host` 구현), Flix/Fantom/Clojure 실행 브리지, 스크립트 계층 로더 | 진입점 및 조립(와이어링) |
| 5 스크립트 | **Kotlin Script** | 부동 원점 재중심화 결정(원래는 Scala `ViewManager`에 위치) | 런타임에 바이트코드로 컴파일되어 매 틱 호출되어도 느리지 않음 |
| | **JRuby** | 청크 기둥 전송 순서(원래는 Scala `ViewManager`에 위치) | 단 한 줄의 `product.each_with_index.sort_by` 체인 |
| | **JavaScript** | "너무 빠르게 이동함" 래그백(러버밴딩) 예외 허용(원래는 Kotlin 리스너에 위치) | Rhino 인터프리터 실행, 이벤트 빈도가 낮음 |
| | **Jython** | 넉백(원래는 Kotlin 리스너에 위치) | 피격 시 1회 호출 |
| | **Lua** | 낙하 데미지(원래는 Kotlin 리스너에 위치) | 수석 배심원 |
| | **BeanShell** | `/rotate status`의 상태 표시 문자열(원래는 Groovy에 위치) | Java 문법 기반 스크립트 |
| | **Common Lisp** | 회전된 플레이어에게 면제할 서버 측 검사 결정(실패한 이동, 플라잉 킥)(원래는 Kotlin 리스너에 위치) | 하나의 `cond` 결정 테이블 |
| | **Prolog** | `/rotate`의 인수 문법을 DCG로 작성(원래는 Groovy에 위치) | 문법은 마땅히 DCG로 작성해야 함 |
| | **8개 전원 + Kawa** | **낙하 데미지 배심원단**: 9개 언어가 각각 계산하고 다수결로 결정, 동률 시 Lua의 판정에 따르며 의견 불일치 시 경고 기록 | 유희(엔터테인먼트) |

**1계층은 빌드 구조상 Bukkit 및 PacketEvents를 전혀 참조할 수 없으며**(`api`만 접근 가능), 5개 언어 상호 간에도 서로를 참조할 수 없습니다. 하위 계층 호출은 일반적인 JVM 호출이며, 상위 계층 호출은 오직 인터페이스를 통해서만 이루어집니다. Flix와 Fantom은 구현체를 `api.Services`에 등록하고, 플러그인 메인 클래스는 `kernel.Host`를 통해 3계층에 서비스를 제공합니다.

## 스크립트 계층

스크립트는 컴파일되어 빌드에 포함되는 것이 아니라, 플러그인 시작 시 내장 인터프리터에 의해 로드됩니다(`script.ScriptLayers`, 8개 인터프리터가 병렬로 시작되며 약 4~5초 소요):

* 소스 코드는 `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*` 에 위치하며, 빌드 시 jar 내부의 `scripts/` 아래에 패키징됩니다.
* `plugins/RotatedWorldZ/scripts/<디렉터리>/rules.*` 파일이 존재하면 jar 내부의 파일을 덮어씁니다: **규칙 변경 시 다시 빌드할 필요 없이 서버를 재시작하기만 하면 됩니다**.
* 스크립트가 구현하는 인터페이스는 `api.ScriptedRules` 에 정의되어 있습니다. Kotlin Script의 마지막 표현식은 `Recenter`와 `FallJuror`를 동시에 구현하는 객체여야 하며 익명 객체는 사용할 수 없습니다. 나머지 스크립트는 전역 함수만 정의하고(Prolog의 경우 마지막 인수가 결과인 술어), Java에서 인터페이스로 래핑합니다.
* 내장 인터프리터는 스레드 안전성을 보장하지 않으므로, 각 스크립트의 호출은 직렬화(`synchronized`)됩니다. 따라서 매 틱 실행되는 핫 패스에는 Kotlin Script(컴파일 실행)만 위치하며, 나머지는 모두 콜드 패스입니다.
* Jython에 내장된 jnr/jffi 등의 라이브러리가 JRuby의 버전과 충돌하므로, 빌드 시 Jython을 별도로 패키징하고 해당 패키지들을 리로케이션(재배치)합니다(업스트림 템플릿의 방식을 따르는 `jythonIsolated` 태스크).

언어 간 일관성이 보장되는 부분:

* 블록 면 매핑(UP→NORTH 등)은 더 이상 수작업으로 작성하지 않으며, Xtend(Bukkit `BlockFace`)와 Kotlin(PacketEvents `BlockFace`) 모두 Frege의 동일한 회전 정의로부터 유도됩니다.
* 섹션 내부 좌표 매핑인 `Geometry.localIndex`(Frege)와 Haxe 조명 회전은 동일한 매핑을 공유하며, `smokeTest`에서 블록 단위로 일치 여부를 대조 검증합니다.

## 빌드

```bash
./gradlew build          # 결과물: build/libs/RotatedWorldZ-1.0.0.jar; checkGeometry 및 smokeTest 포함
./gradlew checkGeometry  # Frege의 QuickCheck 속성만 실행
./gradlew smokeTest      # 서버 불필요: 완성된 jar에서 순수 계산 계층을 시작하여 기존 Kotlin 동작과 대조하고 나머지 계층의 링킹을 검사
```

런타임에는 서버에 PacketEvents가 설치되어 있어야 합니다(`depend: [packetevents]`).

최초 빌드 시 Flix, Haxe, Fantom이 `.tools/` 디렉터리에 다운로드되며(약 60 MB), Haxe는 JDK 8을 추가로 필요로 합니다(rt.jar만 사용, Gradle toolchain에서 찾지 못하면 foojay를 통해 자동 다운로드됨). 그 외에는 모두 JDK 21을 사용합니다.

## 각 언어별 주의점 및 함정

* **Flix**: 진입점 함수를 단 하나만 내보낼 수 있으며, 기본 패키지의 `Main` 클래스에 생성됩니다(`FlixBridge`에서 리플렉션으로 호출). 또한 루트 디렉터리에 약 500개의 클래스와 패키지(`Array`, `List` 등)를 생성하여 Scala/Kotlin 자체의 `Array`/`List`를 가려버리기 때문에, Flix의 출력물은 **그 어떤 컴파일 classpath에도 포함되지 않으며** 런타임 및 jar에만 포함됩니다. Datalog 가드(guard)는 최대 5개의 변수만 캡처할 수 있습니다. Java 인터페이스와 Flix 모듈은 동일한 이름을 가질 수 없습니다.
* **Frege**: 매개변수는 기본적으로 지연 평가(lazy)됩니다(Java 시그니처에서는 `Lazy<Float>`). 엄격한 원시 타입 매개변수가 필요한 경우 패턴에 `!`를 붙입니다.
* **Fantom**: default 메서드가 포함된 Java 인터페이스를 구현하면 크래시가 발생하므로 `api`의 인터페이스에는 default 메서드가 없습니다. Java의 `long`/`double`/`boolean`은 Fantom의 `Int`/`Float`/`Bool`에 직접 대응되며, 모든 참조 타입은 널 가능(nullable)합니다. 부동소수점 리터럴은 `0.5f`로 작성해야 합니다(`0.5`는 Decimal로 취급됨).
* **Gosu**: `block`은 예약어입니다(Gosu의 람다). gosuc는 어노테이션 타입을 즉시 확인하므로 컴파일 classpath에 Paper API 의존성인 `org.jetbrains:annotations` 및 `jspecify`가 있어야 합니다.
* **Xtend**: Bukkit의 `Keyed`에 `key()`와 `getKey()`가 모두 존재하므로 `b.key`는 모호하여 `b.getKey`로 작성해야 합니다. 필드와 메서드의 이름이 같을 경우 메서드 본문 내의 이름은 메서드를 가리킵니다.
* **Haxe**: `--jvm` 타깃을 사용합니다. Java 외부 클래스 로더는 JDK 8의 `rt.jar`만 인식합니다. 공식 Windows 패키지의 `haxelib`는 Neko에 의존하므로 빌드 시 PATH에 스텁(stub)을 배치합니다.
* **Clojure**: 스레드 컨텍스트 클래스 로더(TCCL)를 통해 네임스페이스를 탐색하므로, `ClojureBridge`는 초기화 및 호출 시 임시로 플러그인의 클래스 로더를 가리키도록 설정합니다.
* **Kotlin Script**: 스크립트의 결과는 익명 객체일 수 없습니다(`object : X {}`는 "anonymous type" 오류 발생). 반드시 명명된 클래스를 먼저 선언해야 합니다.
* **Kawa**: Java의 `null`은 Scheme에서 참(truthy)으로 평가됩니다(`#f`만 거짓). null 검사는 `(eq? x #!null)`로 작성해야 합니다. 필드 접근은 `obj:field`, 메서드 호출은 `(obj:method)`입니다.
* **스레드 컨텍스트 클래스 로더**: 서버 스레드(메인 스레드, 리전 스레드, Netty, 글로벌 스케줄러)의 컨텍스트 클래스 로더는 플러그인의 클래스를 볼 수 없지만, Kawa와 Clojure 런타임이 이름으로 클래스를 찾을 때 바로 이 TCCL을 사용합니다. 따라서 서버 스레드에서 이러한 런타임으로 진입하는 모든 지점은 `kernel.PluginContext`로 감싸서 실행합니다(Kawa 캐시 정리, 배심원단, 모든 스크립트 호출, Gosu/Fantom 착지 지점 탐색, Groovy 명령어). 이에 따라 `smokeTest`의 메인 스레드는 컨텍스트 클래스 로더를 platform 로더로 설정하여 서버 스레드 환경을 시뮬레이션합니다.
* **Prolog**: tuProlog는 기본적으로 DCG를 로드하지 않으므로, `-->` 및 `phrase/2`를 사용하려면 `loadLibrary(new DCGLibrary())`를 호출해야 합니다.
* **Jython**: `PyString`은 Python 2의 바이트 문자열입니다. 경로에 비-ASCII 문자(한글, 중국어 등)가 포함된 경우 `Py.newStringOrUnicode`를 사용해야 합니다. 따라서 `smokeTest`는 `build/smoke/插件 plugins/` 와 같은 비-ASCII 경로에서 실행됩니다.
* **Common Lisp (ABCL)**: 초기화 시 리플렉션을 통해 JDK의 가상 스레드 팩토리를 열려고 시도합니다. `--add-opens java.base/java.lang` 옵션이 없으면 `System.err`에 한 줄을 출력합니다(이로 인해 Paper에서 "플러그인이 System.err를 사용했습니다"라는 경고가 발생함). 무해하며, `StderrFilter`가 ABCL 초기화 중에 이 한 줄만 가로채서 억제합니다.
* **배심원단**: `ceil(2.5 - 3)`은 `-0.0`이 됩니다. Python / Kotlin / Scheme의 `max` 함수는 이를 그대로 보존하므로, 표를 집계하기 전에 `0.0`으로 정규화해야 합니다. 그렇지 않으면 2~3블록 낙하 시 잘못된 "의견 불일치" 경고가 발생합니다.
* **Gosu**: 런타임이 초기화되는 즉시 `java.base`가 리플렉션에 완전히 개방되어 이후 다른 라이브러리의 리플렉션 문제를 감지할 수 없게 됩니다. 따라서 `smokeTest`는 플러그인의 `onEnable` 순서에 맞춰 스크립트 계층을 먼저 로드한 후 Gosu를 호출합니다. `./gradlew smokeTest -PsmokeJava=25` 명령어로 서버에서 사용할 JDK로 변경하여 실행할 수 있습니다.
* **JRuby**: `sort_by`는 불안정 정렬(unstable sort)이므로, 원래 순서를 유지하려면 정렬 키에 인덱스를 포함해야 합니다.
* **Scala 3**: 소스 파일을 삭제한 후 증분 컴파일 시 이전 `.tasty` 파일이 남아 빌드 캐시에 들어갈 수 있습니다. jar에 포함되지 않아야 할 클래스가 있는 경우 `./gradlew clean build --rerun-tasks`를 한 번 실행하십시오.

## 검증 완료 / 미검증

검증 완료 (Windows 11, JDK 21, Gradle 9.7.1):

* 12개 컴파일 언어 전체 컴파일 완료, 8개 스크립트 계층 전체 로드 성공, `./gradlew clean build --rerun-tasks` 통과.
* `checkGeometry`: 왕복 일치성, 격자/좌표 일치성, 시야 회전과 벡터 회전의 일치성, `zBase` 내림 나눗셈을 포함한 10가지 QuickCheck 속성 통과.
* `smokeTest` (총 123,134개 검사 항목, 플러그인 로드를 시뮬레이션하기 위해 격리된 클래스 로더 환경 및 비-ASCII(다국어) 경로에서 실행, JDK 21 및 25에서 모두 실행 완료):
  * Flix 규칙, Clojure 설정, Haxe 조명, Fantom 탐색 결과가 기존 Kotlin 구현과 항목별로 완벽히 일치;
  * 8개 스크립트 및 Kawa의 실행 결과가 이관받은 기존 코드와 항목별로 완벽히 일치, 배심원단 9표 전원 일치 및 경고 미발생; 스크립트 계층 로드 시 `System.err` 출력 없음;
  * Scala/Gosu/Groovy/Xtend/Kotlin 각 계층의 링킹 확인, Gosu/Groovy/Scala 런타임 초기화 확인.

**미검증**:

* 실제 Paper / Folia 서버 환경에서의 구동 및 클라이언트 접속 테스트는 진행하지 않음(Minecraft EULA 동의 및 PacketEvents 설치 필요). 패킷 변조, 청크 전송, 재중심화 등 서버에 의존적인 경로는 컴파일 검사와 "원본 코드로부터의 한 줄 단위 이식"으로만 보장됨. 실제 서버 적용 전에 반드시 테스트 서버에서 사전 검증을 진행할 것.
* jar 용량 약 231 MB(원본 4.9 MB): Kotlin 컴파일러, JRuby, Jython, ABCL 및 Gosu, Groovy, Scala, Clojure, Frege, Kawa의 런타임 포함. 스크립트 계층 로드로 인해 플러그인 시작 시 약 4~5초가 추가 소요됨. 이러한 런타임들은 relocate(패키지 재배치) 없이 그대로 패키징되어 있으므로, 동일한 서버에 서로 다른 버전의 Kotlin/Scala/Groovy를 포함한 다른 플러그인이 있을 경우 충돌할 가능성이 있음.

## 디렉터리 구조

```
src/api/java        0계층: 순수 계산 계층의 계약(JDK 타입)
src/kernel/java     2계층: 공유 상태 및 서비스 인터페이스(Bukkit + PacketEvents)
src/main/<언어>      각 언어별 소스 코드(스크립트 계층은 src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog})
src/main/resources  plugin.yml, config.yml
buildSrc            각 언어별 Gradle 빌드 태스크
build.gradle.kts    계층 분리 및 의존성 설정
```
