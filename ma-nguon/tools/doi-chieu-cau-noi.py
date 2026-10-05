# -*- coding: utf-8 -*-
"""Cổng đối chiếu CẦU NỐI giữa lớp web (khung-app.html) và lớp Kotlin (Cau.kt).

Vì sao cần cổng này
───────────────────
App này ghép từ hai nguồn: vỏ Kotlin lấy của app lễ tân bệnh viện (bản mới nhất, có
gom vế · cắt tiếng hai tầng · phiên hội thoại), còn giao diện lấy của app tra cứu thủ
tục. Hai bên vốn không nói chuyện với nhau, phải nối lại bằng tay.

Cầu nối lệch là loại lỗi TỆ NHẤT trên Nova: JS gọi `CAU.abc()` mà Kotlin không có hàm
đó thì WebView ném lỗi TRONG TRANG — app vẫn chạy, màn hình vẫn đẹp, chỉ là bấm nút
không có gì xảy ra. Ngược lại Kotlin gọi `window.xyz()` mà trang không định nghĩa thì
`evaluateJavascript` nuốt luôn, không một dòng log. Cả hai hướng đều IM LẶNG.

Kiểm hai chiều:
  1. Mọi `CAU.<ten>` mà JS gọi  →  phải có @JavascriptInterface cùng tên trong Cau.kt
  2. Mọi `window.<ten>` mà Kotlin gọi  →  phải được gán trong khung-app.html

Chạy:  python tools/doi-chieu-cau-noi.py
Thoát 0 nếu khớp, 1 nếu lệch (dùng được trong dây chuyền kiểm tự động).
"""
import io, os, re, sys

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
KHUNG = os.path.join(APP, "khung-app.html")
CAU_KT = os.path.join(APP, "android", "app", "src", "main", "java",
                      "vn", "roboworld", "hcc", "Cau.kt")
THU_MUC_KT = os.path.dirname(CAU_KT)

# Hàm window.* do chính lớp web tự gọi lẫn nhau, không phải cầu nối — bỏ qua.
BO_QUA_WINDOW = {
    "DU_LIEU",          # dữ liệu Kotlin chèn vào, không phải hàm
    "THONG_TIN",        # thông tin Trung tâm (thong-tin.json) — MainActivity chèn, 24/09/2026
    "THU_MUC_THONG_TIN",  # đường dẫn thư mục ảnh cán bộ — cũng là dữ liệu chèn vào
    "decorView",        # của Android, lọt vào do grep thô
}


def doc(p):
    if not os.path.isfile(p):
        print("✗ Không thấy file: %s" % p)
        raise SystemExit(2)
    return io.open(p, encoding="utf-8").read()


def bo_chu_thich_js(s):
    """Bỏ chú thích /* */ và // để không bắt nhầm tên hàm nằm trong lời giải thích."""
    s = re.sub(r"/\*.*?\*/", " ", s, flags=re.S)
    return re.sub(r"(?m)^\s*//[^\n]*$", " ", s)


def bo_chu_thich_kt(s):
    s = re.sub(r"/\*.*?\*/", " ", s, flags=re.S)
    return re.sub(r"(?m)//[^\n]*$", " ", s)


khung = doc(KHUNG)
khung_sach = bo_chu_thich_js(khung)

kt_cau = doc(CAU_KT)
kt_tat = "\n".join(
    doc(os.path.join(THU_MUC_KT, t))
    for t in sorted(os.listdir(THU_MUC_KT)) if t.endswith(".kt")
)
kt_sach = bo_chu_thich_kt(kt_tat)

# ── Chiều 1: JS gọi CAU.<ten> ──────────────────────────────────────────────
js_goi = set(re.findall(r"\bCAU\.([a-zA-Z_][a-zA-Z0-9_]*)", khung_sach))

# Hàm Kotlin phơi ra cho JS: dòng @JavascriptInterface rồi tới `fun <ten>`
kt_co = set(re.findall(
    r"@JavascriptInterface\s+(?:@\w+\s+)*fun\s+([a-zA-Z_][a-zA-Z0-9_]*)",
    bo_chu_thich_kt(kt_cau)))

# Lời gọi CÓ RÀO: `typeof CAU.abc === 'function'`.
# Đây là cách lớp web tự dò xem tầng Android có hỗ trợ một việc hay không, rồi ẩn
# nút đi nếu không. Thiếu hàm kiểu này KHÔNG phải lỗi — chức năng tự tắt êm, đúng
# như thiết kế. Chỉ lời gọi TRẦN mới là lỗi thật: bấm nút xong không có gì xảy ra.
co_rao = set(re.findall(
    r"typeof\s+CAU\.([a-zA-Z_][a-zA-Z0-9_]*)\s*[!=]==?\s*['\"]function['\"]", khung_sach))

thieu_kt = sorted(js_goi - kt_co - co_rao)
tat_em = sorted((js_goi - kt_co) & co_rao)
thua_kt = sorted(kt_co - js_goi)

# ── Chiều 2: Kotlin gọi window.<ten> ───────────────────────────────────────
kt_goi = set(re.findall(r"window\.([a-zA-Z_][a-zA-Z0-9_]*)", kt_sach)) - BO_QUA_WINDOW

# Lớp web gán: `window.abc = ...`  hoặc  `window['abc'] = ...`
web_co = set(re.findall(r"window\.([a-zA-Z_][a-zA-Z0-9_]*)\s*=", khung_sach))
web_co |= set(re.findall(r"window\[['\"]([a-zA-Z_][a-zA-Z0-9_]*)['\"]\]\s*=", khung_sach))

thieu_web = sorted(kt_goi - web_co)

# ── Báo cáo ───────────────────────────────────────────────────────────────
print("Cầu nối web ⇄ Kotlin")
print("  JS gọi CAU.*        : %d hàm" % len(js_goi))
print("  Cau.kt phơi ra      : %d hàm" % len(kt_co))
print("  Kotlin gọi window.* : %d hàm" % len(kt_goi))
print("  Khung web định nghĩa: %d hàm" % len(web_co))
print()

loi = []

if thieu_kt:
    loi.append(
        "JS gọi %d hàm mà Cau.kt KHÔNG CÓ — bấm nút sẽ không có gì xảy ra,\n"
        "   và WebView nuốt lỗi trong trang nên logcat cũng không báo:\n%s"
        % (len(thieu_kt), "\n".join("        · CAU.%s()" % t for t in thieu_kt)))

if thieu_web:
    loi.append(
        "Kotlin gọi %d hàm window.* mà khung web KHÔNG ĐỊNH NGHĨA — robot làm xong\n"
        "   việc rồi báo ngược lên giao diện, giao diện không nghe thấy gì:\n%s"
        % (len(thieu_web), "\n".join("        · window.%s()" % t for t in thieu_web)))

if tat_em:
    print("ℹ %d chức năng TỰ TẮT ÊM — lớp web có rào `typeof CAU.x === 'function'`"
          % len(tat_em))
    print("   nên nút tự ẩn, không phải lỗi. Muốn bật thì thêm hàm vào Cau.kt:")
    for t in tat_em:
        print("     · CAU.%s()" % t)
    print()

if thua_kt:
    # Không phải lỗi — chỉ là hàm Kotlin chưa ai dùng. In ra để biết mà dọn.
    print("ℹ Cau.kt có %d hàm chưa hàm JS nào gọi tới (không sao, chỉ là chưa dùng):"
          % len(thua_kt))
    for t in thua_kt:
        print("     · CAU.%s()" % t)
    print()

if loi:
    print("✗ CẦU NỐI LỆCH:\n")
    for x in loi:
        print("   " + x + "\n")
    raise SystemExit(1)

print("✓ Cầu nối khớp cả hai chiều.")
