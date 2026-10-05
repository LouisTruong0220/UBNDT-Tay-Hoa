# Video biểu cảm robot — nguồn và cách dùng

| | |
|---|---|
| **Nguồn** | `D:\dl\emoji-Robot.zip` — anh Trường gửi 10/08/2026 |
| **Xuất xứ** | Bộ mặt cảm xúc gốc của robot OrionStar (Nova) |
| **Số file** | 10 clip MP4 + 1 ảnh tĩnh |
| **Khuôn hình** | 1280×800 · H.264 · 30 hình/giây · **không có tiếng** |
| **Dung lượng** | 2,94 MB cả bộ |

| File | Dài | Nội dung |
|---|---:|---|
| `emoji_happy.mp4` | 3,0 s | Mặt cười — **clip mở màn**, luôn phát đầu tiên |
| `emoji_blink.mp4` | 5,0 s | Chớp mắt |
| `emoji_wink_1.mp4` | 3,7 s | Nháy mắt kiểu 1 |
| `emoji_wink_2.mp4` | 2,5 s | Nháy mắt kiểu 2 (có hoa) |
| `emoji_wink_3.mp4` | 2,5 s | Nháy mắt kiểu 3 |
| `emoji_laugh.mp4` | 2,2 s | Cười lớn |
| `emoji_impatient.mp4` | 2,0 s | Sốt ruột |
| `emoji_dizzy.mp4` | 2,2 s | Chóng mặt |
| `emoji_angry.mp4` | 3,0 s | Cáu |
| `emoji_fallasleep.mp4` | 11,0 s | Ngủ gật |
| `emoji_default.png` | — | Ảnh tĩnh, dùng làm `poster` lúc video chưa tải xong |

## Clip Roboworld tự dựng

| File | Dài | Nội dung |
|---|---:|---|
| `emoji_hello.mp4` | 4,0 s | **Chào mời** — đưa mắt tìm người → chớp mắt → cười tươi miệng mở, hoa bung → nháy mắt → mắt to nhìn thẳng, con ngươi nhấp hai nhịp mời chạm vào |

Dựng bằng `tools/bieu-cam/` (không phải file của hãng):

```powershell
cd D:\RBW_Claude\tools\bieu-cam
node ghi-hinh.mjs emoji_hello.html emoji_hello.mp4
```

`emoji_hello.html` vẽ khuôn mặt bằng SVG và có hàm `render(t)`; `ghi-hinh.mjs` chụp lần lượt
120 khung rồi mới ghép — **không** ghi theo thời gian thật như `tools/render-video.mjs`, nên máy
bận cũng không rớt khung. Mở thẳng file HTML trong Chrome thì nó tự chạy vòng lặp để xem trước.

Ba điều đã học khi dựng, cần biết nếu sửa tiếp:

- **Toạ độ đo từ `emoji_wink_1` phải bù trừ.** Khung 55 của clip đó có cả khuôn mặt đang nảy,
  lệch `(-21, -229)` so với hệ chuẩn của `emoji_blink`. Lấy nguyên số đo là mắt cười và chân mày
  treo lơ lửng giữa khung.
- **Không tô màu coral bằng `opacity` thấp.** Trên nền đen nó ra màu nâu bẩn. Muốn hiện dần thì
  phóng to dần từ 0 (`scale`), giữ màu nguyên độ.
- **Hoa không đặt cạnh chân mày như bản gốc được** nếu clip có cả trạng thái mắt mở tròn —
  mắt r=213 sẽ trùm lên chỗ đặt hoa. Để hoa ở hai góc trên (`y ≈ 70–130`), chỗ đó trống ở mọi
  trạng thái. Miệng cũng chỉ nên có hai trạng thái mũi-đóng / mở-hẳn: nửa mở ra hình hộp nhỏ
  có vệt hồng, trông như lỗi.

## Vì sao để rời, không nhúng base64 vào HTML

2,94 MB nhị phân nở thành ~4 MB chữ khi mã hoá base64, và WebView phải phân tích hết cả
file HTML trước khi vẽ được gì. Để rời cạnh `index.html` thì đường dẫn tương đối
`bieu-cam/…` chạy giống nhau ở cả `file://` trên máy tính lẫn `file:///android_asset/`
trên robot.

`dung-app.py` tự copy thư mục này sang `demo/bieu-cam/` và
`android/app/src/main/assets/bieu-cam/` mỗi lần dựng — không phải copy tay.

## Thêm hoặc bớt biểu cảm

1. Bỏ file `.mp4` mới vào thư mục này (1280×800 hoặc tỉ lệ khác đều được — CSS dùng
   `object-fit: contain` trên nền đen nên không méo, chỉ có viền đen hai bên).
2. Thêm tên file (không có đuôi) vào mảng `BIEU_CAM_CHO` trong `khung-app.html`.
   Muốn dùng lúc dẫn đường thì thêm vào `BIEU_CAM_DAN`.
3. Chạy `python dung-app.py` rồi dựng lại APK.

Phần tử **đầu tiên** của mảng là clip mở màn — để mặt cười ở đó, đừng để mặt cáu.
