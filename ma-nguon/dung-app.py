# -*- coding: utf-8 -*-
"""Ghép khung-app.html + du-lieu/app-data.json thành demo/index.html tự chứa,
rồi rải sang chỗ Android build APK.  (App Trợ lý Hành chính công — GreetingBot Nova)

Vì sao phải nhúng dữ liệu vào thẳng file HTML: Chromium chặn fetch() qua giao thức
file://, nên app mở bằng cách nháy đúp sẽ không tải được file JSON rời.
Nhúng vào rồi thì nháy đúp là chạy, không cần máy chủ web.

Vì sao script tự copy sang android/app/src/main/assets/: trước đây phải copy tay,
và LẦN NÀO CŨNG QUÊN — sửa giao diện xong build APK ra vẫn là bản cũ, ngồi soi
nửa buổi không hiểu vì sao. Video biểu cảm cũng đi theo đường này.

Video KHÔNG nhúng base64 vào HTML: 2,9 MB nhị phân nở thành ~4 MB chữ, WebView
phải phân tích cả file trước khi vẽ được gì. Để rời cạnh index.html, đường dẫn
tương đối chạy giống nhau ở cả file:// trên máy tính lẫn file:///android_asset/.
"""
import io, os, sys, shutil

# Windows mặc định cp1252 -> print tiếng Việt là lỗi charmap. Ép UTF-8 cho stdout.
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
KHUNG = os.path.join(HERE, "khung-app.html")
DULIEU = os.path.join(HERE, "du-lieu", "app-data.json")
BIEUCAM = os.path.join(HERE, "bieu-cam")
DEMO = os.path.join(HERE, "demo")
ASSETS = os.path.join(HERE, "android", "app", "src", "main", "assets")
RA = os.path.join(DEMO, "index.html")
os.makedirs(DEMO, exist_ok=True)

khung = io.open(KHUNG, encoding="utf-8").read()
data = io.open(DULIEU, encoding="utf-8").read()

MOC = "/*__DU_LIEU__*/"
if MOC not in khung:
    raise SystemExit("Không tìm thấy mốc %s trong khung-app.html" % MOC)

# </script> nằm trong chuỗi JSON sẽ cắt sớm thẻ script — chèn dấu thoát
data = data.replace("</", "<\\/")

html = khung.replace(MOC, "window.DU_LIEU=" + data + ";")
with io.open(RA, "w", encoding="utf-8") as f:
    f.write(html)

# ── Bản THỬ có giả lập robot ─────────────────────────────────────────────
#
# Sinh cùng lúc với bản thường, cố ý. Bản demo không có đối tượng CAU nên app tự
# ẩn nút dẫn đường — nghĩa là ba màn quan trọng nhất (đang dẫn · chỉ đường · robot
# tự về sảnh) không thử được trên máy tính. Bản này nhét gia-lap-robot.js vào để
# dựng lại CAU và trình tự báo trạng thái của Cau.kt.
#
# Nhét NGAY SAU khối dữ liệu, TRƯỚC mã chính: app kiểm coDanDuong() và coMicRobot()
# ngay lúc dựng màn hình, giả lập tới muộn thì nút vẫn ẩn.
#
# ⚠ Bản thử KHÔNG rải sang android assets. APK phải là mã thật, không mang giả lập.
GIALAP = os.path.join(HERE, "gia-lap-robot.js")
RA_THU = os.path.join(DEMO, "thu-nghiem.html")
if os.path.isfile(GIALAP):
    gl = io.open(GIALAP, encoding="utf-8").read()
    # Mốc chèn: ngay sau thẻ <script> chứa dữ liệu, trước thẻ <script> mã chính.
    # ⚠ Nhận CẢ HAI kiểu xuống dòng. Khung soạn trên Windows là CRLF, khung sinh ra
    # từ script là LF — neo cứng một kiểu thì lỗi hiện ra dưới dạng "đã đổi cấu trúc",
    # rất dễ đi tìm nhầm chỗ.
    MOC_CHEN = "</script>\r\n<script>" if "</script>\r\n<script>" in html else "</script>\n<script>"
    html_thu = html.replace(
        MOC_CHEN, "</script>\n<script>\n" + gl + "\n</script>\n<script>", 1)
    if html_thu == html:
        raise SystemExit("Không tìm thấy chỗ chèn giả lập — khung-app.html đã đổi cấu trúc?")
    html_thu = html_thu.replace(
        "<title>", "<title>[BẢN THỬ] ", 1)
    with io.open(RA_THU, "w", encoding="utf-8") as f:
        f.write(html_thu)
else:
    RA_THU = None


def rai_bieu_cam(dich):
    """Copy thư mục video biểu cảm sang một chỗ, chỉ ghi đè file đã đổi.

    Không dùng shutil.rmtree rồi copy lại: theo quy tắc 2 của workspace, không xoá
    hàng loạt. Ở đây chỉ ghi đè từng file một."""
    if not os.path.isdir(BIEUCAM):
        return 0
    os.makedirs(dich, exist_ok=True)
    n = 0
    for ten in sorted(os.listdir(BIEUCAM)):
        nguon = os.path.join(BIEUCAM, ten)
        if not os.path.isfile(nguon):
            continue
        shutil.copy2(nguon, os.path.join(dich, ten))
        n += 1
    return n


so_bc = rai_bieu_cam(os.path.join(DEMO, "bieu-cam"))


def rai_nhac(dich):
    """Nhạc nhảy múa (nhac/mua.mp3) — cùng lối rải từng file như biểu cảm."""
    nguon_dir = os.path.join(HERE, "nhac")
    if not os.path.isdir(nguon_dir):
        return 0
    os.makedirs(dich, exist_ok=True)
    n = 0
    for ten in sorted(os.listdir(nguon_dir)):
        if ten.lower().endswith(".mp3"):
            shutil.copy2(os.path.join(nguon_dir, ten), os.path.join(dich, ten)); n += 1
    return n


rai_nhac(os.path.join(DEMO, "nhac"))


def rai_thong_tin(dich, kem_js=False):
    """Màn Thông tin (cán bộ Trung tâm) — thư mục thong-tin/ do tools/dung-thong-tin.py sinh.
    Vào APK làm bản MẶC ĐỊNH; bản trên thẻ nhớ robot vẫn được MainActivity ưu tiên.
    kem_js: bản demo không fetch() được JSON qua file:// — kèm thong-tin.js cho bộ soi nạp."""
    nguon = os.path.join(HERE, "thong-tin")
    if not os.path.isdir(nguon):
        return 0
    if os.path.isdir(dich):
        shutil.rmtree(dich)          # thư mục SINH RA, không phải dữ liệu gốc — chép lại từ đầu
    shutil.copytree(nguon, dich)
    if kem_js:
        j = io.open(os.path.join(nguon, "thong-tin.json"), encoding="utf-8").read()
        io.open(os.path.join(dich, "thong-tin.js"), "w", encoding="utf-8").write("window.THONG_TIN_THAT=" + j + ";")
    return len(os.listdir(os.path.join(dich, "can-bo"))) if os.path.isdir(os.path.join(dich, "can-bo")) else 0


rai_thong_tin(os.path.join(DEMO, "thong-tin"), kem_js=True)

print("→ %s" % RA)
print("   %.2f MB · %d đích đến · %d file biểu cảm"
      % (os.path.getsize(RA) / 1024 / 1024, data.count('"id":'), so_bc))
print("   Mở bằng cách nháy đúp file, hoặc: start %s" % RA)
if RA_THU:
    print("→ %s" % RA_THU)
    print("   BẢN THỬ — có giả lập robot, bấm được nút dẫn đường. Nháy đúp để mở.")

# ── Rải sang assets của Android để build APK ──
#
# Rải BA thứ, mỗi thứ một việc:
#
#   khung-app.html + app-data.json   RỜI NHAU — đây là thứ app thật dùng.
#       MainActivity.napGiaoDien() ghép hai file này lúc chạy, và mỗi file đều ưu tiên
#       bản trên thẻ nhớ nếu có. Nhờ để rời mà cán bộ trung tâm cập nhật thủ tục chỉ cần
#       đẩy app-data.json sang robot, không phải build lại APK 20 MB.
#
#   index.html (bản đã ghép)         LƯỚI AN TOÀN.
#       Chỉ dùng khi napGiaoDien() ném lỗi — ví dụ file trên thẻ nhớ đẩy dở, JSON gãy.
#       Không có nó thì lỗi đó thành màn hình trắng trước mặt người dân.
if os.path.isdir(ASSETS):
    shutil.copy2(KHUNG, os.path.join(ASSETS, "khung-app.html"))
    shutil.copy2(DULIEU, os.path.join(ASSETS, "app-data.json"))
    shutil.copy2(RA, os.path.join(ASSETS, "index.html"))
    rai_bieu_cam(os.path.join(ASSETS, "bieu-cam"))
    rai_nhac(os.path.join(ASSETS, "nhac"))
    n_tt = rai_thong_tin(os.path.join(ASSETS, "thong-tin"))
    print("→ %s" % ASSETS)
    print("   thong-tin/ (%d ảnh cán bộ)" % n_tt)
    print("   khung-app.html + app-data.json (app đọc hai file này, ưu tiên bản ở thẻ nhớ)")
    print("   index.html (bản ghép sẵn — lưới an toàn khi dữ liệu thẻ nhớ hỏng)")
    print("   bieu-cam/")
else:
    print("! Không thấy %s — bỏ qua bước chép sang Android" % ASSETS)
