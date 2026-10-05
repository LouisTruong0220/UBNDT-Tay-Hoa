# Mã nguồn — Trợ lý Hành chính công xã Tây Hòa (GreetingBot Nova)

Phiên bản mã nguồn: **1.6.1** (versionCode 8) — mới hơn tệp APK 1.5 ở thư mục gốc kho.

## Cấu trúc

| Đường dẫn | Là gì |
|---|---|
| `khung-app.html` | Toàn bộ giao diện (WebView). **Sửa giao diện ở đây.** |
| `du-lieu/` | Dữ liệu thủ tục lấy từ thutuc.hanhchinhso.ai.vn + cấu hình nguồn |
| `dung-du-lieu.py` | Dựng `du-lieu/app-data.json` từ dữ liệu nguồn |
| `dung-app.py` | Ghép `khung-app.html` + dữ liệu → `android/app/src/main/assets/` (và bản `demo/` chạy trên máy tính) |
| `android/` | Dự án Android (Kotlin). Gói `vn.roboworld.hcc` |
| `android/app/libs/` | SDK OrionStar: `robotservice.jar` (RobotApi) · `sdk-0.4.7.aar` + `agent-base-0.2.10.aar` (Agent SDK, gói offline của hãng) |
| `bieu-cam/` · `thong-tin/` · `nhac/` | Video biểu cảm, trang Thông tin, nhạc nhảy múa |
| `gia-lap-robot.js` | Giả lập đối tượng `CAU` để thử giao diện trên máy tính, không cần robot |
| `tools/` | Tải nguồn, build APK, đẩy giao diện lên robot, bộ thử tự động |

## Dựng lại APK

Cần: JDK 17, Android SDK (platform 34), Python 3, Node.js (cho bộ thử).

```
python tools/tai-nguon.py        # (tuỳ chọn) tải lại dữ liệu nguồn
python dung-du-lieu.py
python dung-app.py
powershell -File tools/build-apk.ps1
```

Bộ build: Gradle 8.7 · AGP 8.5.2 · Kotlin 1.9.24. `tools/build-apk.ps1` tự ghi `android/local.properties`.

## Lưu ý

- **Không có khoá API nào trong kho.** Khoá tra mạng (nếu dùng) đặt trong tệp trên thẻ nhớ robot, không đưa vào mã.
- Đổi giao diện/dữ liệu **không cần build lại APK**: app đọc `files/web/` trên thẻ nhớ trước — dùng `python tools/day-web.py`.
- Mở app **từ màn hình chính RobotOS**, không mở bằng `adb shell am start` (mất giọng nói và dẫn đường).
- Agent SDK phải dùng gói offline 0.4.7 có sẵn trong `libs/`, **không** lấy bản JitPack.
