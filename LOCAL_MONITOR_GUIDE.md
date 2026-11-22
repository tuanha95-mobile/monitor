# Measure – Local Monitor Mode (Hướng dẫn tích hợp & sử dụng)

Bản fork này của [measure-sh](https://github.com/measure-sh/measure) đã được chỉnh để:

- **Không đẩy dữ liệu lên server.** Mọi monitor (event + span) được **giữ lại trong database cục bộ** của thiết bị.
- **In từng monitor ra console/logcat ngay khi phát sinh**, để dev nhìn thấy realtime.
- Thêm API **đọc lại các monitor đã lưu** từ database.

Áp dụng cho **Android** và **iOS**.

---

## 1. Cơ chế hoạt động

| | Bản gốc | Bản này |
|---|---|---|
| Nơi lưu | SQLite/CoreData cục bộ (tạm) → upload server → xoá | SQLite/CoreData cục bộ, **giữ lại** |
| Mạng | Gửi batch lên API | **Không gửi** (bỏ qua hoàn toàn) |
| Hiển thị cho dev | Chỉ khi bật debug log | **Luôn in** mỗi monitor ra console |

- Dữ liệu vẫn bị giới hạn bởi `maxDiskUsageInMb` (mặc định 50MB) — khi vượt, dữ liệu **cũ nhất** bị dọn dần (buffer cuộn). Đây là hành vi an toàn, không phải mất dữ liệu đang quan tâm.
- "Monitor" = **event** (crash, ANR, gesture, lifecycle, http, log, screen view, custom…) và **span** (trace).

---

## 2. Android

### 2.1. Yêu cầu toolchain
Fork này đã nâng để chạy với Android Studio dùng **JDK 25**:

- Gradle **9.3.1**, AGP **9.0.1**, Kotlin **2.2.10**
- NDK pin `27.0.12077973`

> Nếu Android Studio báo chọn Gradle JDK: chọn JDK 17–21 **hoặc** JDK 25 (đã tương thích nhờ bản nâng này).

### 2.2. Nhúng SDK vào app

Publish SDK ra Maven Local rồi dùng như dependency:

```bash
cd android/measure-android
./gradlew :measure:publishToMavenLocal
```

Trong app của bạn:

```kotlin
// settings.gradle.kts / build.gradle.kts repositories
repositories { mavenLocal(); google(); mavenCentral() }

// app/build.gradle.kts
dependencies {
    implementation("sh.measure:measure-android:0.23.0-SNAPSHOT")
}
```

### 2.3. Manifest — KHÔNG cần credential

Local mode **không còn yêu cầu** `API_URL`/`API_KEY`. Bạn có thể bỏ hẳn phần meta-data này khỏi `AndroidManifest.xml`. SDK vẫn khởi tạo và lưu/in monitor bình thường.

> (Tuỳ chọn) Nếu sau này muốn chuyển sang gửi server, cứ thêm lại 2 meta-data `sh.measure.android.API_URL` và `sh.measure.android.API_KEY` — khi có credential hợp lệ, network client sẽ được khởi tạo (nhưng vẫn không upload chừng nào còn ở local mode).

### 2.4. Khởi tạo (Application.onCreate)

```kotlin
import sh.measure.android.Measure
import sh.measure.android.config.MeasureConfig

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Measure.init(
            this,
            MeasureConfig(
                enableLogging = true,            // in thêm log nội bộ SDK (tuỳ chọn)
                enableFullCollectionMode = true, // thu mọi event/span, không sampling (nên bật khi debug)
            ),
        )
    }
}
```

### 2.5. Xem monitor realtime

Mọi monitor được in ra logcat dưới tag **`MeasureMonitor`**:

```bash
adb logcat -s MeasureMonitor
```

Ví dụ output:
```
EVENT  gesture_click
   session : 0f9c...e21
   time    : 2026-10-30T08:12:33.120Z
   data    : ClickData(target=Button, ...)
SPAN   screen_load  142ms  [Ok]
   session : 0f9c...e21
Local mode: not uploading. Retained in DB -> 37 events, 4 spans.
```

### 2.6. Đọc lại monitor đã lưu từ DB

```kotlin
// Nên gọi off main-thread (đọc DB đồng bộ). Trả về chuỗi + tự in ra logcat.
val dump = Measure.getStoredMonitors(limit = 100)
Log.i("MyApp", dump)
```

### 2.7. Tự track thêm (tuỳ chọn)

```kotlin
Measure.trackScreenView("Home")
Measure.trackEvent("checkout_started", mapOf("cart_size" to NumberAttr(3)))
val span = Measure.startSpan("load_profile"); /* ... */ span.end()
```

---

## 3. iOS

### 3.1. Yêu cầu
- Xcode 27.x → **min iOS đã nâng lên 15.0** (bản gốc 13.0, Xcode 27 không build được <15).
- `libwebp` là git submodule — clone kèm submodule:
  ```bash
  git submodule update --init --recursive
  ```

### 3.2. Nhúng SDK
Mở `ios/Measure.xcworkspace` và thêm target `Measure` vào app, **hoặc** dùng CocoaPods:

```ruby
# Podfile
platform :ios, '15.0'
pod 'measure-sh', :path => '../path/to/monitor'   # trỏ tới bản fork
```

### 3.3. Khởi tạo (AppDelegate / App init)

Local mode **không cần credential**. Dùng overload init không tham số:

```swift
import MeasureSDK   // hoặc: import Measure

Measure.initialize(config: BaseMeasureConfig(
    enableLogging: true,
    enableFullCollectionMode: true
))
Measure.start()
```

> (Tuỳ chọn) Vẫn có thể dùng `Measure.initialize(with: ClientInfo(apiKey:apiUrl:), config:)` nếu sau này muốn cấu hình server.

### 3.4. Xem monitor realtime
In ra Xcode console với prefix **`[MeasureMonitor]`**:

```
[MeasureMonitor] EVENT  gesture_click
   session : 0f9c...e21
   time    : 2026-10-30T08:12:33.120Z
   data    : ...
[MeasureMonitor] SPAN   screen_load  142ms  [ok]
   session : 0f9c...e21
[MeasureMonitor] Local mode: not uploading. Retained in DB -> 37 events, 4 spans.
```

### 3.5. Đọc lại monitor đã lưu từ DB

```swift
let dump = Measure.getStoredMonitors(limit: 100)  // trả về String + in ra console
print(dump)
```

---

## 4. Build lại từ môi trường sạch

> Môi trường bị chặn mạng (sandbox TLS) khi tải Gradle/SPM → build bằng terminal thường của bạn.

**Android**
```bash
cd android/measure-android
# cần local.properties trỏ Android SDK (sdk.dir=/Users/<you>/Library/Android/sdk)
./gradlew :measure:assembleDebug
```

**iOS**
```bash
cd ios
git submodule update --init --recursive
xcodebuild -workspace Measure.xcworkspace -scheme Measure \
  -destination 'generic/platform=iOS Simulator' -configuration Debug \
  CODE_SIGNING_ALLOWED=NO build
```

---

## 5. Lưu ý / giới hạn hiện tại

- Hai module Android phụ `:benchmarks` và `:baseline-target` đang bị **tạm loại** trong `settings.gradle.kts` (plugin `androidx.baselineprofile 1.4.1` chưa tương thích AGP 9). Không ảnh hưởng tới SDK/tính năng.
- Bật `enableFullCollectionMode = true` khi muốn thấy **mọi** monitor (tắt sampling). Để mặc định thì một số loại event bị lấy mẫu theo cấu hình gốc.
- Dữ liệu nằm trong app sandbox:
  - Android: database SQLite trong `databases/` của app (pull: `adb shell run-as <package> ...`).
  - iOS: CoreData trong thư mục app.
- Vì giữ dữ liệu cục bộ, tôn trọng `maxDiskUsageInMb` để tránh đầy đĩa.

---

## 6. Các điểm đã chỉnh trong fork (tham khảo)

> **Luồng upload/network đã bị xóa hẳn khỏi code** (không chỉ tắt). Không còn `NetworkClient`/`HttpClient`/tải config từ xa/upload profile/credential. Vẫn giữ phần theo dõi HTTP của chính app (`okhttp`/`httpurl` trên Android, `Http/` trên iOS) và các lớp lưu trữ DB (packet/batch/attachment store) vì đó là lưu trữ, không phải truyền mạng.

**Android** — `android/measure-android/measure/src/main/java/sh/measure/android/`
- `logger/MonitorPrinter.kt` *(mới)* — in monitor ra logcat.
- `events/SignalProcessor.kt` — in mỗi event/span khi lưu.
- `exporter/Exporter.kt` — viết lại local-only (chỉ in tổng số monitor đang giữ trong DB).
- `config/ConfigLoader.kt` — chỉ còn load config từ disk.
- `storage/Database.kt`, `storage/DbConstants.kt` — query đọc monitor gần nhất.
- `MeasureInternal.kt`, `Measure.kt` — API `getStoredMonitors()`, bỏ setup credential.
- *Đã xóa:* `exporter/NetworkClient.kt`, `exporter/HttpUrlConnectionClient.kt`, `exporter/HttpResponse.kt`, `exporter/LoggingOutputStream.kt`, `config/ConfigResponse.kt`, `profiling/ProfileUpload{Scheduler,Worker}.kt`.

**iOS** — `ios/Sources/MeasureSDK/Swift/`
- `Utils/Constants.swift` — thêm `MonitorPrinter`.
- `Events/SignalProcessor.swift` — in mỗi event/span khi lưu.
- `Exporter/Exporter.swift` — viết lại local-only (chỉ in tổng số monitor đang giữ trong DB).
- `Config/ConfigLoader.swift` — chỉ còn load config từ disk.
- `MeasureInternal.swift`, `Measure.swift` — API `getStoredMonitors(limit:)`, bỏ credential, thêm `Measure.initialize()`.
- *Đã xóa:* `Exporter/NetworkClient.swift`, `Exporter/HttpClient.swift`, `Exporter/HttpModels.swift` (và tham chiếu trong `.xcodeproj`).
