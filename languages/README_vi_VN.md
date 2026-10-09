# RotatedWorldZ

Phiên bản 20 ngôn ngữ của RotatedWorld (plugin Paper/Folia dùng PacketEvents để xoay toàn bộ thế giới 90° quanh trục X cho người chơi quan sát):
**Java, Kotlin, Scala 3, Gosu, Groovy, Kawa Scheme, Xtend, Fantom, Flix, Frege, Clojure, Haxe** được biên dịch vào,
cùng 8 tầng script tải khi runtime: **Kotlin Script, JRuby, JavaScript, Jython, Lua, BeanShell, Common Lisp, Prolog**, tất cả được đóng gói thành **một file jar duy nhất**.


## Ai chịu trách nhiệm phần nào

| Tầng | Ngôn ngữ | Chịu trách nhiệm | Lý do lựa chọn |
|---|---|---|---|
| 0 api | Java | Hợp đồng (contract) cần triển khai cho tầng tính toán thuần túy (chỉ dùng các kiểu JDK) | Mẫu số chung nhỏ nhất mà mọi trình biên dịch đều hiểu được |
| 1 Tính toán thuần túy | **Frege** | Đại số tọa độ: định nghĩa duy nhất về phép xoay, cùng 10 tính chất QuickCheck | Hàm thuần túy + tích hợp sẵn QuickCheck; biên dịch thành phương thức `static int/float/double`, không boxing |
| | **Flix** | Quy tắc đặt tên khối (dạng đứng ↔ dạng gắn tường, loại block entity); thu hồi bộ nhớ đệm (cache) | Bảng quy tắc + hệ thống hiệu ứng (effect system) đảm bảo tính thuần túy; "chunk nào vẫn còn người cần" là một quy tắc **Datalog** |
| | **Clojure** | Phân tích cấu hình: các mục cấu hình được viết dưới dạng dữ liệu schema | Hướng dữ liệu (data-driven): thêm mục cấu hình = thêm một dòng dữ liệu |
| | **Haxe** | Phép xoay mảng nibble của ánh sáng | Chỉ dùng `haxe.io.Bytes`, cùng một mã nguồn có thể biên dịch sang JS/C++ (ví dụ: trình xem trước ngoại tuyến) |
| | **Fantom** | Chiến lược tìm kiếm vị trí tiếp đất an toàn | Thuật toán thuần túy, quan sát thế giới qua interface `BlockProbe`, không đụng tới Bukkit |
| 2 kernel | Java | Trạng thái người chơi `PlayerState`, `SerialExecutor`, interface `Host` / `BlockRotator` | Trạng thái chia sẻ đồng thời có volatile / biến nguyên tử (atomic), viết bằng Java là trực tiếp nhất |
| 3 Động cơ | **Scala 3** | Bộ đệm chunk `ChunkStore` + động cơ tầm nhìn `ViewManager` (gốc tọa độ động / floating origin, tìm nạp trước, gửi chunk) | Trọng tâm hiệu năng, nhiều vòng lặp mảng |
| | **Gosu** | `WorldProbe`: kiểm tra khối / hộp va chạm (hitbox) của Bukkit; enhancement cho `Material` | enhancement bổ sung thuộc tính cho các kiểu Bukkit (`type.Hazard`) |
| | **Groovy** | Điều phối luồng lệnh `/rotate` | Đường dẫn nguội (cold path) |
| | **Kawa Scheme** | Dọn dẹp cache: thu thập phạm vi tầm nhìn của từng thế giới, giao cho Datalog của Flix quyết định giữ lại phần nào (trước đây nằm trong `ViewManager` của Scala) | Biên dịch trước (AOT), xử lý danh sách |
| | **Xtend** | `BlockRotation`: quy tắc xoay BlockData | Ép kiểu tự động bằng `instanceof` + cú pháp thuộc tính (property syntax), quy tắc đọc lên tự nhiên đúng như bản chất quy tắc |
| 4 Plugin | **Kotlin** | Viết lại packet (đường dẫn nóng netty), sự kiện Bukkit, facade vector PacketEvents | Mã nguồn gốc; viết PacketEvents bằng Kotlin mượt mà nhất |
| | Java | Điểm khởi nhập plugin (triển khai `Host`), cầu nối khởi động Flix/Fantom/Clojure, trình tải tầng script | Điểm khởi nhập và lắp ráp |
| 5 Script | **Kotlin Script** | Quyết định tái định vị gốc tọa độ động (trước đây trong `ViewManager` của Scala) | Biên dịch thành bytecode khi runtime, gọi mỗi tick cũng không bị chậm |
| | **JRuby** | Thứ tự gửi cột chunk (trước đây trong `ViewManager` của Scala) | Chuỗi pipeline `product.each_with_index.sort_by` |
| | **JavaScript** | Bỏ qua hiện tượng giật lag "di chuyển quá nhanh" (trước đây trong listener của Kotlin) | Rhino thông dịch thực thi, sự kiện rất hiếm |
| | **Jython** | Độ giật lùi / knockback (trước đây trong listener của Kotlin) | Mỗi lần chịu đòn đánh chạy một lần |
| | **Lua** | Sát thương rơi (trước đây trong listener của Kotlin) | Bồi thẩm viên trưởng |
| | **BeanShell** | Dòng chữ hiển thị của `/rotate status` (trước đây trong Groovy) | Script với cú pháp Java |
| | **Common Lisp** | Những kiểm tra phía server nào cho phép người chơi xoay bỏ qua (di chuyển thất bại, kick do bay) (trước đây trong listener của Kotlin) | Bảng quyết định `cond` |
| | **Prolog** | Ngữ pháp đối số của `/rotate`, viết bằng DCG (trước đây trong Groovy) | Ngữ pháp thì nên dùng DCG |
| | **Toàn bộ 8 script + Kawa** | **Bồi thẩm đoàn sát thương rơi**: chín ngôn ngữ cùng tính toán độc lập, thiểu số phục tùng đa số, hòa phiếu nghe theo Lua, ghi cảnh báo khi có ý kiến bất đồng | Giải trí |

**Tầng 1 khi build hoàn toàn không nhìn thấy Bukkit và PacketEvents** (chúng chỉ nhận được `api`), và năm ngôn ngữ này cũng không nhìn thấy lẫn nhau. Lời gọi hướng xuống là các lệnh gọi JVM thông thường; lời gọi hướng lên chỉ có thể đi qua interface: Flix và Fantom giao việc triển khai cho `api.Services`, lớp chính của plugin cung cấp dịch vụ cho Tầng 3 thông qua `kernel.Host`.

## Tầng script

Script không được biên dịch sẵn vào mà được tải bởi các trình thông dịch nhúng khi plugin khởi động (`script.ScriptLayers`, 8 trình thông dịch khởi động song song, mất khoảng 4–5 giây):

* Mã nguồn nằm tại `src/main/<kts|jruby|javascript|jython|lua|beanshell|commonlisp|prolog>/rules.*`, khi build sẽ được đóng gói vào thư mục `scripts/` của file jar.
* Nếu `plugins/RotatedWorldZ/scripts/<thư mục>/rules.*` tồn tại, nó sẽ ghi đè lên file trong jar: **sửa quy tắc không cần build lại, chỉ cần khởi động lại server**.
* Interface do script triển khai nằm trong `api.ScriptedRules`. Biểu thức cuối cùng của Kotlin Script phải là một đối tượng đồng thời triển khai `Recenter` và `FallJuror`, và không được là đối tượng ẩn danh (anonymous object); các script còn lại chỉ định nghĩa các hàm toàn cục (Prolog là predicate, tham số cuối cùng là kết quả), được Java bọc thành interface.
* Các trình thông dịch nhúng đều không đảm bảo thread-safe, lời gọi tới mỗi script được thực hiện tuần tự (`synchronized`). Do đó chỉ có Kotlin Script (biên dịch thực thi) nằm trên đường dẫn nóng mỗi tick, các script còn lại đều thuộc đường dẫn nguội.
* Các thư viện như jnr/jffi đi kèm Jython xung đột phiên bản với JRuby, do đó khi build, Jython được đóng gói riêng trước và relocate các package này (task `jythonIsolated`, kế thừa cách làm từ template upstream).

Một số điểm đảm bảo tính nhất quán giữa các ngôn ngữ:

* Ánh xạ các mặt khối (UP→NORTH……) không còn viết thủ công, Xtend (Bukkit `BlockFace`) và Kotlin (PacketEvents `BlockFace`) đều được suy ra từ cùng một phép xoay của Frege.
* Ánh xạ tọa độ trong section `Geometry.localIndex` (Frege) và phép xoay ánh sáng của Haxe dùng chung một ánh xạ, được so sánh từng ô một trong `smokeTest`.

## Build

```bash
./gradlew build          # Thành phẩm: build/libs/RotatedWorldZ-1.0.0.jar; bao gồm checkGeometry và smokeTest
./gradlew checkGeometry  # Chỉ chạy các tính chất QuickCheck của Frege
./gradlew smokeTest      # Không cần server: khởi động tầng tính toán thuần túy từ jar thành phẩm và đối chiếu hành vi với Kotlin gốc, kiểm tra liên kết các tầng còn lại
```

Runtime yêu cầu server phải cài đặt PacketEvents (`depend: [packetevents]`).

Lần build đầu tiên sẽ tải Flix, Haxe, Fantom về `.tools/` (khoảng 60 MB), Haxe còn cần một JDK 8 (chỉ dùng `rt.jar` của nó, nếu Gradle toolchain không tìm thấy sẽ tự động tải qua foojay). Tất cả phần còn lại đều dùng JDK 21.

## Điểm cần lưu ý ở từng ngôn ngữ

* **Flix**: Chỉ có thể xuất một hàm khởi nhập duy nhất, được sinh ra trong class `Main` của default package (`FlixBridge` gọi thông qua reflection). Nó cũng sinh ra khoảng 500 class và package ở thư mục gốc (`Array`, `List`……), có thể che khuất `Array`/`List` của chính Scala/Kotlin, vì vậy đầu ra của Flix **không nằm trên bất kỳ compilation classpath nào**, chỉ đưa vào runtime và jar. Guard của Datalog bắt tối đa 5 biến. Java interface và module Flix không được trùng tên.
* **Frege**: Tham số mặc định là lười (lazy, trong chữ ký Java là `Lazy<Float>`), muốn tham số kiểu nguyên thủy nghiêm ngặt (strict) thì thêm `!` vào pattern.
* **Fantom**: Triển khai Java interface có default method sẽ bị crash, vì vậy các interface trong `api` không có default method. `long`/`double`/`boolean` của Java ánh xạ trực tiếp sang `Int`/`Float`/`Bool` của Fantom, các kiểu tham chiếu đều có thể nhận null (nullable). Số thực dấu phẩy động phải viết là `0.5f` (`0.5` là Decimal).
* **Gosu**: `block` là từ khóa (lambda của Gosu). gosuc phân giải ngay các kiểu annotation, nên compilation classpath phải có `org.jetbrains:annotations` và `jspecify` mà Paper API phụ thuộc.
* **Xtend**: `Keyed` của Bukkit có cả `key()` lẫn `getKey()`, viết `b.key` sẽ bị mơ hồ (ambiguous), phải viết `b.getKey`. Khi trường (field) và phương thức trùng tên, tên bên trong thân phương thức sẽ trỏ tới phương thức.
* **Haxe**: Target `--jvm`; class loader ngoại vi của Java chỉ nhận `rt.jar` của JDK 8; `haxelib` trong bản phân phối chính thức cho Windows phụ thuộc vào Neko, khi build cần đặt một stub vào PATH.
* **Clojure**: Dựa vào Thread Context ClassLoader để tìm namespace, `ClojureBridge` tạm thời trỏ đến class loader của plugin trong lúc khởi tạo và gọi.
* **Kotlin Script**: Kết quả của script không được là đối tượng ẩn danh (`object : X {}` báo lỗi "anonymous type"), cần khai báo một class có tên trước.
* **Kawa**: Giá trị `null` của Java trong Scheme mang giá trị chân lý (truthy - chỉ có `#f` là false), kiểm tra phải viết `(eq? x #!null)`; `obj:field` để đọc trường, `(obj:method)` để gọi phương thức.
* **Thread Context ClassLoader**: Context class loader của các luồng server (luồng chính, region thread, netty, bộ điều phối toàn cục) không nhìn thấy các class của plugin, trong khi runtime của Kawa, Clojure khi tìm class theo tên lại dùng chính nó. Vì vậy, mọi vị trí từ luồng server đi vào các runtime này đều được bọc một lớp bằng `kernel.PluginContext` (dọn dẹp cache Kawa, bồi thẩm đoàn, tất cả các lời gọi script, Gosu/Fantom tìm điểm tiếp đất, lệnh Groovy). Luồng chính của `smokeTest` do đó đặt context class loader thành platform loader để mô phỏng luồng server.
* **Prolog**: tuProlog mặc định không tải DCG, phải gọi `loadLibrary(new DCGLibrary())` mới có `-->` và `phrase/2`.
* **Jython**: `PyString` là chuỗi byte của Python 2, khi đường dẫn chứa ký tự tiếng Trung / non-ASCII phải dùng `Py.newStringOrUnicode`. Do đó `smokeTest` được chạy từ đường dẫn non-ASCII kiểu như `build/smoke/插件 plugins/`.
* **Common Lisp (ABCL)**: Khi khởi tạo cố dùng reflection mở virtual thread factory của JDK, nếu không có `--add-opens java.base/java.lang` sẽ in một dòng ra `System.err` (khiến Paper cảnh báo "plugin đã dùng System.err"). Điều này vô hại, `StderrFilter` chỉ chặn duy nhất dòng này trong thời gian khởi tạo ABCL.
* **Bồi thẩm đoàn**: `ceil(2.5 - 3)` ra `-0.0`, `max` trong Python / Kotlin / Scheme giữ nguyên giá trị này, trước khi kiểm phiếu phải chuẩn hóa về `0.0`, nếu không khi rơi 2～3 block sẽ báo sai "ý kiến bất đồng".
* **Gosu**: Runtime vừa khởi tạo sẽ mở `java.base` cho reflection, sau đó các vấn đề reflection của những thư viện khác sẽ không thể phát hiện ra. Vì vậy `smokeTest` tuân theo thứ tự `onEnable` của plugin: tải tầng script trước, rồi mới chạm đến Gosu. Có thể dùng `./gradlew smokeTest -PsmokeJava=25` để chạy bằng JDK mà server sử dụng.
* **JRuby**: `sort_by` không ổn định (unstable), muốn giữ thứ tự ban đầu phải đưa chỉ số (index) vào khóa sắp xếp.
* **Scala 3**: Sau khi xóa file mã nguồn, biên dịch gia tăng (incremental compilation) có thể để lại file `.tasty` cũ và lọt vào build cache. Nếu gặp trường hợp trong jar có class không mong muốn, hãy chạy `./gradlew clean build --rerun-tasks` một lần.

## Đã kiểm chứng / Chưa kiểm chứng

Đã kiểm chứng (Windows 11, JDK 21, Gradle 9.7.1):

* Toàn bộ 12 ngôn ngữ biên dịch đều biên dịch thành công, toàn bộ 8 tầng script đều tải thành công, `./gradlew clean build --rerun-tasks` vượt qua.
* `checkGeometry`: 10 tính chất QuickCheck vượt qua, bao gồm khứ hồi (roundtrip), tính nhất quán ô/điểm, phép xoay góc nhìn khớp với phép xoay vector, `zBase` là phép chia làm tròn xuống (floor division).
* `smokeTest` (tổng cộng 123.134 phép kiểm tra, chạy trong class loader cô lập, từ đường dẫn chứa ký tự tiếng Trung nhằm mô phỏng nạp plugin; đã chạy trên cả JDK 21 và 25):
  * Kết quả của quy tắc Flix, cấu hình Clojure, ánh sáng Haxe, tìm kiếm Fantom khớp từng mục một với bản triển khai Kotlin gốc;
  * Kết quả của 8 script và Kawa khớp từng mục một với mã nguồn gốc mà chúng thay thế, 9 lá phiếu bồi thẩm đoàn hoàn toàn nhất trí, không ghi bất kỳ cảnh báo nào; khi tải tầng script không in ra `System.err`;
  * Các tầng Scala/Gosu/Groovy/Xtend/Kotlin liên kết bình thường, runtime của Gosu/Groovy/Scala khởi tạo bình thường.

**Chưa kiểm chứng**:

* Chưa chạy trên server Paper / Folia thực tế, cũng chưa dùng client tham gia server (điều đó đòi hỏi chấp nhận Minecraft EULA và cài đặt PacketEvents). Các đường dẫn phụ thuộc server như viết lại packet, gửi chunk, tái định vị chỉ có kiểm tra biên dịch và việc "chuyển mã từng dòng từ bản gốc" bảo đảm. Vui lòng kiểm tra trên server test trước khi đưa lên môi trường thực tế.
* File jar khoảng 231 MB (bản gốc 4.9 MB): chứa trình biên dịch Kotlin, JRuby, Jython, ABCL, cộng thêm runtime của Gosu, Groovy, Scala, Clojure, Frege, Kawa. Khởi động plugin tốn thêm 4–5 giây để tải các tầng script. Các runtime này được đóng gói nguyên bản, không relocate: nếu trên cùng một server có các plugin khác mang theo các phiên bản Kotlin/Scala/Groovy khác, có thể xảy ra xung đột.

## Cấu trúc thư mục

```
src/api/java        Tầng 0: Hợp đồng của tầng tính toán thuần túy (kiểu JDK)
src/kernel/java     Tầng 2: Trạng thái chia sẻ và interface dịch vụ (Bukkit + PacketEvents)
src/main/<ngôn ngữ>  Mã nguồn từng ngôn ngữ (tầng script tại src/main/{kts,jruby,javascript,jython,lua,beanshell,commonlisp,prolog})
src/main/resources  plugin.yml, config.yml
buildSrc            Task biên dịch Gradle cho các ngôn ngữ
build.gradle.kts    Phân tầng và phụ thuộc
```
