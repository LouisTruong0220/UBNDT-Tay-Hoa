# Trợ lý Hành chính công — Robot GreetingBot Nova

Phần mềm robot hướng dẫn thủ tục tại **Trung tâm Phục vụ Hành chính công xã Tây Hòa, tỉnh Đắk Lắk**.
Do Công ty Cổ phần Tập đoàn Roboworld phát triển.

| | |
|---|---|
| Phiên bản | **1.4** (mã 5) — ngày 24/09/2026 |
| Tệp cài | [`tro-ly-hanh-chinh-tay-hoa-v1.4.apk`](tro-ly-hanh-chinh-tay-hoa-v1.4.apk) |
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
   adb install -r tro-ly-hanh-chinh-tay-hoa-v1.4.apk
   ```

   `adb devices` phải hiện đúng một dòng có chữ `device`. Cài xong sẽ hiện `Success`.

4. **Mở app BẰNG TAY từ màn hình chính của robot.**

> ⚠⚠ **Đừng mở app bằng lệnh `adb shell am start`.** Mở theo đường đó, app lên hình bình thường nhưng
> **mất giọng nói và mất dẫn đường** — nhìn từ ngoài giống hệt app bị lỗi.

---

## Sau khi cài — kiểm nhanh

| Kiểm | Đạt khi |
|---|---|
| Màn chờ | mặt robot + nút vàng **"Chạm vào màn hình để bắt đầu"** + dòng *hoặc nói "Xin chào"* |
| Chạm màn hình | robot chào một câu, mở **màn chính 5 ô**: Tra cứu thủ tục · Giao tiếp AI · Dẫn đường · Giải trí · Thông tin |
| Tra cứu thủ tục | hiện **9 nhóm việc**; chọn một việc → nội dung hiện ngay, robot đọc tóm tắt |
| Giao tiếp AI → bấm micro rồi nói | robot nghe và trả lời — **người nói đứng đối diện robot** |
| Đứng trước robot, nói "Xin chào" (ở màn chờ) | robot chào lại và mở màn chính |

**Bấm micro mà robot không nghe?** Micro của máy có thể đang bị tắt từ trước. Chạy:

```
adb shell settings put global microphone 1
```

rồi tắt và mở lại app từ màn hình chính.

> Robot dùng camera để biết câu nói có hướng vào nó không. Đứng lệch sang bên hoặc nói vọng từ xa
> thì câu nói bị coi là tiếng ồn và bỏ qua. Nên dặn người dân **đứng trước mặt robot khi nói**.

---

## Robot làm được gì

| Chức năng | Mô tả |
|---|---|
| **Tra cứu thủ tục** | 37 việc thuộc 9 nhóm, nạp sẵn trong máy — **trả lời tức thì, không cần mạng**. Chỉ chạm, không có ô hỏi AI. Nội dung giữ **nguyên văn** từ một nguồn duy nhất, kèm căn cứ pháp lý |
| **Giao tiếp AI** | Bấm micro rồi nói. Robot hiểu cả khi người dân kể hoàn cảnh (*"Bố tôi vừa mất thì làm giấy tờ gì"*) và đọc đúng thủ tục đã nạp |
| **Dẫn đường** | **Chưa cài đặt** — màn hình và robot nói thẳng là đang cài đặt, mời hỏi cán bộ |
| **Giải trí** | **Robot nhảy múa** khoảng 35 giây theo nhạc: lắc thân, gật đầu, đổi nét mặt khớp phách. **Đố vui thủ tục** 5 câu, câu hỏi sinh từ chính dữ liệu thủ tục đã nạp |
| **Thông tin** | Cán bộ Trung tâm + hình ảnh, video giới thiệu — xem mục *Cập nhật thông tin Trung tâm* |
| **"Xin chào"** | Ở màn chờ, người đứng trước robot nói "Xin chào" → robot chào lại và mở màn chính |
| **Quay về phía người dùng** | Rời màn chờ là robot xoay thân đối diện người đang đứng trước màn hình (trong khoảng 2 m) |

⚠ **Robot có xoay thân** (lúc quay về phía người dùng và lúc nhảy múa). Để trống quanh robot
khoảng **1 m**. Lúc đang nhảy, **chạm vào bất kỳ đâu trên màn hình là robot dừng**.

## Robot CHƯA làm được gì

- Lệ phí từng thủ tục, file mẫu đơn, phân công quầy — robot nói thẳng là chưa có, mời hỏi quầy.
- Dẫn đường tới quầy — **chưa cài đặt**, chờ sơ đồ quầy của Trung tâm.
- Thông tin cán bộ, ảnh và video Trung tâm — **chưa có**, màn hình hiện "Đang cập nhật" cho tới khi
  Trung tâm gửi tư liệu.

Robot được thiết kế để **thà nói "tôi chưa rõ" còn hơn trả lời một thủ tục gần đúng**.

---

## Cập nhật thông tin Trung tâm

Màn **Thông tin** đọc một tệp trên thẻ nhớ robot — **không phải cài lại app**. Trên máy tính, tạo
thư mục `thong-tin` gồm tệp `thong-tin.json` và ảnh/video:

```
thong-tin/
  thong-tin.json
  can-bo/nguyen-van-a.jpg        ← ảnh cán bộ, nên ảnh vuông hoặc dọc
  trung-tam/anh-1.jpg
  trung-tam/video-gioi-thieu.mp4 ← video phải mã hoá H.264
```

Khuôn `thong-tin.json` (thay phần trong ngoặc bằng thông tin thật):

```json
{
  "ten_don_vi": "Trung tâm Phục vụ Hành chính công xã Tây Hòa",
  "can_bo": [
    { "ten": "(Họ và tên)", "chuc_vu": "(Chức vụ)", "mo_ta": "(Phụ trách việc gì, quầy số mấy)",
      "anh": "can-bo/ten-tep-anh.jpg" }
  ],
  "gioi_thieu": {
    "tieu_de": "(Tiêu đề)",
    "doan": ["(Đoạn giới thiệu thứ nhất)", "(Đoạn thứ hai)"],
    "media": [ { "loai": "anh", "tep": "trung-tam/anh-1.jpg" },
               { "loai": "video", "tep": "trung-tam/video-gioi-thieu.mp4" } ]
  }
}
```

Đẩy sang robot rồi **tắt và mở lại app từ màn hình chính**:

```
adb push thong-tin /sdcard/Android/data/vn.roboworld.hcc/files/
```

Tệp sai cú pháp thì màn Thông tin vẫn hiện "Đang cập nhật" — không làm hỏng app.

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

### 1.4 — 24/09/2026 · đã chạy thử trên robot thật
- **Màn chính mới — 5 chức năng:** Tra cứu thủ tục · Giao tiếp AI · Dẫn đường · Giải trí · Thông tin,
  kèm nút **"Về màn chờ"**. Màn Tra cứu chỉ chạm, không còn ô hỏi AI và nút micro.
- **"Xin chào"** ở màn chờ đánh thức robot: robot chào lại trong chưa đầy nửa giây và mở màn chính.
  Chạm màn hình rời màn chờ cũng có một câu chào.
- **Quay về phía người dùng:** rời màn chờ là robot xoay thân đối diện người đứng trước màn hình.
- **Giải trí:** robot nhảy múa theo nhạc — động tác và nét mặt khớp phách (đo nhịp ~129 BPM từ chính
  bài nhạc); đố vui thủ tục 5 câu, câu hỏi lấy từ dữ liệu đã nạp.
- **Thông tin:** cán bộ Trung tâm và ảnh/video giới thiệu, cập nhật bằng tệp trên thẻ nhớ.
- **Dẫn đường:** có ô trên màn chính, hiện "đang cài đặt" — chờ sơ đồ quầy.
- Sửa: nút ▶ xám hiện chồng lên mặt robot mỗi lần đổi nét mặt.


### 1.3 — 24/09/2026 · đã thay bằng 1.4 (lưu ở `ban-cu/`)
- **Hiểu câu nói tự nhiên:** người dân kể hoàn cảnh thay vì gọi tên thủ tục (*"Bố tôi vừa mất thì
  làm giấy tờ gì"*, *"Tôi muốn chia mảnh đất cho hai đứa con"*) — robot nhận ra đúng thủ tục
  (khai tử, tách thửa) và đọc nguyên văn dữ liệu đã nạp. Thử 7/7 câu đúng, mỗi câu dưới 2 giây.
  Trí tuệ nhân tạo chỉ chọn thủ tục, **không viết một chữ nội dung nào**.
- Câu ngoài phạm vi (thời tiết…) và câu hỏi việc nội bộ Trung tâm (giờ mở cửa, cán bộ trực) trả
  lời trong **dưới 2 giây** (trước: 8–12 giây)
- Khi phải tra thêm lâu, robot nói *"anh chị chờ tôi tra thêm một chút"* sau 2 giây thay vì đứng im
- Mất mạng: câu ngoài dữ liệu báo "chưa có" sau khoảng 4 giây (trước: 25 giây)

### 1.2 — 22/09/2026 · đã thay bằng 1.3 (lưu ở `ban-cu/`)
- **Sửa lỗi nghiêm trọng của bản 1.1:** robot tự cắt tiếng của chính nó ở các câu hỏi lại
  ("Cần làm rõ…") — 6 lần trong 2 phút khi thử trên máy. **Đừng dùng bản 1.1.**
- Hiểu câu nói lệch chữ: *"chấm dứt hoạt động kinh doanh"* nay khớp đúng thủ tục
  *"chấm dứt hoạt động hộ kinh doanh"*, trả lời trong 1 giây (trước: chờ 10 giây rồi báo chưa có)
- Câu hỏi lại lấy từ dữ liệu trong máy nay hiện **nút bấm**, và robot không còn đọc ký hiệu ra loa
- Kiểm trên robot: 9/9 loại câu đi đúng hướng · tắt Wi-Fi vẫn trả lời câu đã nạp · đặt giữa phòng
  ồn 60 giây robot không tự nói câu nào

### 1.1 — 22/09/2026 · ⚠ có lỗi tự cắt tiếng, đã thay bằng 1.2 (lưu ở `ban-cu/`)
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
