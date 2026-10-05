# Nhạc nhảy múa

`mua.mp3` — bài anh Trường gửi ngày 24/09/2026 (`C:\Users\ADMIN\Downloads\nhạc.MP3`), 35,0 giây,
chép nguyên bài, chỉ mã hoá lại 160 kbps stereo. **Chưa rõ tác giả và giấy phép** — anh Trường chọn
bài; trước khi đưa robot ra sự kiện công khai có quay phim thì nên xác nhận quyền dùng.

Đo bằng `python tools/do-nhip-nhac.py nhac/mua.mp3`: **~129,2 BPM · độ rõ nhịp 0,75 · 76 phách**,
khoảng cách phách 462 ms (lệch chuẩn 11 ms). Bảng phách ghi thẳng vào khung-app.html (khối VU_DAO) —
**đổi bài thì phải chạy lại script**, không thì robot múa theo nhịp của bài cũ.
Kiểm điệu múa: `node tools/thu-vu-dao.mjs` (mỗi ô nhịp tổng góc = 0, lệnh xoay không đè nhau).

Bài trước đó (Liborio Conti, giấy phép CC, 60 giây, ~122 BPM) đã thay.
