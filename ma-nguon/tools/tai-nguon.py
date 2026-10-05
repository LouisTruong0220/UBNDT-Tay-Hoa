# -*- coding: utf-8 -*-
"""TẢI TOÀN BỘ NỘI DUNG APP TỪ thutuc.hanhchinhso.ai.vn — không lấy từ đâu khác.

    python tools/tai-nguon.py            # tải cấu hình + dựng menu + nạp câu trả lời
    python tools/tai-nguon.py --xem      # chỉ xem đang có gì, không gọi mạng
    python tools/tai-nguon.py --chi-menu # dừng sau khi dựng menu, chưa nạp nội dung

VÌ SAO CÓ FILE NÀY
──────────────────
Anh Trường chốt 18/09/2026: app **chỉ dùng dữ liệu và nguồn thông tin của
thutuc.hanhchinhso.ai.vn**. Kho 165 thủ tục Quảng Ninh mồi sẵn trước đây bị bỏ hẳn —
nó vốn sai địa bàn (số quầy, lệ phí, mã QR đều của tỉnh khác), giữ lại chỉ tổ có hai
nguồn sự thật mâu thuẫn nhau trong cùng một máy.

BA THỨ LẤY VỀ, MỖI THỨ MỘT ĐƯỜNG
─────────────────────────────────
① GET /api/config
   Chính là thứ dựng nên giao diện website của họ: tên, khẩu hiệu, BẢNG MÀU
   (#C8102E đỏ · #FFCD00 vàng · #FEF2F2 nền), ba thẻ tra cứu nhanh, bốn nhóm câu hỏi
   ví dụ, các bước hướng dẫn, danh mục văn bản đã lập chỉ mục, và dòng cảnh báo bản
   xem trước. App lấy nguyên chỗ này làm giao diện chính.

② POST /api/chat/stream với câu hỏi CHUNG CHUNG
   Đo 18/09/2026: hỏi "thủ tục đất đai" thì máy chủ KHÔNG trả lời thẳng mà trả về
   "Cần làm rõ" kèm danh sách lựa chọn dạng {{...}}. Đó chính là menu con — do CHÍNH
   HỌ sinh ra, theo đúng kho văn bản họ có. Nhờ vậy menu của robot không phải tự
   biên, và không bao giờ có mục trỏ vào thứ họ không trả lời được.

③ POST /api/chat/stream với từng câu cụ thể
   Nạp sẵn câu trả lời vào máy để robot trả lời tức thì và vẫn chạy khi mất mạng.
   Vẫn là chữ của họ, không sửa một câu.

TÔN TRỌNG HẠN MỨC CỦA HỌ
────────────────────────
Hạn mức dùng chung với người dùng thật của trang đó. Script nghỉ giữa các câu, và
DỪNG HẲN khi gặp câu báo hết hạn mức — không thử lại, không vét.
"""
import io, json, os, re, sys, time, unicodedata, urllib.request

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
DU_LIEU = os.path.join(APP, "du-lieu")

GOC = "https://thutuc.hanhchinhso.ai.vn"
URL_CAU_HINH = GOC + "/api/config"
URL_HOI = GOC + "/api/chat/stream"
UA = "RoboworldNova/1.0 (robot le tan hanh chinh cong)"

F_CAU_HINH = os.path.join(DU_LIEU, "nguon-cau-hinh.json")
F_MENU = os.path.join(DU_LIEU, "nguon-menu.json")
F_KHO = os.path.join(DU_LIEU, "nguon-kho.json")

NGHI_GIAY = 2

# Bao nhiêu câu hỏng LIÊN TIẾP thì coi là máy chủ đang chặn, chứ không phải
# kho của họ thiếu mục. Ba là đủ: đường kho trả lời trong nửa giây, nên ba câu
# liền không qua được thì không còn là chuyện thiếu một mục nữa.
NGUONG_HONG_LIEN = 3
HAN_CHO = 90

# ══════════════════════════════════════════════════════════════════════════
# LĨNH VỰC — lấy từ chính lời tự mô tả phạm vi của họ
#
# brand.description và onboarding.description của họ ghi nguyên văn:
#   "…tra cứu thủ tục hộ tịch, cư trú, chứng thực, đất đai, chính sách xã hội
#    sau cải cách hành chính hai cấp 01/7/2025"
#
# Năm lĩnh vực đó là năm thẻ lớn trên màn hình robot. Câu hỏi mồi bên dưới chỉ để
# XIN HỌ danh sách lựa chọn — nội dung menu con là của họ, không phải của mình.
# ══════════════════════════════════════════════════════════════════════════
LINH_VUC = [
    # (mã, tên thẻ, dòng phụ, biểu tượng, CÂU MỒI ĐÃ ĐO đi đường nhanh)
    #
    # ⚠ Câu mồi KHÔNG tự nghĩ ra — mỗi câu dưới đây đã đo trên máy chủ của họ ngày
    #   18/09/2026 và xác nhận đi ĐƯỜNG KHO VĂN BẢN (dưới một giây, trả về danh sách
    #   lựa chọn). Câu viết khác đi một chút là rơi xuống đường hỏi mô hình: mất năm
    #   giây, tốn hạn mức, và trả về câu KHÔNG có căn cứ pháp lý.
    #
    #   Đã thử và KHÔNG chạy: "Thủ tục hộ tịch cần giấy tờ gì?" · "Thủ tục hộ tịch tại
    #   xã" · "hộ tịch cần giấy tờ gì?" — cùng một ý mà khác chữ thì khác đường.
    ("khai-sinh",  "Khai sinh",   "Cho con mới sinh · khai muộn",      "👶",
     "Thủ tục khai sinh cần giấy tờ gì?"),
    ("ket-hon",    "Kết hôn",     "Đăng ký · xác nhận tình trạng hôn nhân", "💍",
     "xác nhận tình trạng hôn nhân"),
    ("ho-tich",    "Hộ tịch khác", "Khai tử · cải chính · giám hộ",    "📋",
     "hộ tịch"),
    ("cu-tru",     "Cư trú",      "Thường trú · tạm trú · tạm vắng",   "🏠",
     "Thủ tục cư trú cần giấy tờ gì?"),
    ("chung-thuc", "Chứng thực",  "Sao y · chữ ký · hợp đồng",         "✍️",
     "Thủ tục chứng thực cần giấy tờ gì?"),
    ("dat-dai",    "Đất đai",     "Sổ đỏ · sang tên · tách thửa",      "🏡",
     "Thủ tục đất đai cần giấy tờ gì?"),
    ("kinh-doanh", "Kinh doanh",  "Hộ kinh doanh · đổi · tạm ngừng",   "🏪",
     "đăng ký kinh doanh hộ cá thể"),
    ("chinh-sach", "Chính sách",  "Hộ nghèo · trợ cấp · người có công", "🤝",
     "trợ cấp xã hội"),
    ("khieu-nai",  "Khiếu nại",   "Khiếu nại · tố cáo",                "⚖️",
     "khiếu nại tố cáo"),
]

# Lĩnh vực nào nguồn KHÔNG tự sinh menu con thì khai mục con ở đây — nhưng CHỈ được
# khai câu ĐÃ ĐO là trả lời được, kèm ngày đo và số căn cứ pháp lý thu về.
#
# Vì sao cần bảng này: "chính sách xã hội" là cụm quá chung, hỏi theo khuôn
# "Thủ tục X cần giấy tờ gì?" thì máy chủ không khớp được văn bản nào (đo 7 lần,
# ngày 18 và 20/09/2026). Nhưng hỏi thẳng từng việc thì họ trả về đầy đủ. Bảy lần
# trượt đó từng dẫn tôi tới kết luận SAI rằng kho của họ chưa phủ mảng này.
# Khuôn phải TRÙNG với thứ boc_lua_chon() trả về: {"nhan", "hoi"}.
#   nhan = chữ hiện trên màn robot, viết theo tiếng người dân
#   hoi  = câu gửi máy chủ, GIỮ NGUYÊN VĂN câu đã đo — đổi một chữ là trượt bộ đệm,
#          và với mảng này thì có khi trượt luôn kho.
MUC_TAY = {
    "ket-hon": [
        {"nhan": "Đăng ký kết hôn",
         "hoi": "Đăng ký kết hôn"},          # nguồn sinh sẵn ở menu hộ tịch
    ],
    "ho-tich": [
        {"nhan": "Đăng ký khai tử",
         "hoi": "khai tử"},                  # đo 20/09 — 2.823 ký tự, 3 căn cứ
        {"nhan": "Đăng ký giám hộ",
         "hoi": "giám hộ"},                  # đo 20/09 — 3.558 ký tự, 5 căn cứ
        {"nhan": "Nhận cha, mẹ, con",
         "hoi": "Thủ tục đăng ký nhận cha, mẹ, con không có tranh chấp tại UBND cấp xã "
                "cần giấy tờ gì?"},
    ],
    "cu-tru": [
        {"nhan": "Đăng ký thường trú",
         "hoi": "đăng ký thường trú"},       # đo 20/09 — 3.586 ký tự, 2 căn cứ
    ],
    "chinh-sach": [
        {"nhan": "Hộ nghèo, hộ cận nghèo",
         "hoi": "hộ nghèo"},              # đo 20/09/2026 — 3.223 ký tự, 4 căn cứ
        {"nhan": "Trợ cấp xã hội hàng tháng",
         "hoi": "trợ cấp xã hội"},        # đo 20/09/2026 — 3.122 ký tự, 5 căn cứ
        {"nhan": "Chế độ người có công",
         "hoi": "người có công"},         # đo 20/09/2026 — 4.149 ký tự, 8 căn cứ
        {"nhan": "Trợ cấp mai táng phí",
         "hoi": "mai táng phí"},          # đo 20/09/2026 — 2.125 ký tự, 3 căn cứ
        {"nhan": "Trợ cấp trẻ em mồ côi",
         "hoi": "trợ cấp cho trẻ em mồ côi"},   # đo 20/09 — 3.120 ký tự, 2 căn cứ
    ],
    # Thẻ này có đúng MỘT mục: hai cụm cùng nhóm ("tiếp công dân", "kiến nghị phản ánh")
    # đều trượt khi thử 20/09/2026. Vẫn dựng vì mục này có 6 căn cứ pháp lý.
    "khieu-nai": [
        {"nhan": "Khiếu nại, tố cáo tại xã",
         "hoi": "khiếu nại tố cáo"},      # đo 20/09/2026 — 3.744 ký tự, 6 căn cứ
    ],
}

# Mục nguồn sinh ra nhưng KHÔNG dựng thẻ — vì đã có lĩnh vực riêng cho nó.
# Để lại thì người dân thấy cùng một việc ở hai chỗ, bấm vào đâu cũng ra như nhau,
# và số đếm trên thẻ nói dối.
BO_MUC = {
    "ho-tich": ["Đăng ký khai sinh", "Đăng ký kết hôn"],
}

# ══════════════════════════════════════════════════════════════════════════
# MẢNG HỌ CHƯA PHỦ — phải nói thật, không dựng thẻ rỗng
#
# Đo 18/09/2026: mọi cách hỏi về chính sách xã hội đều rơi xuống đường mô hình
# ("trợ cấp xã hội" · "chính sách xã hội" · "hộ nghèo" · "người có công" ·
#  "Thủ tục trợ cấp xã hội cần giấy tờ gì?" · "Thủ tục bảo trợ xã hội…").
# Nghĩa là kho văn bản của họ chưa có mảng này, dù lời tự mô tả của họ có nhắc tới.
#
# App KHÔNG dựng thẻ cho mảng này. Dựng thẻ rồi bấm vào không ra gì thì tệ hơn hẳn
# việc không có thẻ: người dân tưởng robot hỏng, còn cán bộ thì mất lòng tin vào cả
# những mục đang chạy tốt.
# ══════════════════════════════════════════════════════════════════════════
# Mảng nào nguồn THẬT SỰ chưa phủ thì khai ở đây — app nói thẳng với người dân
# thay vì dựng thẻ rỗng.
#
# ⚠ ĐỪNG kết luận "kho họ thiếu" chỉ vì mấy câu mồi trượt. "Chính sách xã hội" từng
# nằm trong bảng này suốt hai ngày, dựa trên 7 lần hỏi theo khuôn "Thủ tục X cần giấy
# tờ gì?" — hỏi thẳng "hộ nghèo" thì họ trả 3.223 ký tự kèm 4 căn cứ. Kho không thiếu,
# chỉ là câu mồi sai khuôn. Trước khi thêm dòng vào đây: thử ÍT NHẤT ba khuôn câu khác
# hẳn nhau (cụm danh từ trần · câu người dân hay nói · tên thủ tục đầy đủ).
CHUA_PHU = []

DAU_HIEU_HONG = ("hit your limit", "rate limit", "quota", "internal server error",
                 "something went wrong", "lưu ý kiểm chứng",
                 "chưa trích dẫn được căn cứ pháp lý")


def chuan_hoa(s):
    """Khoá tra cứu — PHẢI khớp khoaNapTruoc() bên khung-app.html."""
    s = unicodedata.normalize("NFD", (s or "").lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn").replace("đ", "d")
    return " ".join("".join(c if c.isalnum() or c.isspace() else " " for c in s).split())


def tai_json(url):
    rq = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(rq, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def hoi(q, phien=None):
    """Trả (answer, citations, sessionId, giây). Ném lỗi nếu gọi hỏng."""
    than = {"query": q, "language": "vi"}
    if phien:
        than["sessionId"] = phien
    rq = urllib.request.Request(URL_HOI, data=json.dumps(than).encode("utf-8"),
                                method="POST", headers={
        "Content-Type": "application/json; charset=utf-8",
        "Accept": "text/event-stream",
        "User-Agent": UA})
    t0 = time.time()
    ten, ans, cit, sid = "", None, [], phien
    with urllib.request.urlopen(rq, timeout=HAN_CHO) as r:
        for dong in r:
            d = dong.decode("utf-8", "replace").rstrip("\n")
            if d.startswith("event:"):
                ten = d[6:].strip()
            elif d.startswith("data:") and ten == "answer":
                try:
                    o = json.loads(d[5:].strip())
                    ans = o.get("answer") or None
                    cit = o.get("citations") or []
                    sid = o.get("sessionId") or sid
                except Exception:
                    pass
            elif not d.strip():
                ten = ""
    return ans, cit, sid, time.time() - t0


def cau_hong(t):
    t = (t or "").lower()
    return any(x in t for x in DAU_HIEU_HONG)


def boc_lua_chon(dap):
    """Lấy danh sách {{nhãn||câu gửi lại}} — chính là menu con do họ sinh ra."""
    ds = []
    for m in re.finditer(r"\{\{([^}]*)\}\}", dap or ""):
        o = [x.strip() for x in m.group(1).split("||")]
        if o and o[0]:
            ds.append({"nhan": o[0], "hoi": o[1] if len(o) > 1 else o[0]})
    return ds


def ghi(duong, obj):
    tam = duong + ".tam"
    with io.open(tam, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
    os.replace(tam, duong)          # ghi nguyên tử: mất điện giữa chừng không mất file


# ══════════════════════════════════════════════════════════════════════════
def buoc1_cau_hinh():
    print("① Tải cấu hình giao diện từ %s" % URL_CAU_HINH)
    c = tai_json(URL_CAU_HINH)
    c["_tai_luc"] = time.strftime("%d/%m/%Y %H:%M")
    c["_nguon"] = URL_CAU_HINH
    ghi(F_CAU_HINH, c)
    b = c.get("brand", {})
    p = b.get("accentPalette", {})
    print("   ✓ %s — %s" % (b.get("name"), b.get("tagline")))
    print("   ✓ màu: %s · %s · %s" % (p.get("primary"), p.get("secondary"), p.get("surface")))
    print("   ✓ %d thẻ tra cứu nhanh · %d nhóm câu hỏi ví dụ · %d văn bản đã lập chỉ mục"
          % (len(c.get("quickAccess", [])), len(c.get("exampleQuestions", [])),
             len(c.get("coverageMap", {}).get("have", []))))
    return c


def buoc2_menu(cu=None):
    """Hỏi họ từng lĩnh vực để LẤY VỀ menu con. Chạy tiếp được."""
    print("\n② Dựng menu — hỏi chính máy chủ của họ, không tự biên mục nào")
    menu = cu or {"nguon": URL_HOI, "cap_nhat": "", "linh_vuc": {}}
    for ma, ten, phu, icon, cau_moi in LINH_VUC:
        cu_muc = menu["linh_vuc"].get(ma)
        if cu_muc and cu_muc.get("muc"):
            # Tên, dòng phụ, biểu tượng và câu mồi là chữ TỰ KHAI, không phải dữ liệu
            # của nguồn — cập nhật lại mỗi lần chạy. Bản trước bỏ qua hẳn, nên sửa dòng
            # phụ trong bảng LINH_VUC xong chạy lại vẫn thấy chữ cũ (dính 20/09/2026).
            cu_muc.update({"ten": ten, "phu": phu, "icon": icon, "cau_moi": cau_moi})
            print("   · %-18s đã có %d mục" % (ten, len(cu_muc["muc"])))
            continue
        try:
            ans, _, _, giay = hoi(cau_moi)
        except Exception as e:
            print("   ✗ %-18s lỗi mạng: %s" % (ten, e))
            continue
        if cau_hong(ans):
            print("   ✗ %-18s máy chủ đang hết hạn mức — DỪNG, chạy lại sau" % ten)
            break
        ds = boc_lua_chon(ans)
        bot = 0
        if ma in BO_MUC:
            truoc = len(ds)
            ds = [m for m in ds if m.get("nhan") not in BO_MUC[ma]]
            bot = truoc - len(ds)
        if ma in MUC_TAY:
            da_co = {chuan_hoa(m.get("hoi", "")) for m in ds}
            them = [m for m in MUC_TAY[ma] if chuan_hoa(m["hoi"]) not in da_co]
            ds = ds + them
            print("   · %-18s nguồn %d mục%s + %d mục khai tay đã đo"
                  % (ten, len(ds) - len(them),
                     (" (bỏ %d trùng lĩnh vực khác)" % bot) if bot else "", len(them)))
        elif not ds:
            print("   ⚠ %-18s họ trả lời thẳng, không có lựa chọn (%.1fs)" % (ten, giay))
        menu["linh_vuc"][ma] = {
            "ten": ten, "phu": phu, "icon": icon,
            "cau_moi": cau_moi, "muc": ds,
        }
        print("   ✓ %-18s %d mục (%.1fs)" % (ten, len(ds), giay))
        time.sleep(NGHI_GIAY)
    # Sắp lại ĐÚNG thứ tự bảng LINH_VUC trước khi ghi. Dict giữ thứ tự chèn, nên lĩnh
    # vực dựng lại sau sẽ nhảy xuống cuối — thẻ Kết hôn và Hộ tịch từng bị đẩy ra xa thẻ
    # Khai sinh dù cùng một mảng việc, người dân phải quét mắt cả màn mới thấy.
    menu["linh_vuc"] = {ma: menu["linh_vuc"][ma]
                        for ma, *_ in LINH_VUC if ma in menu["linh_vuc"]}
    menu["cap_nhat"] = time.strftime("%d/%m/%Y %H:%M")
    ghi(F_MENU, menu)
    tong = sum(len(v.get("muc", [])) for v in menu["linh_vuc"].values())
    print("   → %d lĩnh vực · %d mục con" % (len(menu["linh_vuc"]), tong))
    return menu


def buoc3_kho(menu, cau_hinh, toi_da=None):
    """Nạp sẵn câu trả lời cho mọi mục trong menu + các thẻ tra cứu nhanh."""
    print("\n③ Nạp sẵn câu trả lời (để robot trả lời tức thì và chạy được khi mất mạng)")
    kho = {"nguon": URL_HOI, "cap_nhat": "", "muc": {}}
    if os.path.isfile(F_KHO):
        kho = json.load(io.open(F_KHO, encoding="utf-8"))

    can = []
    for v in menu["linh_vuc"].values():
        can.append(v["cau_moi"])
        for m in v["muc"]:
            can.append(m["hoi"])
    for q in cau_hinh.get("quickAccess", []):
        if q.get("query"):
            can.append(q["query"])
    for e in cau_hinh.get("exampleQuestions", []):
        can.extend(e.get("questions", []))

    thieu = [c for c in can if chuan_hoa(c) not in kho["muc"]]
    print("   %d câu cần nạp · %d đã có · %d còn thiếu" % (len(can), len(can) - len(thieu), len(thieu)))
    if toi_da:
        thieu = thieu[:toi_da]

    xong, hong_lien, bo_qua = 0, 0, []
    for c in thieu:
        try:
            ans, cit, _, giay = hoi(c)
        except Exception as e:
            print("   ✗ lỗi mạng: %s — dừng" % e)
            break
        if cau_hong(ans):
            """Câu này rơi xuống đường hỏi mô hình, mà đường đó đang hết hạn mức.

            ⚠ KHÔNG dừng ngay. Hai chuyện rất khác nhau cùng hiện ra một câu báo lỗi:
              · kho văn bản của họ KHÔNG CÓ mục này  → bỏ qua mục, đi tiếp mục sau
              · máy chủ đang chặn hẳn mình            → dừng, nạp tiếp vào hôm khác
            Phân biệt bằng số câu hỏng LIÊN TIẾP: đường kho vẫn trả lời trong nửa giây
            thì xen kẽ vài câu hỏng là bình thường; hỏng liền một mạch mới là bị chặn."""
            bo_qua.append(c)
            hong_lien += 1
            print("   ⤫ %5.1fs không lấy được lúc này — bỏ qua · %s" % (giay, c[:44]))
            if hong_lien >= NGUONG_HONG_LIEN:
                print("   ✗ %d câu hỏng liên tiếp — máy chủ đang chặn. DỪNG, chạy lại sau."
                      % hong_lien)
                break
            time.sleep(NGHI_GIAY)
            continue
        hong_lien = 0
        if not ans:
            print("   ⚠ không có câu trả lời: %s" % c[:50])
            continue
        kho["muc"][chuan_hoa(c)] = {"hoi": c, "dap": ans, "nguon": cit,
                                    "luc": time.strftime("%d/%m/%Y")}
        xong += 1
        print("   ✓ %5.1fs %5d ký tự %2d căn cứ · %s" % (giay, len(ans), len(cit), c[:48]))
        kho["cap_nhat"] = time.strftime("%d/%m/%Y %H:%M")
        ghi(F_KHO, kho)          # ghi sau MỖI câu — mất mạng giữa chừng không mất công
        time.sleep(NGHI_GIAY)
    print("   → kho có %d câu (vừa nạp thêm %d)" % (len(kho["muc"]), xong))
    if bo_qua:
        print("   ⤫ %d mục CHƯA NẠP ĐƯỢC — chạy lại sau, robot tạm hỏi trực tiếp:" % len(bo_qua))
        print("      (không phân biệt được là kho họ thiếu hay máy chủ đang chặn —")
        print("       câu 'đăng ký tạm trú' sáng nay còn trả về đủ 2.718 ký tự)")
        for c in bo_qua:
            print("      · %s" % c)
    return kho


def xem():
    for ten, d in (("cấu hình", F_CAU_HINH), ("menu", F_MENU), ("kho", F_KHO)):
        if not os.path.isfile(d):
            print("  %-10s CHƯA CÓ" % ten)
            continue
        o = json.load(io.open(d, encoding="utf-8"))
        if ten == "menu":
            tong = sum(len(v.get("muc", [])) for v in o.get("linh_vuc", {}).values())
            print("  %-10s %d lĩnh vực · %d mục con · %s"
                  % (ten, len(o.get("linh_vuc", {})), tong, o.get("cap_nhat", "")))
        elif ten == "kho":
            print("  %-10s %d câu · %s" % (ten, len(o.get("muc", {})), o.get("cap_nhat", "")))
        else:
            print("  %-10s %s · %s" % (ten, o.get("brand", {}).get("name"), o.get("_tai_luc", "")))


if __name__ == "__main__":
    os.makedirs(DU_LIEU, exist_ok=True)
    if "--xem" in sys.argv:
        xem()
        raise SystemExit(0)

    toi_da = None
    if "--toi-da" in sys.argv:
        toi_da = int(sys.argv[sys.argv.index("--toi-da") + 1])

    ch = buoc1_cau_hinh()
    cu = json.load(io.open(F_MENU, encoding="utf-8")) if os.path.isfile(F_MENU) else None
    menu = buoc2_menu(cu)
    if "--chi-menu" in sys.argv:
        raise SystemExit(0)
    buoc3_kho(menu, ch, toi_da)
    print("\nXong. Chạy tiếp:  python dung-du-lieu.py  →  python dung-app.py")
