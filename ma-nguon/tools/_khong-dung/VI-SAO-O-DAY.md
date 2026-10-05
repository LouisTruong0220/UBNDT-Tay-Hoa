# Ba công cụ của kiến trúc cũ — giữ lại để tra, không còn chạy

Anh Trường chốt **18/09/2026**: app chỉ dùng dữ liệu và nguồn thông tin của
`thutuc.hanhchinhso.ai.vn`. Kho 165 thủ tục niêm yết Quảng Ninh bị bỏ hẳn, kéo theo
cả tầng tra cứu tại chỗ và tầng dẫn đường tới quầy.

Ba file dưới đây canh đúng những tầng đó, nên **không còn chạy được** — nhưng không
xoá, vì chúng ghi lại cách giải mấy bài toán có thể quay lại bất cứ lúc nào.

| File | Canh cái gì | Vì sao dừng |
|---|---|---|
| `thu-tim-kiem.mjs` | 40 phép thử cho bộ tìm kiếm tại chỗ: ngưỡng tin cậy tuyệt đối, cụm hai từ, bẫy bỏ dấu (`đâu`→`dau` trùng `đau`) | Bộ tìm kiếm đó tra kho 165 thủ tục — kho không còn |
| `thu-luong-dan-duong.mjs` | Luồng dẫn khách tới quầy, xác nhận trước khi đi | Bảng phân quầy nằm trong kho cũ; quầy thật của Tây Hòa chưa ai khảo sát |
| `nap-truoc.py` | Nạp sẵn câu trả lời của nguồn theo danh sách viết tay | Đã thay bằng `tools/tai-nguon.py`, lấy cả menu lẫn nội dung từ chính máy chủ của họ |

## Muốn bật lại tầng tra cứu tại chỗ

Cần đúng hai thứ, và thứ nhất là việc của khách chứ không phải việc của mã:

1. **Niêm yết TTHC của Đắk Lắk** — kho hiện tại là của Quảng Ninh, sai từ số quầy,
   lệ phí tới mã QR. Dùng lại mà không thay dữ liệu là robot nói sai có căn cứ.
2. **Sơ đồ quầy thật của Tây Hòa** — mấy quầy, quầy nào nhận việc gì, tên điểm trên
   bản đồ robot đặt ra sao.

Có đủ hai thứ đó thì ba file này chạy lại được gần như nguyên vẹn: đổi đường dẫn dữ
liệu, chạy `node thu-tim-kiem.mjs` rồi sửa cho tới khi xanh.
