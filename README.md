# LockTimer — Hẹn Giờ Khóa Máy

App Android native (Kotlin + Jetpack Compose) cho phép hẹn một khoảng thời gian
(5/15/30 phút, 1–2 giờ hoặc tùy chỉnh). Khi đếm ngược hết giờ, máy sẽ **tự động
khóa màn hình** — ví dụ hẹn 15 phút trước khi đi ngủ.

## Tính năng

- Chọn nhanh 5 / 15 / 30 phút, 1 giờ, 2 giờ hoặc nhập số phút tùy chỉnh.
- Đồng hồ đếm ngược dạng vòng tròn tiến trình (mm:ss / h:mm:ss).
- Hết giờ → khóa màn hình bằng `DevicePolicyManager.lockNow()` (Device Admin).
- Chạy đúng giờ kể cả khi app ở background / tắt màn hình:
  - Foreground service (kiểu `stopwatch`) + notification thời gian còn lại.
  - Alarm `setExactAndAllowWhileIdle()` tại thời điểm kết thúc (đếm dựa trên
    absolute time, không trôi giờ khi bị Doze).
  - Sống sót qua reboot: tái lập alarm + service khi khởi động lại.
- Dừng / hủy bất cứ lúc nào; xin quyền `SCHEDULE_EXACT_ALARM` (Android 12+),
  `POST_NOTIFICATIONS` (Android 13+) và gợi ý loại trừ tối ưu pin.

## Quyền cần cấp khi dùng lần đầu

1. **Device Admin** — bắt buộc, app sẽ mở sẵn màn hình cấp quyền khi bạn nhấn
   "Bắt đầu" (quyền chỉ dùng để khóa màn hình).
2. **Notification + báo thức chính xác + loại trừ tối ưu pin** — app tự gợi ý
   mở đúng màn hình Settings tương ứng.

## Tải App

Mở tab [Releases](https://github.com/khahdihdz/LockTimer/releases) và tải file
`LockTimer-<version>.apk`.

## Build

```bash
./gradlew :app:assembleDebug     # APK debug
./gradlew :app:assembleRelease   # APK release (chưa ký)
```

## Ký APK & Release

APK release được ký bằng **keystore dùng chung của
[khahdihdz/apksign](https://github.com/khahdihdz/apksign)** (alias
`khahdihdz-release`):

- `.github/workflows/release.yml` gọi reusable workflow
  `khahdihdz/apksign/.github/workflows/android-shared-signing.yml@main` với
  `secrets: inherit`; workflow dùng chung tự decode/validate keystore → build →
  zipalign → `apksigner sign` → upload artifact `android-signed-apks`.
- 4 secret `ANDROID_SIGNING_*` được workflow
  "Sync Android signing to all repositories" của `khahdihdz/apksign`
  đồng bộ tự động hằng ngày sang mọi Android repo (gọi thủ công nhanh hơn:
  `gh workflow run sync-android-signing.yml -R khahdihdz/apksign`).

Quy trình ra bản release mới:

```bash
git tag v1.0.1
git push origin v1.0.1
# Workflow Release build + ký APK và tạo GitHub Release đính kèm LockTimer-v1.0.1.apk
```

## Cấu trúc

```
app/src/main/java/com/khahdihdz/locktimer/
├── MainActivity.kt           # Compose UI + xin quyền
├── TimerViewModel.kt         # Trạng thái đếm ngược + start/cancel
├── TimerRepository.kt        # Lưu thời điểm kết thúc (SharedPrefs)
├── AlarmScheduler.kt         # setExactAndAllowWhileIdle
├── CountdownService.kt       # Foreground service đếm ngược
├── CountdownFinisher.kt      # Khóa máy (logic dùng chung)
├── AlarmReceiver.kt          # khóa máy khi alarm đổ chuông
├── BootReceiver.kt           # tái lập hẹn giờ sau reboot
├── Notifier.kt               # notification
├── LockDeviceAdminReceiver.kt# Device Admin (force-lock)
└── TimeFormat.kt
```

## Nếu màn hình không khóa khi hết giờ (troubleshooting)

Từ v1.1.0, đường chính khóa máy là **foreground service tự khóa đúng thời
điểm kết thúc trong tiến trình của nó**; alarm chỉ là dự phòng. Nếu vẫn không
khóa được:

1. **Xiaomi/MIUI/Miui HyperOS, Oppo/ColorOS, Vivo/OriginOS**: mở
   *Cài đặt → Ứng dụng → LockTimer →* cho phép **Khởi chạy tự động (Autostart)**
   và **Chạy ở nền không hạn chế**. các ROM này hay tắt cả alarm lẫn service
   cho app không nằm trong danh sách white-list.
2. Kiểm tra đã bật **"Alarms & reminders" (Báo thức & lời nhắc)** cho LockTimer
   ở *Cài đặt → Ứng dụng → Quyền đặc biệt* (Android 12+).
   Cấp quyền này app mới đặt được alarm chính xác.
3. Cấp **loại trừ tối ưu pin** khi app yêu cầu (tên mục hiển thị khác nhau tùy
   máy). Nếu bỏ qua, vào *Cài đặt → Ứng dụng → LockTimer → Pin → Không hạn chế*.
4. Nếu vẫn lỗi: lấy log thiết bị, lọc tag `LockTimer` để xem đường kích hoạt nào bắn:

```bash
adb logcat -d -s LockTimer | tail -50
```

Chuỗi log bình thường: `scheduleLockAlarm ...` → `onStartCommand action=START`
→ `handleStart ...` → `lockRunnable: locked at scheduled time` (dự phòng:
`onEnableFailed` không xuất hiện; nếu chỉ thấy `finishNow()` mà admin
active=false → app cần cấp lại quyền Device Admin).

## License

MIT
