# -*- coding: utf-8 -*-
"""
Dựng màn THÔNG TIN (cán bộ Trung tâm) từ tư liệu Trung tâm gửi — 29/09/2026.

Nguồn (bản gốc lưu ở 10-project/Dang-Khoa-TP/tu-lieu/can-bo-tthcc-tay-hoa/):
  · Thông báo số 16/TB-PVHCC ngày 03/08/2026 — danh sách công khai 10 cán bộ, công chức
  · 5 ảnh chân dung, tên tệp "<Họ tên> Quầy NN.jpg" — số quầy lấy từ tên tệp

Ra: thong-tin/thong-tin.json + thong-tin/can-bo/*.jpg (ảnh thu về 480×640, tên không dấu).
dung-app.py rải thư mục thong-tin/ vào assets của APK; bản trên thẻ nhớ robot vẫn được ưu tiên.

⚠ CỐ Ý KHÔNG đưa số điện thoại lên màn hình: thông báo có ghi, nhưng đó là số di động cá nhân,
  và APK được phát công khai trên GitHub. Cần hiện thì thêm trường "sdt" và sửa lớp web.
⚠ Chữ mô tả là TÓM TẮT cột "Chuyên môn và Lĩnh vực" — không thêm ý nào ngoài thông báo.
"""
import io, json, os, sys, unicodedata
from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NGUON_ANH = os.path.join(HERE, "..", "..", "10-project", "Dang-Khoa-TP", "tu-lieu", "can-bo-tthcc-tay-hoa", "anh")
RA = os.path.join(HERE, "thong-tin")

# Thứ tự, họ tên, giới tính, chức vụ đúng như Thông báo 16/TB-PVHCC. "quay" lấy từ tên tệp ảnh.
CAN_BO = [
    ("Võ Thị Hạnh", "Nữ",        "Giám đốc Trung tâm",     None, "Quản lý chung hoạt động của Trung tâm Phục vụ hành chính công xã."),
    ("Lê Hồng Phong", "Nam",      "Phó Giám đốc Trung tâm", None, "Tham mưu Giám đốc quản lý chung hoạt động của Trung tâm · ký hồ sơ lĩnh vực Chứng thực."),
    ("Lê Thị Đỗ Quyên", "Nữ",    "Phó Giám đốc Trung tâm", None, "Tham mưu Giám đốc quản lý chung hoạt động của Trung tâm · ký hồ sơ lĩnh vực Chứng thực."),
    ("Võ Tấn Đạt", "Nam",         "Chuyên viên",            None, "Phụ trách kiểm soát thủ tục hành chính · theo dõi niêm yết công khai thủ tục và việc giải quyết thủ tục trên địa bàn xã."),
    ("Trần Thị Bích Tiền", "Nữ", "Chuyên viên",            None, "Thực hiện công tác kế toán Trung tâm · hướng dẫn công dân nộp phí, lệ phí thủ tục hành chính."),
    ("Trần Thị Hồng", "Nữ",      "Chuyên viên",            "05", "Tiếp nhận hồ sơ lĩnh vực của Phòng Văn hóa – Xã hội."),
    ("Lê Kim Đính", "Nam",        "Chuyên viên",            "01", "Tiếp nhận, xử lý hồ sơ lĩnh vực Hộ tịch."),
    ("Nguyễn Trần Hoài Mơ", "Nữ", "Chuyên viên",           "03", "Tiếp nhận, xử lý hồ sơ lĩnh vực Chứng thực."),
    ("Nguyễn Ngọc Tính", "Nam",   "Chuyên viên",            "04", "Tiếp nhận, xử lý hồ sơ lĩnh vực Chứng thực."),
    # Tệp ảnh ghi "Quầy 01" nhưng anh Trường xác nhận 29/09/2026: anh Tiếng ngồi QUẦY 02.
    ("Trần Minh Tiếng", "Nam",    "Chuyên viên",            "02", "Tiếp nhận, xử lý hồ sơ kinh doanh, công thương, đất đai, xây dựng, quy hoạch, khoáng sản… (lĩnh vực Phòng Kinh tế)."),
]


def khong_dau(s):
    s = unicodedata.normalize("NFD", s).replace("đ", "d").replace("Đ", "D")
    s = "".join(c for c in s if unicodedata.category(c) != "Mn").lower()
    return "-".join("".join(c if c.isalnum() else " " for c in s).split())


def tim_anh(ten):
    """Tệp ảnh có tên bắt đầu bằng họ tên (so khi đã bỏ dấu — tránh lệch NFC/NFD tên tệp)."""
    if not os.path.isdir(NGUON_ANH):
        return None
    for f in os.listdir(NGUON_ANH):
        if khong_dau(f).startswith(khong_dau(ten) + "-"):
            return os.path.join(NGUON_ANH, f)
    return None


os.makedirs(os.path.join(RA, "can-bo"), exist_ok=True)
ds, co_anh = [], 0
SO_CHU = {"01": "một", "02": "hai", "03": "ba", "04": "bốn", "05": "năm", "06": "sáu", "07": "bảy", "08": "tám", "09": "chín"}


def cau_doc(ten, gt, cv, quay, mo_ta):
    """Câu robot đọc khi người dân bấm vào thẻ. Số quầy đọc thành chữ ("quầy số năm", không
    phải "quầy không năm"); bỏ ký hiệu · … – ( ) để giọng máy ngắt hơi cho tự nhiên."""
    xung = "chị" if gt == "Nữ" else "anh"
    goi = ten.split()[-1]
    c = "Đây là %s %s, %s" % (xung, ten, cv[0].lower() + cv[1:])
    if quay:
        c += ", làm việc tại quầy số %s" % SO_CHU.get(quay, quay)
    m = mo_ta.replace(" · ", ", ").replace("…", "").replace(" – ", ", ").replace(" (", ", ").replace(")", "")
    return c + ". %s %s %s" % (xung.capitalize(), goi, m[0].lower() + m[1:])


for ten, gt, cv, quay, mo_ta in CAN_BO:
    muc = {"ten": ten, "gioi_tinh": gt, "chuc_vu": cv + (" · Quầy %s" % quay if quay else ""), "mo_ta": mo_ta,
           "doc": cau_doc(ten, gt, cv, quay, mo_ta)}
    if quay:
        muc["quay"] = int(quay)      # lớp web xếp thẻ theo số quầy (anh Trường yêu cầu 29/09/2026)
    f = tim_anh(ten)
    if f:
        ra = "can-bo/%s.jpg" % khong_dau(ten)
        im = Image.open(f).convert("RGB")
        im.thumbnail((480, 640), Image.LANCZOS)
        im.save(os.path.join(RA, ra), "JPEG", quality=86, optimize=True)
        muc["anh"] = ra
        co_anh += 1
    ds.append(muc)
    print("%s %-22s %s" % ("✓" if f else "·", ten, muc["chuc_vu"]))

T = {
    "_nguon": "Thông báo số 16/TB-PVHCC ngày 03/08/2026 của TT PVHCC xã Tây Hòa + ảnh Trung tâm gửi 29/09/2026. "
              "Sinh bằng tools/dung-thong-tin.py — đừng sửa tay.",
    "ten_don_vi": "Trung tâm Phục vụ Hành chính công xã Tây Hòa",
    "can_bo": ds,
    "gioi_thieu": {},
}
io.open(os.path.join(RA, "thong-tin.json"), "w", encoding="utf-8").write(json.dumps(T, ensure_ascii=False, indent=2))
print("→ %s · %d cán bộ · %d có ảnh" % (RA, len(ds), co_anh))
if co_anh < len(ds):
    print("  (chưa có ảnh: %s)" % ", ".join(m["ten"] for m in ds if "anh" not in m))
