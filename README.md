# Trợ lý Hành chính công — Robot GreetingBot Nova

Phần mềm robot hướng dẫn thủ tục tại **Trung tâm Phục vụ Hành chính công xã Tây Hòa, tỉnh Đắk Lắk**.
Do Công ty Cổ phần Tập đoàn Roboworld phát triển.

| | |
|---|---|
| Phiên bản | **1.1** (mã 2) — ngày 22/09/2026 |
| Tệp cài | [`tro-ly-hanh-chinh-tay-hoa-v1.1.apk`](tro-ly-hanh-chinh-tay-hoa-v1.1.apk) |
| Tên gói | `vn.roboworld.hcc` |
| Máy | GreetingBot Nova (OrionStar), Android 9 |

---

## Cài đặt

**Cần:** máy tính Windows có `adb` bản **1.0.41** (bộ *platform-tools* của Android SDK) và cáp USB.

> ⚠ Không dùng `adb` bản 1.0.39 hay có sẵn ở `C:\Windows\adb.exe` — lệch phiên bản, robot rớt kết nối liên tục.
> Kiểm bằng lệnh `adb version`.

1. Trên robot: bật **Gỡ lỗi lâu dài** trong phần cài đặt, rồi **khởi động lại robot**.
2. Cắm cáp USB vào cổng ở **phần đầu** robot (không phải thân máy).
3. Mở cửa sổ lệnh ở thư mục chứa tệp APK, chạy:

   ```
   adb devices
   adb install -r tro-ly-hanh-chinh-tay-hoa-v1.1.apk
   ```

   `adb devices` phải hiện đúng một dòng có chữ `device`. Cài xong sẽ hiện `Success`.

4. **Mở app BẰNG TAY từ màn hình chính của robot.**

> ⚠⚠ **Đừng mở app bằng lệnh `adb shell am start`.** Mở theo đường đó, app lên hình bình thường nhưng
> **mất giọng nói và mất dẫn đường** — nhìn từ ngoài giống hệt app bị lỗi.

---

## Sau khi cài — kiểm nhanh

| Kiểm | Đạt khi |
|---|---|
| Màn chính | hiện **9 nhóm việc**: Khai sinh · Kết hôn · Hộ tịch khác · Cư trú · Chứng thực · Đất đai · Kinh doanh · Chính sách · Khiếu nại |
| Bấm một nhóm, chọn một việc | nội dung hiện ngay, robot đọc tóm tắt |
| Bấm nút micro rồi nói | robot nghe và trả lời — **người nói đứng đối diện robot** |

**Bấm micro mà robot không nghe?** Micro của máy có thể đang bị tắt từ trước. Chạy:

```
adb shell settings put global microphone 1
```

rồi tắt và mở lại app từ màn hình chính.

> Robot dùng camera để biết câu nói có hướng vào nó không. Đứng lệch sang bên hoặc nói vọng từ xa
> thì câu nói bị coi là tiếng ồn và bỏ qua. Nên dặn người dân **đứng trước mặt robot khi nói**.

---

## Robot làm được gì

- **Tra cứu 37 việc** thuộc 9 nhóm, nội dung nạp sẵn trong máy — **trả lời tức thì, không cần mạng**.
- **Hỏi bằng giọng nói** — bấm nút micro, nói xong robot tự tắt micro và trả lời.
- Nội dung thủ tục lấy từ một nguồn duy nhất và **giữ nguyên văn**, kèm danh mục căn cứ pháp lý.
- **Câu đời thường** ngoài phạm vi thủ tục (thời tiết, đường đi…) có thể tra trên Internet — xem mục dưới.

## Robot CHƯA làm được gì

- Lệ phí từng thủ tục, file mẫu đơn, phân công quầy — robot nói thẳng là chưa có, mời hỏi quầy.
- Dẫn đường tới quầy — **đang tắt**, chờ sơ đồ quầy của Trung tâm.

Robot được thiết kế để **thà nói "tôi chưa rõ" còn hơn trả lời một thủ tục gần đúng**.

---

## Tra cứu trên Internet cho câu đời thường

Mặc định **TẮT**. Chỉ bật khi đặt một tệp khoá lên thẻ nhớ robot tại:

```
/sdcard/Android/data/vn.roboworld.hcc/files/cau-hinh/tra-mang.txt
```

Dòng đầu tệp là khoá dịch vụ tra cứu. **Khoá không bao giờ nằm trong APK hay trong kho này** —
đừng tải tệp khoá lên đây.

Khi đã bật, robot chỉ tra mạng cho câu hỏi đời thường. Câu hỏi thuộc **thủ tục, pháp luật** vẫn chỉ
trả lời bằng dữ liệu đã nạp; câu hỏi **chính trị** robot từ chối. Câu trả lời từ mạng hiện rõ
**"Thông tin tra trên Internet — chỉ để tham khảo"** kèm nguồn, và robot **không đọc câu nào không
kèm nguồn tra cứu**.

---

## Nhật ký phiên bản

### 1.1 — 22/09/2026
- Mở rộng từ 5 lên **9 nhóm việc · 37 việc**, nạp sẵn đủ 37/37
- Thêm nhóm **Kết hôn**, **Kinh doanh** (hộ kinh doanh cá thể), **Chính sách** (hộ nghèo, trợ cấp,
  người có công, mai táng phí, trẻ mồ côi), **Khiếu nại**
- Sửa: bấm lựa chọn nhiều trường hợp (ví dụ các trường hợp kết hôn) mà không ra nội dung
- Sửa: ký hiệu `##` và `>` hiện nguyên trên màn hình kết quả
- Chặn trợ lý của nền tảng tự trả lời bằng kho kiến thức dùng chung — robot chỉ nói dữ liệu của chính nó
- Thêm tầng tra cứu Internet cho câu đời thường (mặc định tắt)
- Bỏ dải "bản xem trước" khỏi màn hình

### 1.0 — 18/09/2026
- Bản chạy máy thật đầu tiên

---

**Liên hệ:** Công ty Cổ phần Tập đoàn Roboworld · Hotline **0866 153 946** · roboworld.com.vn
